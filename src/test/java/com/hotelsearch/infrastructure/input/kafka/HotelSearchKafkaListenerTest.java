package com.hotelsearch.infrastructure.input.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
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
import com.hotelsearch.infrastructure.observability.CorrelationContext;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
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
        HotelSearchEvent event = eventOf(SearchId.generate());
        HotelSearch search = searchOf(event.searchId());
        when(mapper.toHotelSearch(event)).thenReturn(search);

        // when
        listener.consume(recordWithSearchId(event));

        // then
        verify(persistHotelSearch).execute(search);
    }

    /**
     * Verifica que el listener establezca la correlación durante la
     * persistencia y retire el MDC al finalizar.
     *
     * Condiciones:
     * - El evento incluye el header searchId
     *
     * Se espera:
     * - La persistencia vea component=kafka-consumer y el searchId correcto
     * - El MDC quede vacío después del consumo
     */
    @Test
    void consume_ValidEvent_SetsCorrelationAndClearsMdc() {
        // given
        HotelSearchEvent event = eventOf(SearchId.generate());
        HotelSearch search = searchOf(event.searchId());
        when(mapper.toHotelSearch(event)).thenReturn(search);
        doAnswer(invocation -> {
            assertThat(MDC.getCopyOfContextMap())
                .as("La persistencia debe ejecutarse dentro del scope de correlación")
                .containsEntry(CorrelationContext.COMPONENT,
                    CorrelationContext.KAFKA_CONSUMER_COMPONENT)
                .containsEntry(CorrelationContext.SEARCH_ID, event.searchId().toString());
            return null;
        }).when(persistHotelSearch).execute(search);

        // when
        listener.consume(recordWithSearchId(event));

        // then
        assertThat(MDC.getCopyOfContextMap())
            .as("El listener debe limpiar el MDC al finalizar")
            .isNull();
    }

    /**
     * Verifica que un evento sin header de correlación use el searchId del
     * propio evento.
     *
     * Condiciones:
     * - El registro Kafka no contiene el header searchId
     *
     * Se espera:
     * - El MDC use el searchId canónico del evento
     */
    @Test
    void consume_MissingCorrelationHeader_UsesEventSearchId() {
        // given
        HotelSearchEvent event = eventOf(SearchId.generate());
        HotelSearch search = searchOf(event.searchId());
        when(mapper.toHotelSearch(event)).thenReturn(search);
        doAnswer(invocation -> {
            assertThat(MDC.get(CorrelationContext.SEARCH_ID))
                .isEqualTo(event.searchId().toString());
            return null;
        }).when(persistHotelSearch).execute(search);

        // when
        listener.consume(recordWithoutSearchId(event));

        // then
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    /**
     * Verifica que un header de correlación inválido no reemplace el
     * searchId canónico del evento.
     *
     * Condiciones:
     * - El header searchId no contiene un UUID
     *
     * Se espera:
     * - El MDC use el searchId del evento
     */
    @Test
    void consume_InvalidCorrelationHeader_UsesEventSearchId() {
        // given
        HotelSearchEvent event = eventOf(SearchId.generate());
        HotelSearch search = searchOf(event.searchId());
        when(mapper.toHotelSearch(event)).thenReturn(search);
        doAnswer(invocation -> {
            assertThat(MDC.get(CorrelationContext.SEARCH_ID))
                .isEqualTo(event.searchId().toString());
            return null;
        }).when(persistHotelSearch).execute(search);
        ConsumerRecord<String, HotelSearchEvent> consumerRecord = recordWithoutSearchId(event);
        consumerRecord.headers().add(
            CorrelationContext.SEARCH_ID,
            "no-es-uuid".getBytes(StandardCharsets.UTF_8));

        // when
        listener.consume(consumerRecord);

        // then
        assertThat(MDC.getCopyOfContextMap()).isNull();
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
     * - El MDC quede limpio
     */
    @Test
    void consume_InvalidEvent_PropagatesErrorWithoutPersisting() {
        // given
        HotelSearchEvent event = eventOf(SearchId.generate());
        var error = new InvalidHotelSearchEventException("evento inválido");
        when(mapper.toHotelSearch(event)).thenThrow(error);

        // when
        var exception = assertThrows(InvalidHotelSearchEventException.class,
            () -> listener.consume(recordWithSearchId(event)));

        // then
        assertThat(exception).isSameAs(error);
        verify(persistHotelSearch, never()).execute(org.mockito.ArgumentMatchers.any());
        assertThat(MDC.getCopyOfContextMap()).isNull();
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
     * - El MDC quede limpio
     */
    @Test
    void consume_TransientPersistenceFailure_PropagatesDatabaseError() {
        // given
        HotelSearchEvent event = eventOf(SearchId.generate());
        HotelSearch search = searchOf(event.searchId());
        var error = new DataAccessResourceFailureException("Oracle no disponible");
        when(mapper.toHotelSearch(event)).thenReturn(search);
        org.mockito.Mockito.doThrow(error).when(persistHotelSearch).execute(search);

        // when
        var exception = assertThrows(DataAccessResourceFailureException.class,
            () -> listener.consume(recordWithSearchId(event)));

        // then
        assertThat(exception).isSameAs(error);
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    // <editor-fold desc="Helpers">
    private HotelSearchEvent eventOf(SearchId searchId) {
        return new HotelSearchEvent(
            searchId,
            HotelId.of("550e8400-e29b-41d4-a716-446655440000"),
            CheckIn.of(LocalDate.of(2026, 11, 10)),
            CheckOut.of(LocalDate.of(2026, 11, 29)),
            Ages.of(List.of(29, 28, 4, 3)));
    }

    private HotelSearch searchOf(SearchId searchId) {
        return new HotelSearch(
            searchId,
            HotelId.of("550e8400-e29b-41d4-a716-446655440000"),
            CheckIn.of(LocalDate.of(2026, 11, 10)),
            CheckOut.of(LocalDate.of(2026, 11, 29)),
            Ages.of(List.of(29, 28, 4, 3)));
    }

    private ConsumerRecord<String, HotelSearchEvent> recordWithSearchId(HotelSearchEvent event) {
        ConsumerRecord<String, HotelSearchEvent> consumerRecord = recordWithoutSearchId(event);
        consumerRecord.headers().add(
            CorrelationContext.SEARCH_ID,
            event.searchId().toString().getBytes(StandardCharsets.UTF_8));
        return consumerRecord;
    }

    private ConsumerRecord<String, HotelSearchEvent> recordWithoutSearchId(HotelSearchEvent event) {
        return new ConsumerRecord<>(
            "hotel_availability_searches",
            0,
            12L,
            event.searchId().toString(),
            event);
    }
    // </editor-fold>
}
