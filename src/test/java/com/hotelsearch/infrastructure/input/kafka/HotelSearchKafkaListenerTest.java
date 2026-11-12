package com.hotelsearch.infrastructure.input.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hotelsearch.application.PersistHotelSearch;
import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.model.HotelSearchEvent;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Unit tests del listener Kafka de búsquedas.
 */
class HotelSearchKafkaListenerTest {

    private final HotelSearchEventMapper mapper = mock(HotelSearchEventMapper.class);
    private final PersistHotelSearch persistHotelSearch = mock(PersistHotelSearch.class);
    private final HotelSearchKafkaListener listener =
        new HotelSearchKafkaListener(mapper, persistHotelSearch);

    /**
     * Verifica que el listener valide el evento y lo delegue al servicio
     * transaccional de persistencia.
     *
     * Condiciones:
     * - El mapper devuelve una búsqueda válida
     *
     * Se espera:
     * - PersistHotelSearch reciba exactamente esa búsqueda
     */
    @Test
    void consume_ValidEvent_PersistsMappedSearch() {
        // given
        HotelSearchEvent event = mock(HotelSearchEvent.class);
        HotelSearch search = searchOf(SearchId.generate());
        when(mapper.toHotelSearch(event)).thenReturn(search);

        // when
        listener.consume(event);

        // then
        verify(persistHotelSearch).execute(search);
    }

    /**
     * Verifica que un evento inválido no llegue a la persistencia y que el
     * error permanente se propague al error handler.
     *
     * Condiciones:
     * - El mapper rechaza el evento
     *
     * Se espera:
     * - Se propague InvalidHotelSearchEventException
     * - No se invoque el repositorio
     */
    @Test
    void consume_InvalidEvent_PropagatesErrorWithoutPersisting() {
        // given
        HotelSearchEvent event = mock(HotelSearchEvent.class);
        var error = new InvalidHotelSearchEventException("evento inválido");
        when(mapper.toHotelSearch(event)).thenThrow(error);

        // when / then
        assertThatThrownBy(() -> listener.consume(event))
            .as("El error permanente debe llegar al error handler")
            .isSameAs(error);
        verify(persistHotelSearch, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    /**
     * Verifica que un fallo transitorio de Oracle se propague sin convertirlo
     * en un error de formato.
     *
     * Condiciones:
     * - La persistencia falla por indisponibilidad de Oracle
     *
     * Se espera:
     * - Se propague DataAccessResourceFailureException
     */
    @Test
    void consume_TransientPersistenceFailure_PropagatesDatabaseError() {
        // given
        HotelSearchEvent event = mock(HotelSearchEvent.class);
        HotelSearch search = searchOf(SearchId.generate());
        var error = new DataAccessResourceFailureException("Oracle no disponible");
        when(mapper.toHotelSearch(event)).thenReturn(search);
        org.mockito.Mockito.doThrow(error).when(persistHotelSearch).execute(search);

        // when / then
        assertThatThrownBy(() -> listener.consume(event))
            .as("El error transitorio debe conservar su tipo")
            .isSameAs(error);
    }

    // <editor-fold desc="Helpers">
    private HotelSearch searchOf(SearchId searchId) {
        return new HotelSearch(
            searchId,
            HotelId.of("550e8400-e29b-41d4-a716-446655440000"),
            CheckIn.of(LocalDate.of(2026, 11, 10)),
            CheckOut.of(LocalDate.of(2026, 11, 29)),
            Ages.of(List.of(29, 28, 4, 3)));
    }
    // </editor-fold>
}
