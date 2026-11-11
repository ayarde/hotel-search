package com.hotelsearch.domain.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import com.hotelsearch.domain.port.HotelSearchRepository;

class CountHotelSearchTest {

    private static class FakeRepository implements HotelSearchRepository {
        private HotelSearch stored;

        @Override
        public void save(HotelSearch search) {
            this.stored = search;
        }

        @Override
        public Optional<HotelSearch> findById(SearchId searchId) {
            if (stored != null && stored.searchId().equals(searchId)) {
                return Optional.of(stored);
            }
            return Optional.empty();
        }

        @Override
        public int countEquivalents(HotelSearch search, SearchId excludeSearchId) {
            if (stored == null || !stored.equals(search)) {
                return 0;
            }
            return 2;
        }
    }

    /**
     * Verifica que count devuelve la búsqueda y el conteo de equivalentes cuando existe.
     *
     * Condiciones:
     * - El repositorio contiene una búsqueda con el searchId consultado
     * - Existen dos búsquedas equivalentes excluyendo la consultada
     *
     * Se espera:
     * - Se devuelva la búsqueda encontrada
     * - El conteo de equivalentes sea 2
     */
    @Test
    @DisplayName("count_SearchExists_ReturnsSearchAndEquivalentsCount")
    void count_SearchExists_ReturnsSearchAndEquivalentsCount() {
        // given
        FakeRepository repo = new FakeRepository();
        SearchId id = SearchId.generate();
        HotelId hotel = HotelId.of("550e8400-e29b-41d4-a716-446655440000");
        CheckIn checkIn = CheckIn.of(LocalDate.of(2026, 11, 10));
        CheckOut checkOut = CheckOut.of(LocalDate.of(2026, 11, 29));
        Ages ages = Ages.of(List.of(29, 28, 4, 3));
        HotelSearch search = new HotelSearch(id, hotel, checkIn, checkOut, ages);
        repo.save(search);

        CountHotelSearch useCase = new CountHotelSearch(repo);

        // when
        CountHotelSearch.Result result = useCase.execute(new CountHotelSearch.Query(id.toString()));

        // then
        assertThat(result.search()).as("Se debe devolver la búsqueda encontrada").isEqualTo(search);
        assertThat(result.equivalentsCount()).as("Se debe devolver el conteo de equivalentes").isEqualTo(2);
    }

    /**
     * Verifica que count lanza excepción cuando el searchId no existe.
     *
     * Condiciones:
     * - El repositorio no contiene ninguna búsqueda
     * - El searchId consultado no existe
     *
     * Se espera:
     * - Se lance IllegalArgumentException
     */
    @Test
    @DisplayName("count_SearchNotFound_ThrowsIllegalArgumentException")
    void count_SearchNotFound_ThrowsIllegalArgumentException() {
        // given
        FakeRepository repo = new FakeRepository();
        CountHotelSearch useCase = new CountHotelSearch(repo);

        // when / then
        assertThatThrownBy(() -> useCase.execute(new CountHotelSearch.Query(
            "550e8400-e29b-41d4-a716-446655440000")))
            .as("Un searchId inexistente debe lanzar IllegalArgumentException")
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifica que count rechaza un query nulo.
     *
     * Condiciones:
     * - El query recibido es null
     *
     * Se espera:
     * - Se lance NullPointerException
     */
    @Test
    @DisplayName("count_NullQuery_ThrowsNullPointerException")
    void count_NullQuery_ThrowsNullPointerException() {
        // given
        CountHotelSearch useCase = new CountHotelSearch(new FakeRepository());

        // when / then
        assertThatThrownBy(() -> useCase.execute(null))
            .as("Un query nulo debe lanzar NullPointerException")
            .isInstanceOf(NullPointerException.class);
    }

    // <editor-fold desc="Helpers">
    // sin helpers adicionales por ahora
    // </editor-fold>
}
