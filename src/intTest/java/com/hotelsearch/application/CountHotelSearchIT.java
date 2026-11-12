package com.hotelsearch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.application.port.HotelSearchRepository;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;

/**
 * Integration tests del caso de uso CountHotelSearch contra el
 * repositorio JDBC real y un Oracle Database Free real (Testcontainers).
 *
 * <p>Comprueba el circuito completo de lectura: caso de uso + puerto +
 * JdbcTemplate + Flyway, incluyendo la sentencia única de búsqueda y
 * conteo definida en TECHNICAL-DECISIONS.md (§7 Oracle).</p>
 *
 * Condiciones del entorno:
 * - Imagen Oracle Free pinneada en 23.26.3 (multi-arch), misma línea de
 *   versión que la imagen de referencia del proyecto.
 * - El módulo oficial oracle-free incluye su propia wait strategy y
 *   usuario de esquema dedicado que @ServiceConnection conecta.
 * - El estado se limpia antes de cada test porque el contenedor es
 *   compartido por toda la clase.
 */
@Testcontainers
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class CountHotelSearchIT {

    @Container
    @ServiceConnection
    private static final OracleContainer oracle =
        new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart");

    private static final String HOTEL_A = "550e8400-e29b-41d4-a716-446655440000";

    @Autowired
    private HotelSearchRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CountHotelSearch useCase;

    @BeforeEach
    void setUp() {
        useCase = new CountHotelSearch(repository);
        jdbcTemplate.update("DELETE FROM hotel_search");
    }

    /**
     * Verifica que el caso de uso devuelve la búsqueda persistida y el
     * conteo de equivalentes sobre Oracle real, excluyendo la búsqueda
     * consultada.
     *
     * Condiciones:
     * - Búsqueda base guardada y una búsqueda equivalente con otro searchId
     *
     * Se espera:
     * - El resultado expone la búsqueda base
     * - El conteo de equivalentes es 1
     */
    @Test
    void count_SearchExists_ReturnsSearchAndEquivalentsCount() {
        // given
        SearchId baseId = SearchId.generate();
        repository.save(searchOf(baseId, HOTEL_A, LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 29), List.of(29, 28, 4, 3)));
        repository.save(searchOf(SearchId.generate(), HOTEL_A, LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 29), List.of(29, 28, 4, 3)));

        // when
        CountHotelSearch.Result result = useCase.execute(new CountHotelSearch.Query(baseId.toString()));

        // then
        assertAll(
            () -> assertThat(result.search().searchId()).as("Debe devolverse la búsqueda consultada")
                .isEqualTo(baseId),
            () -> assertThat(result.search().ages().values())
                .as("Las edades deben leerse en el mismo orden guardado").containsExactly(29, 28, 4, 3),
            () -> assertThat(result.equivalentsCount())
                .as("Debe contar la equivalente y excluir la consultada").isEqualTo(1));
    }

    /**
     * Verifica que un searchId que no existe en Oracle se distingue de un
     * conteo en cero: el caso de uso falla con un error específico.
     *
     * Condiciones:
     * - Tabla vacía al iniciar el test
     *
     * Se espera:
     * - Se lance SearchNotFoundException con el searchId en el mensaje
     */
    @Test
    void count_SearchNotFound_ThrowsSearchNotFoundException() {
        // given
        String unknownId = "00000000-0000-4000-8000-000000000000";

        // when
        var exception = assertThrows(SearchNotFoundException.class,
            () -> useCase.execute(new CountHotelSearch.Query(unknownId)),
            "Un searchId inexistente debe fallar y no devolver conteo cero");

        // then
        assertThat(exception.getMessage())
            .as("El mensaje debe identificar el searchId consultado")
            .contains(unknownId);
    }

    // <editor-fold desc="Helpers">
    private HotelSearch searchOf(SearchId searchId, String hotelId, LocalDate checkIn,
                                 LocalDate checkOut, List<Integer> ages) {
        return new HotelSearch(
            searchId,
            HotelId.of(hotelId),
            CheckIn.of(checkIn),
            CheckOut.of(checkOut),
            Ages.of(ages));
    }
    // </editor-fold>
}
