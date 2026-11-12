package com.hotelsearch.infrastructure.config;

import com.hotelsearch.infrastructure.output.kafka.KafkaHotelSearchPublisher;
import com.hotelsearch.application.port.HotelSearchPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Configuración de adaptadores de salida Kafka.
 *
 * <p>Define los beans de infraestructura para la publicación de eventos
 * en Kafka. El {@link KafkaHotelSearchPublisher} implementa el puerto
 * {@link HotelSearchPublisher} y se configura como bean singleton.</p>
 */
@Configuration
public class KafkaOutputConfig {

    @Value("${app.kafka.topics.hotel-availability-searches}")
    private String searchesTopic;

    @Bean
    public HotelSearchPublisher hotelSearchPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        return new KafkaHotelSearchPublisher(kafkaTemplate, searchesTopic);
    }
}
