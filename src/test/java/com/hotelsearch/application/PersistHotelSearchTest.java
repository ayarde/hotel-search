package com.hotelsearch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.model.HotelSearchWithCount;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import com.hotelsearch.application.port.HotelSearchRepository;

class PersistHotelSearchTest {

    private static class FakeRepository implements HotelSearchRepository {
        private boolean saved;

        @Override
        public void save(HotelSearch search) {
            this.saved = true;
        }

        @Override
        public Optional<HotelSearchWithCount> findByIdWithCount(SearchId searchId) {
            return Optional.empty();
        }
    }

    /**
     * Verifica que persist delega el guardado al repositorio.
     *
     * Condiciones:
     * - El search recibido es válido y no nulo
     *
     * Se espera:
     * - El repositorio marque la búsqueda como guardada
     */
    @Test
    @DisplayName("persist_ValidSearch_DelegatesToRepository")
    void persist_ValidSearch_DelegatesToRepository() {
        // given
        FakeRepository repo = new FakeRepository();
        PersistHotelSearch useCase = new PersistHotelSearch(repo);
        HotelSearch search = new HotelSearch(
            SearchId.generate(),
            HotelId.of("550e8400-e29b-41d4-a716-446655440000"),
            CheckIn.of(java.time.LocalDate.of(2026, 11, 10)),
            CheckOut.of(java.time.LocalDate.of(2026, 11, 29)),
            Ages.of(List.of(29)));

        // when
        useCase.execute(search);

        // then
        assertThat(repo.saved).as("El repositorio debe haber guardado la búsqueda").isTrue();
    }

    /**
     * Verifica que persist rechaza un search nulo.
     *
     * Condiciones:
     * - El search recibido es null
     *
     * Se espera:
     * - Se lance NullPointerException
     */
    @Test
    @DisplayName("persist_NullSearch_ThrowsNullPointerException")
    void persist_NullSearch_ThrowsNullPointerException() {
        // given
        PersistHotelSearch useCase = new PersistHotelSearch(new FakeRepository());

        // when / then
        assertThatThrownBy(() -> useCase.execute(null))
            .as("Un search nulo debe lanzar NullPointerException")
            .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifica que persist rechaza un repository nulo en el constructor.
     *
     * Condiciones:
     * - El repository recibido en el constructor es null
     *
     * Se espera:
     * - Se lance NullPointerException
     */
    @Test
    @DisplayName("persist_NullRepository_ThrowsNullPointerException")
    void persist_NullRepository_ThrowsNullPointerException() {
        // given / when / then
        assertThatThrownBy(() -> new PersistHotelSearch(null))
            .as("Un repository nulo debe lanzar NullPointerException")
            .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifica que persist ignora el duplicate key (PK) al guardar el mismo searchId dos veces.
     *
     * Condiciones:
     * - Se llama a save dos veces con el mismo searchId válido
     *
     * Se espera:
     * - La primera llamada guarde correctamente
     * - La segunda llamada no lance excepción (defensa de PK)
     */
    @Test
    @DisplayName("persist_DuplicateSearchIdTwice_NoExceptionThrown")
    void persist_DuplicateSearchIdTwice_NoExceptionThrown() {
        // given
        SearchId searchId = SearchId.generate();
        HotelSearch search = new HotelSearch(
            searchId,
            HotelId.of("550e8400-e29b-41d4-a716-446655440000"),
            CheckIn.of(java.time.LocalDate.of(2026, 11, 10)),
            CheckOut.of(java.time.LocalDate.of(2026, 11, 29)),
            Ages.of(List.of(29)));

        // when / then
        PersistHotelSearch useCase = new PersistHotelSearch(new FakeRepository());
        assertThatCode(() -> useCase.execute(search))
            .as("Primera ejecución debe guardar sin excepción")
            .doesNotThrowAnyException();

        assertThatCode(() -> useCase.execute(search))
            .as("Segunda ejecución con mismo searchId no debe lanzar excepción")
            .doesNotThrowAnyException();
    }
}
