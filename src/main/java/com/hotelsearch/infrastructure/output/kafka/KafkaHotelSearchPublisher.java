package com.hotelsearch.infrastructure.output.kafka;

import com.hotelsearch.application.port.HotelSearchPublisher;
import com.hotelsearch.domain.model.HotelSearchEvent;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.ProducerRecord;
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
 * el timeout, lanza una excepción que el caso de uso traduce a 503.</p>
 */
public class KafkaHotelSearchPublisher implements HotelSearchPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaHotelSearchPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

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
        var record = new ProducerRecord<String, Object>(topic, event.searchId().value().toString(), event);

        try {
            SendResult<String, Object> result = kafkaTemplate.send(record).get();
            log.debug("Evento publicado: topic={}, partition={}, offset={}, key={}",
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset(),
                event.searchId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Publicación interrumpida para searchId={}", event.searchId(), e);
            throw new IllegalStateException("Publicación interrumpida", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("Fallo publicando evento searchId={}: {}", event.searchId(),
                cause != null ? cause.getMessage() : e.getMessage(), e);
            if (cause instanceof TimeoutException) {
                throw new IllegalStateException("Timeout publicando en Kafka", e);
            }
            throw new IllegalStateException("Error publicando en Kafka", e);
        }
    }
}
