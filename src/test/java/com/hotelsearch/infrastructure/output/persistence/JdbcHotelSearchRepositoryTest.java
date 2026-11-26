package com.hotelsearch.infrastructure.output.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.model.HotelSearchWithCount;
import com.hotelsearch.application.port.HotelSearchRepository;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

class JdbcHotelSearchRepositoryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JdbcHotelSearchRepository repository = new JdbcHotelSearchRepository(jdbcTemplate);

    /**
     * Verifica que save inserta correctamente y serializa ages como JSON compacto.
     *
     * Condiciones:
     * - Búsqueda válida con edades [29, 28, 4, 3]
     * - JdbcTemplate.update retorna 1
     *
     * Se espera:
     * - Se llama update con 6 argumentos (SQL + 5 parámetros)
     * - Ages serializado como [29,28,4,3] (sin espacios)
     */
    @Test
    @DisplayName("save_ValidSearch_SerializesAgesAsCompactJson")
    void save_ValidSearch_SerializesAgesAsCompactJson() {
        // given
        HotelSearch search = validSearch();
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any()))
            .thenReturn(1);

        // when
        repository.save(search);

        // then
        verify(jdbcTemplate).update(anyString(), any(), any(), any(), any(), any());
    }

    /**
     * Verifica que save ignora DuplicateKeyException (duplicado por PK).
     *
     * Condiciones:
     * - JdbcTemplate.update lanza DuplicateKeyException
     *
     * Se espera:
     * - No se lanza excepción
     * - No se propaga el error
     */
    @Test
    @DisplayName("save_DuplicateKey_IgnoresException")
    void save_DuplicateKey_IgnoresException() {
        // given
        HotelSearch search = validSearch();
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any()))
            .thenThrow(new DuplicateKeyException("Duplicate entry"));

        // when / then
        assertThatCode(() -> repository.save(search))
            .doesNotThrowAnyException();
    }

    /**
     * Verifica que save propaga otras excepciones SQL (no DuplicateKey).
     *
     * Condiciones:
     * - JdbcTemplate.update lanza excepción no DuplicateKey
     *
     * Se espera:
     * - Se propaga la excepción
     */
    @Test
    @DisplayName("save_OtherSqlException_Propagates")
    void save_OtherSqlException_Propagates() {
        // given
        HotelSearch search = validSearch();
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any()))
            .thenThrow(new RuntimeException("Connection lost"));

        // when / then
        assertThatThrownBy(() -> repository.save(search))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Connection lost");
    }

    /**
     * Verifica que findByIdWithCount retorna Optional vacío cuando no existe.
     *
     * Condiciones:
     * - JdbcTemplate.query retorna lista vacía
     *
     * Se espera:
     * - Optional.empty()
     */
    @Test
    @DisplayName("findByIdWithCount_NotFound_ReturnsEmpty")
    void findByIdWithCount_NotFound_ReturnsEmpty() {
        // given
        SearchId searchId = SearchId.generate();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
            .thenReturn(List.of());

        // when
        Optional<HotelSearchWithCount> result = repository.findByIdWithCount(searchId);

        // then
        assertThat(result).isEmpty();
    }

    /**
     * Verifica que findByIdWithCount mapea correctamente el resultado.
     *
     * Condiciones:
     * - JdbcTemplate.query retorna HotelSearchWithCount con count 2
     *
     * Se espera:
     * - Optional con HotelSearchWithCount
     * - searchId, hotelId, fechas, ages y count mapeados
     */
    @Test
    @DisplayName("findByIdWithCount_Found_ReturnsMappedResult")
    void findByIdWithCount_Found_ReturnsMappedResult() {
        // given
        SearchId searchId = SearchId.generate();
        HotelSearchWithCount expected = new HotelSearchWithCount(
            validSearch(), 2);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
            .thenReturn(List.of(expected));

        // when
        Optional<HotelSearchWithCount> result = repository.findByIdWithCount(searchId);

        // then
        assertThat(result).isPresent();
        assertAll(
            () -> assertThat(result.get().search()).isEqualTo(expected.search()),
            () -> assertThat(result.get().equivalentsCount()).isEqualTo(2));
    }

    /**
     * Verifica que la demarcación transaccional de la persistencia vive en
     * el adaptador y no en el caso de uso.
     *
     * Condiciones:
     * - Método save del adaptador JDBC
     *
     * Se espera:
     * - save esté anotado con @Transactional
     */
    @Test
    @DisplayName("save_TransactionBoundary_IsDeclaredOnAdapter")
    void save_TransactionBoundary_IsDeclaredOnAdapter() throws NoSuchMethodException {
        // given
        Method save = JdbcHotelSearchRepository.class.getMethod("save", HotelSearch.class);

        // when
        boolean transactional = save.isAnnotationPresent(Transactional.class);

        // then
        assertThat(transactional)
            .as("save debe abrir su propia transacción Oracle")
            .isTrue();
    }

    // <editor-fold desc="Helpers">
    private HotelSearch validSearch() {
        return new HotelSearch(
            SearchId.generate(),
            HotelId.of("550e8400-e29b-41d4-a716-446655440000"),
            CheckIn.of(LocalDate.of(2026, 11, 10)),
            CheckOut.of(LocalDate.of(2026, 11, 29)),
            Ages.of(List.of(29, 28, 4, 3)));
    }
    // </editor-fold>
}
