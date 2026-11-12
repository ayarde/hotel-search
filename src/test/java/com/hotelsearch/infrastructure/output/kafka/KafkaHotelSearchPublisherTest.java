package com.hotelsearch.infrastructure.output.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.hotelsearch.application.HotelSearchUnavailableException;
import com.hotelsearch.domain.model.HotelSearchEvent;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Tests de la traducción de fallos del producer Kafka al error de
 * disponibilidad de la aplicación.
 */
class KafkaHotelSearchPublisherTest {

    /**
     * Verifica que un timeout confirmado por el future se traduzca a error de
     * disponibilidad.
     *
     * Condiciones:
     * - El future del publish falla con TimeoutException de Kafka
     *
     * Se espera:
     * - Se lance HotelSearchUnavailableException
     */
    @Test
    void publish_BrokerTimeout_ThrowsUnavailableException() {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new TimeoutException("timeout"));
        doReturn(failed).when(template).send(any(ProducerRecord.class));
        KafkaHotelSearchPublisher publisher = new KafkaHotelSearchPublisher(template, "hotel_availability_searches");

        // when / then
        assertThatThrownBy(() -> publisher.publish(validEvent()))
            .isInstanceOf(HotelSearchUnavailableException.class)
            .hasMessageContaining("Timeout");
    }

    /**
     * Verifica que un fallo general confirmado por el future se traduzca a
     * error de disponibilidad.
     *
     * Condiciones:
     * - El future del publish falla con una causa genérica
     *
     * Se espera:
     * - Se lance HotelSearchUnavailableException
     */
    @Test
    void publish_BrokerFailure_ThrowsUnavailableException() {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker caído"));
        doReturn(failed).when(template).send(any(ProducerRecord.class));
        KafkaHotelSearchPublisher publisher = new KafkaHotelSearchPublisher(template, "hotel_availability_searches");

        // when / then
        assertThatThrownBy(() -> publisher.publish(validEvent()))
            .isInstanceOf(HotelSearchUnavailableException.class)
            .hasMessageContaining("Error publicando");
    }

    /**
     * Verifica que un error Kafka lanzado sincrónicamente por el producer se
     * traduzca a error de disponibilidad.
     *
     * Condiciones:
     * - KafkaTemplate.send lanza KafkaException
     *
     * Se espera:
     * - Se lance HotelSearchUnavailableException con la causa original
     */
    @Test
    void publish_SynchronousKafkaFailure_ThrowsUnavailableException() {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        KafkaException cause = new KafkaException("producer cerrado");
        doThrow(cause).when(template).send(any(ProducerRecord.class));
        KafkaHotelSearchPublisher publisher = new KafkaHotelSearchPublisher(template, "hotel_availability_searches");

        // when
        var exception = org.junit.jupiter.api.Assertions.assertThrows(
            HotelSearchUnavailableException.class,
            () -> publisher.publish(validEvent()));

        // then
        assertThat(exception.getCause()).isSameAs(cause);
    }

    // <editor-fold desc="Helpers">
    private HotelSearchEvent validEvent() {
        return new HotelSearchEvent(
            SearchId.of("f47ac10b-58cc-4372-a567-0e02b2c3d479"),
            HotelId.of("550e8400-e29b-41d4-a716-446655440000"),
            CheckIn.of(LocalDate.of(2026, 11, 9)),
            CheckOut.of(LocalDate.of(2026, 11, 29)),
            Ages.of(List.of(29, 28, 4, 3)));
    }
    // </editor-fold>
}
