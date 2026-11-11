package com.hotelsearch.domain.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import com.hotelsearch.domain.port.HotelSearchRepository;

class PersistHotelSearchTest {

    private static class FakeRepository implements HotelSearchRepository {
        private boolean saved;

        @Override
        public void save(HotelSearch search) {
            this.saved = true;
        }

        @Override
        public java.util.Optional<HotelSearch> findById(SearchId searchId) {
            return java.util.Optional.empty();
        }

        @Override
        public int countEquivalents(HotelSearch search, SearchId excludeSearchId) {
            return 0;
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
}
