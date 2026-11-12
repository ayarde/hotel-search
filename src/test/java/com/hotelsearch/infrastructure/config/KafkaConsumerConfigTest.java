package com.hotelsearch.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hotelsearch.infrastructure.input.kafka.InvalidHotelSearchEventException;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Unit tests de la política de reintentos y recuperación del consumer Kafka.
 */
class KafkaConsumerConfigTest {

    private static final String DLT_TOPIC = "hotel_search_dlt";

    private final KafkaConsumerConfig config = new KafkaConsumerConfig();

    /**
     * Verifica que la fábrica del listener use confirmación por registro.
     *
     * Condiciones:
     * - Se crea la fábrica con un consumer factory tipado
     *
     * Se espera:
     * - El modo de confirmación sea RECORD
     */
    @Test
    void kafkaListenerContainerFactory_Configured_UsesRecordAckMode() {
        // given
        var consumerFactory = mock(org.springframework.kafka.core.ConsumerFactory.class);

        // when
        var factory = config.kafkaListenerContainerFactory(
            consumerFactory,
            mock(DefaultErrorHandler.class),
            ObservationRegistry.create(),
            kafkaProperties(true));

        // then
        assertThat(factory.getContainerProperties().getAckMode())
            .as("El offset debe confirmarse por registro")
            .isEqualTo(ContainerProperties.AckMode.RECORD);
    }

    /**
     * Verifica que la fábrica del listener habilite las observations de
     * Micrometer y use el registro proporcionado.
     *
     * Condiciones:
     * - La propiedad de observations del listener está habilitada
     *
     * Se espera:
     * - El container tenga observations habilitadas
     * - El container use el ObservationRegistry inyectado
     */
    @Test
    void kafkaListenerContainerFactory_ObservationsEnabled_ConfiguresContainerProperties() {
        // given
        var consumerFactory = mock(org.springframework.kafka.core.ConsumerFactory.class);
        var observationRegistry = ObservationRegistry.create();

        // when
        var factory = config.kafkaListenerContainerFactory(
            consumerFactory,
            mock(DefaultErrorHandler.class),
            observationRegistry,
            kafkaProperties(true));

        // then
        assertThat(factory.getContainerProperties().isObservationEnabled())
            .as("El listener debe crear observations")
            .isTrue();
        assertThat(factory.getContainerProperties().getObservationRegistry())
            .as("El listener debe usar el registro de la aplicación")
            .isSameAs(observationRegistry);
    }

    /**
     * Verifica que un error permanente se envíe al DLT sin esperar los tres
     * reintentos adicionales.
     *
     * Condiciones:
     * - El evento tiene una estructura inválida
     * - La publicación al DLT responde correctamente
     *
     * Se espera:
     * - Se publique una sola vez en hotel_search_dlt
     * - Se conserve la key del mensaje original
     */
    @Test
    void kafkaErrorHandler_PermanentError_RecoversWithoutRetry() {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        DefaultErrorHandler handler = config.kafkaErrorHandler(template, DLT_TOPIC);
        ConsumerRecord<String, Object> record = record();

        // when
        handler.handleOne(
            new InvalidHotelSearchEventException("evento inválido"),
            record,
            mock(Consumer.class),
            mock(MessageListenerContainer.class));

        // then
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        assertThat(captor.getValue().topic()).as("El mensaje debe ir al DLT configurado")
            .isEqualTo(DLT_TOPIC);
        assertThat(captor.getValue().key()).as("La key original debe conservarse")
            .isEqualTo(record.key());
    }

    /**
     * Verifica que un error transitorio se reintente tres veces antes de
     * publicarse en el DLT.
     *
     * Condiciones:
     * - Oracle está indisponible y la publicación al DLT funciona
     *
     * Se espera:
     * - No haya publicación durante los primeros tres intentos
     * - El cuarto intento publique en el DLT
     */
    @Test
    void kafkaErrorHandler_TransientError_RetriesThreeTimesThenRecovers() {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        DefaultErrorHandler handler = config.kafkaErrorHandler(template, DLT_TOPIC);
        ConsumerRecord<String, Object> record = record();

        // when
        for (int attempt = 0; attempt < 3; attempt++) {
            handler.handleOne(
                new DataAccessResourceFailureException("Oracle no disponible"),
                record,
                mock(Consumer.class),
                mock(MessageListenerContainer.class));
        }
        verify(template, never()).send(any(ProducerRecord.class));

        handler.handleOne(
            new DataAccessResourceFailureException("Oracle no disponible"),
            record,
            mock(Consumer.class),
            mock(MessageListenerContainer.class));

        // then
        verify(template).send(any(ProducerRecord.class));
    }

    /**
     * Verifica que un fallo al publicar en el DLT impida considerar el
     * mensaje recuperado y confirme su offset original.
     *
     * Condiciones:
     * - La publicación al DLT termina con error
     *
     * Se espera:
     * - El error handler indique que el registro no fue manejado
     */
    @Test
    void kafkaErrorHandler_DltPublicationFails_DoesNotRecoverRecord() {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        CompletableFuture<org.springframework.kafka.support.SendResult<String, Object>> failedSend =
            new CompletableFuture<>();
        failedSend.completeExceptionally(new IllegalStateException("broker no disponible"));
        when(template.send(any(ProducerRecord.class))).thenReturn(failedSend);
        DefaultErrorHandler handler = config.kafkaErrorHandler(template, DLT_TOPIC);

        // when
        boolean handled = handler.handleOne(
            new InvalidHotelSearchEventException("evento inválido"),
            record(),
            mock(Consumer.class),
            mock(MessageListenerContainer.class));

        // then
        assertThat(handled).as("El offset original no debe confirmarse")
            .isFalse();
        verify(template).send(any(ProducerRecord.class));
    }

    // <editor-fold desc="Helpers">
    private KafkaProperties kafkaProperties(boolean observationEnabled) {
        var properties = new KafkaProperties();
        properties.getListener().setObservationEnabled(observationEnabled);
        return properties;
    }

    private ConsumerRecord<String, Object> record() {
        return new ConsumerRecord<>(
            "hotel_availability_searches",
            0,
            12L,
            "550e8400-e29b-41d4-a716-446655440000",
            new Object());
    }
    // </editor-fold>
}
