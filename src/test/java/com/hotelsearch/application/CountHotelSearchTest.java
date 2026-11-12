package com.hotelsearch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.model.HotelSearchWithCount;
import com.hotelsearch.application.port.HotelSearchRepository;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;

class CountHotelSearchTest {

    /**
     * Verifica que el caso de uso devuelve la búsqueda y el conteo de
     * equivalentes cuando el searchId existe.
     *
     * Condiciones:
     * - El repositorio devuelve la búsqueda con dos equivalentes
     *
     * Se espera:
     * - El resultado expone la búsqueda encontrada
     * - El conteo de equivalentes es 2
     */
    @Test
    void count_SearchExists_ReturnsSearchAndEquivalentsCount() {
        // given
        HotelSearchRepository repository = mock(HotelSearchRepository.class);
        SearchId id = SearchId.generate();
        HotelSearch search = validSearch(id);
        when(repository.findByIdWithCount(id))
            .thenReturn(Optional.of(new HotelSearchWithCount(search, 2)));
        CountHotelSearch useCase = new CountHotelSearch(repository);

        // when
        CountHotelSearch.Result result = useCase.execute(new CountHotelSearch.Query(id.toString()));

        // then
        assertThat(result.search()).as("Se debe devolver la búsqueda encontrada").isEqualTo(search);
        assertThat(result.equivalentsCount()).as("Se debe devolver el conteo de equivalentes").isEqualTo(2);
    }

    /**
     * Verifica que un searchId inexistente se distingue de un conteo en
     * cero: el caso de uso falla en lugar de devolver un resultado con
     * conteo cero.
     *
     * Condiciones:
     * - El repositorio no encuentra el searchId consultado
     *
     * Se espera:
     * - Se lance SearchNotFoundException
     */
    @Test
    void count_SearchNotFound_ThrowsSearchNotFoundException() {
        // given
        HotelSearchRepository repository = mock(HotelSearchRepository.class);
        when(repository.findByIdWithCount(SearchId.of("550e8400-e29b-41d4-a716-446655440000")))
            .thenReturn(Optional.empty());
        CountHotelSearch useCase = new CountHotelSearch(repository);

        // when / then
        assertThatThrownBy(() -> useCase.execute(new CountHotelSearch.Query(
            "550e8400-e29b-41d4-a716-446655440000")))
            .as("Un searchId inexistente debe lanzar SearchNotFoundException")
            .isInstanceOf(SearchNotFoundException.class);
    }

    /**
     * Verifica que el caso de uso rechaza un query nulo.
     *
     * Condiciones:
     * - El query recibido es null
     *
     * Se espera:
     * - Se lance NullPointerException
     */
    @Test
    void count_NullQuery_ThrowsNullPointerException() {
        // given
        CountHotelSearch useCase = new CountHotelSearch(mock(HotelSearchRepository.class));

        // when / then
        assertThatThrownBy(() -> useCase.execute(null))
            .as("Un query nulo debe lanzar NullPointerException")
            .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifica que el caso de uso rechaza un searchId con formato inválido.
     *
     * Condiciones:
     * - El query contiene un string que no es un UUID de 36 caracteres
     *
     * Se espera:
     * - Se lance IllegalArgumentException
     */
    @Test
    void count_InvalidSearchIdFormat_ThrowsIllegalArgumentException() {
        // given
        CountHotelSearch useCase = new CountHotelSearch(mock(HotelSearchRepository.class));

        // when / then
        assertThrows(IllegalArgumentException.class,
            () -> useCase.execute(new CountHotelSearch.Query("no-es-un-uuid")));
    }

    /**
     * Verifica que el caso de uso rechaza un searchId con longitud incorrecta.
     *
     * Condiciones:
     * - El query contiene un UUID muy corto (35 caracteres) o muy largo (37 caracteres)
     *
     * Se espera:
     * - Se lance IllegalArgumentException en cada caso
     */
    @Test
    void count_InvalidSearchId_WrongLength_ThrowsIllegalArgumentException() {
        // given
        CountHotelSearch useCase = new CountHotelSearch(mock(HotelSearchRepository.class));

        // when / then
        assertThrows(IllegalArgumentException.class,
            () -> useCase.execute(new CountHotelSearch.Query("550e8400-e29b-41d4-a716-44665544000")));
        assertThrows(IllegalArgumentException.class,
            () -> useCase.execute(new CountHotelSearch.Query("550e8400-e29b-41d4-a716-4466554400000")));
    }

    /**
     * Verifica que el caso de uso rechaza un query con espacios en blanco.
     *
     * Condiciones:
     * - El query contiene un string con espacios al inicio o al final
     *
     * Se espera:
     * - Se lance IllegalArgumentException en cada caso
     */
    @Test
    void count_InvalidQuery_Whitespace_ThrowsIllegalArgumentException() {
        // given
        CountHotelSearch useCase = new CountHotelSearch(mock(HotelSearchRepository.class));

        // when / then
        assertThrows(IllegalArgumentException.class,
            () -> useCase.execute(new CountHotelSearch.Query(" 550e8400-e29b-41d4-a716-446655440000")));
        assertThrows(IllegalArgumentException.class,
            () -> useCase.execute(new CountHotelSearch.Query("550e8400-e29b-41d4-a716-446655440000 ")));
    }

    // <editor-fold desc="Helpers">
    private HotelSearch validSearch(SearchId searchId) {
        return new HotelSearch(
            searchId,
            HotelId.of("550e8400-e29b-41d4-a716-446655440000"),
            CheckIn.of(LocalDate.of(2026, 11, 10)),
            CheckOut.of(LocalDate.of(2026, 11, 29)),
            Ages.of(List.of(29, 28, 4, 3)));
    }
    // </editor-fold>
}
