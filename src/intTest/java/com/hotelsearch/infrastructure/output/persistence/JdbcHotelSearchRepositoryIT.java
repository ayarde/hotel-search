package com.hotelsearch.infrastructure.output.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;

/**
 * Integration tests del repositorio JDBC contra un Oracle Database Free
 * real (Testcontainers). Flyway aplica la migración V1 al arrancar el
 * contexto, de modo que el esquema verificado es el mismo que el de
 * producción.
 *
 * Condiciones del entorno:
 * - Imagen Oracle Free pinneada en 23.26.3, la misma línea de versión
 *   que la imagen de referencia del proyecto; variante slim-faststart
 *   multi-arch (amd64/arm64).
 * - El módulo oficial oracle-free incluye su propia wait strategy
 *   (log "DATABASE IS READY TO USE") y crea un usuario de esquema
 *   dedicado que @ServiceConnection conecta al DataSource.
 * - El estado se limpia antes de cada test porque el contenedor es
 *   compartido por toda la clase.
 */
@Testcontainers
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class JdbcHotelSearchRepositoryIT {

    @Container
    @ServiceConnection
    private static final OracleContainer oracle =
        new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart");

    @Autowired
    private JdbcHotelSearchRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.update("DELETE FROM hotel_search");
    }

    /**
     * Verifica que una búsqueda válida se persiste y se lee con todos sus
     * campos: edades en el mismo orden, ages almacenado como JSON compacto
     * sin espacios y created_at completado por la base de datos.
     *
     * Condiciones:
     * - Tabla vacía al iniciar el test
     * - Búsqueda con edades [30, 25, 3]
     *
     * Se espera:
     * - La lectura devuelve la búsqueda persistida con count 0
     * - La columna ages contiene exactamente [30,25,3]
     * - created_at no es nulo (DEFAULT CURRENT_TIMESTAMP)
     */
    @Test
    void save_ValidSearch_PersistsSearchWithCompactAgesAndCreatedAt() {
        // given
        SearchId id = SearchId.generate();
        HotelSearch search = searchOf(id, HOTEL_A, LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 29), List.of(30, 25, 3));

        // when
        repository.save(search);

        // then
        var found = repository.findByIdWithCount(id);
        assertThat(found).as("La búsqueda debe existir tras el INSERT").isPresent();
        HotelSearch stored = found.orElseThrow().search();
        assertAll(
            () -> assertThat(stored.searchId()).as("El searchId debe coincidir").isEqualTo(id),
            () -> assertThat(stored.hotelId()).as("El hotelId debe coincidir").isEqualTo(search.hotelId()),
            () -> assertThat(stored.checkIn()).as("El checkIn debe coincidir").isEqualTo(search.checkIn()),
            () -> assertThat(stored.checkOut()).as("El checkOut debe coincidir").isEqualTo(search.checkOut()),
            () -> assertThat(stored.ages().values()).as("El orden de las edades debe preservarse")
                .containsExactly(30, 25, 3),
            () -> assertThat(found.orElseThrow().equivalentsCount())
                .as("Sin otras búsquedas equivalentes el conteo debe ser 0").isZero(),
            () -> assertThat(agesColumnOf(id)).as("ages debe guardarse como JSON compacto").isEqualTo("[30,25,3]"),
            () -> assertThat(createdAtOf(id)).as("created_at lo completa la base de datos").isNotNull());
    }

    /**
     * Verifica que una violación de la PK search_id se trata como
     * duplicado y se descarta sin lanzar error, dejando una sola fila.
     *
     * Condiciones:
     * - La misma búsqueda se guarda dos veces (redelivery at-least-once)
     *
     * Se espera:
     * - No se lanza excepción en el segundo INSERT
     * - La tabla contiene una sola fila
     */
    @Test
    void save_DuplicateSearchId_IsIgnored() {
        // given
        SearchId id = SearchId.generate();
        HotelSearch search = searchOf(id, HOTEL_A, LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 29), List.of(29, 28, 4, 3));
        repository.save(search);

        // when
        assertDoesNotThrow(() -> repository.save(search),
            "El duplicado por PK debe descartarse sin error");

        // then
        assertThat(rowCountOf(id)).as("Debe existir una sola fila para el searchId").isEqualTo(1);
    }

    /**
     * Verifica que solo la violación de PK prevista se trata como
     * duplicado: la constraint ck_hotel_search_dates rechaza un check-out
     * anterior al check-in y el error se propaga sin estado parcial.
     *
     * Condiciones:
     * - Ya existe una búsqueda válida guardada
     * - La búsqueda a insertar viola la constraint de fechas
     *
     * Se espera:
     * - Se lance DataIntegrityViolationException
     * - La tabla siga conteniendo únicamente la búsqueda válida previa
     */
    @Test
    void save_CheckOutBeforeCheckIn_PropagatesConstraintViolation() {
        // given
        SearchId validId = SearchId.generate();
        repository.save(searchOf(validId, HOTEL_A, LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 29), List.of(29, 28)));
        HotelSearch invalid = searchOf(SearchId.generate(), HOTEL_A,
            LocalDate.of(2026, 11, 29), LocalDate.of(2026, 11, 9), List.of(29, 28));

        // when
        var exception = assertThrows(DataIntegrityViolationException.class,
            () -> repository.save(invalid),
            "La constraint de fechas debe rechazar el INSERT y propagarse");

        // then
        assertThat(exception).as("El error SQL debe traducirse a DataIntegrityViolationException")
            .isNotNull();
        assertThat(rowCountOf(validId)).as("La búsqueda previa debe seguir intacta").isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM hotel_search", Integer.class))
            .as("El INSERT fallido no debe dejar estado parcial").isEqualTo(1);
    }

    /**
     * Verifica que un searchId inexistente devuelve Optional vacío, de
     * modo que se distinga de una búsqueda existente con conteo en cero.
     *
     * Condiciones:
     * - Tabla vacía al iniciar el test
     * - Se consulta un UUID no persistido
     *
     * Se espera:
     * - Optional.empty
     */
    @Test
    void findByIdWithCount_SearchNotFound_ReturnsEmptyOptional() {
        // given
        SearchId unknownId = SearchId.of("00000000-0000-4000-8000-000000000000");

        // when
        var found = repository.findByIdWithCount(unknownId);

        // then
        assertTrue(found.isEmpty(), "Un searchId inexistente debe devolver Optional vacío");
    }

    /**
     * Verifica que el conteo de equivalentes excluye la búsqueda
     * consultada y solo suma coincidencias exactas de hotelId, fechas y
     * edades en el mismo orden.
     *
     * Condiciones:
     * - Búsqueda base y dos equivalentes con distinto searchId
     * - Una búsqueda cuyo hotelId solo difiere en mayúsculas, que no debe contar
     *
     * Se espera:
     * - Conteo igual a 2
     * - La búsqueda devuelta es la base consultada
     */
    @Test
    void findByIdWithCount_WithEquivalentSearches_ExcludesQueriedSearchId() {
        // given
        SearchId baseId = SearchId.generate();
        HotelSearch base = searchOf(baseId, HOTEL_A, LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 29), List.of(29, 28, 4, 3));
        repository.save(base);
        repository.save(searchOf(SearchId.generate(), HOTEL_A, LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 29), List.of(29, 28, 4, 3)));
        repository.save(searchOf(SearchId.generate(), HOTEL_A, LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 29), List.of(29, 28, 4, 3)));
        repository.save(searchOf(SearchId.generate(), HOTEL_B, LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 29), List.of(29, 28, 4, 3)));

        // when
        var found = repository.findByIdWithCount(baseId);

        // then
        assertThat(found).as("La búsqueda base debe existir").isPresent();
        assertAll(
            () -> assertThat(found.orElseThrow().equivalentsCount())
                .as("Debe contar las dos equivalentes y excluir la consultada y la de otro hotel")
                .isEqualTo(2),
            () -> assertThat(found.orElseThrow().search().searchId())
                .as("Debe devolverse la búsqueda consultada").isEqualTo(baseId),
            () -> assertThat(found.orElseThrow().search().ages().values())
                .as("Las edades deben leerse en el mismo orden guardado")
                .containsExactly(29, 28, 4, 3));
    }

    /**
     * Verifica que el orden de las edades forma parte de la equivalencia:
     * los mismos valores en otro orden no cuentan como equivalentes.
     *
     * Condiciones:
     * - Búsqueda base con edades [29, 28, 4, 3]
     * - Otra búsqueda idéntica pero con edades [4, 28, 29, 3]
     *
     * Se espera:
     * - Conteo igual a 0
     */
    @Test
    void findByIdWithCount_DifferentAgesOrder_IsNotEquivalent() {
        // given
        SearchId baseId = SearchId.generate();
        repository.save(searchOf(baseId, HOTEL_A, LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 29), List.of(29, 28, 4, 3)));
        repository.save(searchOf(SearchId.generate(), HOTEL_A, LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 29), List.of(4, 28, 29, 3)));

        // when
        var found = repository.findByIdWithCount(baseId);

        // then
        assertThat(found).as("La búsqueda base debe existir").isPresent();
        assertThat(found.orElseThrow().equivalentsCount())
            .as("El mismo set de edades en otro orden no debe contar como equivalente")
            .isZero();
    }

    /**
     * Verifica que la migración V2 amplía hotel_id y que un identificador
     * de texto de longitud máxima se guarda y se lee sin cambios.
     *
     * Condiciones:
     * - Flyway aplicó V1 y V2 sobre Oracle real
     * - Búsqueda con un hotelId alfanumérico de 64 caracteres
     *
     * Se espera:
     * - La columna hotel_id admita 64 caracteres
     * - El hotelId leído sea idéntico al guardado
     */
    @Test
    void save_MaxLengthTextHotelId_PersistsWithoutChanges() {
        // given
        String hotelId = "aB3".repeat(21) + "Z";
        SearchId searchId = SearchId.generate();
        HotelSearch search = searchOf(searchId, hotelId, LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 29), List.of(29));

        // when
        repository.save(search);
        var found = repository.findByIdWithCount(searchId);
        Integer columnLength = jdbcTemplate.queryForObject(
            "SELECT char_length FROM user_tab_columns "
                + "WHERE table_name = 'HOTEL_SEARCH' AND column_name = 'HOTEL_ID'",
            Integer.class);

        // then
        assertThat(found).as("La búsqueda debe existir").isPresent();
        assertAll(
            () -> assertThat(columnLength).as("V2 debe ampliar hotel_id a 64").isEqualTo(64),
            () -> assertThat(found.orElseThrow().search().hotelId().value())
                .as("El hotelId debe leerse sin cambios").isEqualTo(hotelId)
        );
    }

    // <editor-fold desc="Helpers">
    private static final String HOTEL_A = "1234aBc";
    private static final String HOTEL_B = "1234abc";

    private HotelSearch searchOf(SearchId searchId, String hotelId, LocalDate checkIn,
                                 LocalDate checkOut, List<Integer> ages) {
        return new HotelSearch(
            searchId,
            HotelId.of(hotelId),
            CheckIn.of(checkIn),
            CheckOut.of(checkOut),
            Ages.of(ages));
    }

    private String agesColumnOf(SearchId id) {
        return jdbcTemplate.queryForObject(
            "SELECT ages FROM hotel_search WHERE search_id = ?",
            String.class,
            id.value().toString());
    }

    private java.sql.Timestamp createdAtOf(SearchId id) {
        return jdbcTemplate.queryForObject(
            "SELECT created_at FROM hotel_search WHERE search_id = ?",
            java.sql.Timestamp.class,
            id.value().toString());
    }

    private int rowCountOf(SearchId id) {
        Integer rows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM hotel_search WHERE search_id = ?",
            Integer.class,
            id.value().toString());
        return rows == null ? 0 : rows;
    }
    // </editor-fold>
}
