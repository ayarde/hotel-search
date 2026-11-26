package com.hotelsearch.infrastructure.output.kafka;

import com.hotelsearch.application.HotelSearchUnavailableException;
import com.hotelsearch.application.port.HotelSearchPublisher;
import com.hotelsearch.domain.model.HotelSearchEvent;
import com.hotelsearch.infrastructure.observability.CorrelationContext;
import com.hotelsearch.infrastructure.observability.MdcScope;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Adaptador Kafka del puerto {@link HotelSearchPublisher}.
 *
 * <p>Publica el evento en el topic {@code hotel_availability_searches}
 * usando el {@code searchId} como clave del mensaje. Espera la
 * confirmación (ACK) del broker antes de retornar; si falla o expira
 * el timeout, lanza una excepción de indisponibilidad que la capa HTTP
 * traduce a 503.</p>
 */
public class KafkaHotelSearchPublisher implements HotelSearchPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaHotelSearchPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    /**
     * @param kafkaTemplate template que envía los eventos
     * @param topic topic de búsquedas
     */
    public KafkaHotelSearchPublisher(
        KafkaTemplate<String, Object> kafkaTemplate,
        @org.springframework.beans.factory.annotation.Value("${app.kafka.topics.hotel-availability-searches}")
        String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(HotelSearchEvent event) {
        String searchId = event.searchId().toString();

        try (MdcScope _ = MdcScope.search(
            CorrelationContext.KAFKA_PRODUCER_COMPONENT, searchId)) {
            var producerRecord = new ProducerRecord<String, Object>(topic, searchId, event);
            producerRecord.headers().add(
                CorrelationContext.SEARCH_ID,
                searchId.getBytes(StandardCharsets.UTF_8));

            try {
                SendResult<String, Object> result = kafkaTemplate.send(producerRecord).get();
                logPublished(result, searchId);
            } catch (InterruptedException e) {
                restoreInterruptFlag();
                LOG.error("Publicación interrumpida para searchId={}", searchId, e);
                throw new HotelSearchUnavailableException("Publicación interrumpida", e);
            } catch (ExecutionException e) {
                throw publicationFailure(e, searchId);
            } catch (KafkaException e) {
                LOG.error("Kafka no está disponible para searchId={}", searchId, e);
                throw new HotelSearchUnavailableException("Kafka no está disponible", e);
            }
        }
    }

    private static void logPublished(SendResult<String, Object> result, String searchId) {
        if (LOG.isDebugEnabled()) {
            var metadata = result.getRecordMetadata();
            LOG.debug("Evento publicado: topic={}, partition={}, offset={}, key={}",
                metadata.topic(), metadata.partition(), metadata.offset(), searchId);
        }
    }

    // Restablecer el flag es la forma correcta de no perder la interrupción.
    private static void restoreInterruptFlag() {
        Thread.currentThread().interrupt();
    }

    /**
     * Traduce el fallo asíncrono del envío. El log es el mismo para todos
     * los fallos; lo que distingue un timeout del broker de cualquier otro
     * error es el mensaje de la excepción devuelta. Ambos casos terminan en
     * 503.
     */
    private static HotelSearchUnavailableException publicationFailure(
        ExecutionException exception,
        String searchId
    ) {
        Throwable cause = exception.getCause();
        String reason = cause == null ? exception.getMessage() : cause.getMessage();
        LOG.error("Fallo publicando evento searchId={}: {}", searchId, reason, exception);
        if (cause instanceof TimeoutException) {
            return new HotelSearchUnavailableException("Timeout publicando en Kafka", exception);
        }
        return new HotelSearchUnavailableException("Error publicando en Kafka", exception);
    }
}
