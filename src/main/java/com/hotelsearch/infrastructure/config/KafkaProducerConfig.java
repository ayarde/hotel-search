package com.hotelsearch.infrastructure.config;

import io.micrometer.observation.ObservationRegistry;
import java.util.HashMap;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuración del producer Kafka para eventos de búsqueda de hotel.
 *
 * <p>Crea un {@link ProducerFactory} que usa la configuración estándar
 * de Spring Boot ({@code spring.kafka.producer.*}) leída vía {@link Environment}
 * pero sobrescribe el serializador de valores para usar
 * {@link JacksonJsonSerializer} con Jackson 3.</p>
 *
 * <p>Los topics {@code hotel_availability_searches} y {@code hotel_search_dlt}
 * se crean con una partición y replication factor 1.</p>
 */
@Configuration
public class KafkaProducerConfig {

    private final Environment env;

    @Value("${app.kafka.topics.hotel-availability-searches}")
    private String searchesTopicName;

    @Value("${app.kafka.topics.hotel-search-dlt}")
    private String dltTopicName;

    /**
     * @param env entorno de Spring del que se leen las propiedades del producer
     */
    public KafkaProducerConfig(Environment env) {
        this.env = env;
    }

    /**
     * Arma el factory con las propiedades de {@code spring.kafka.*} y sus
     * valores por defecto. Los eventos se serializan como JSON sin headers
     * de tipo, así el consumer no depende del nombre de la clase Java.
     *
     * @param jsonMapper mapper Jackson 3 de la aplicación
     * @return factory de producers del topic de búsquedas
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory(JsonMapper jsonMapper) {
        var props = new HashMap<String, Object>();

        // Leer propiedades de spring.kafka.producer.* del environment
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            env.getProperty("spring.kafka.bootstrap-servers", "localhost:9092"));
        props.put(ProducerConfig.ACKS_CONFIG,
            env.getProperty("spring.kafka.producer.acks", "all"));
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
            env.getProperty("spring.kafka.producer.enable-idempotence", "true"));
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
            env.getProperty("spring.kafka.producer.delivery-timeout-ms", "10000"));
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
            env.getProperty("spring.kafka.producer.request-timeout-ms", "3000"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            org.apache.kafka.common.serialization.StringSerializer.class);

        var factory = new DefaultKafkaProducerFactory<String, Object>(props);

        // El factory toma el serializador y lo cierra junto con cada producer.
        @SuppressWarnings("PMD.CloseResource")
        var jsonSerializer = new JacksonJsonSerializer<Object>(jsonMapper);
        jsonSerializer.setAddTypeInfo(false);
        factory.setValueSerializer(jsonSerializer);

        return factory;
    }

    /**
     * @param producerFactory factory de producers
     * @param observationRegistry registro de Micrometer para correlación
     * @param kafkaProperties propiedades {@code spring.kafka.template.*}
     * @return template con observación y timeout de cierre configurados
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(
        ProducerFactory<String, Object> producerFactory,
        ObservationRegistry observationRegistry,
        KafkaProperties kafkaProperties
    ) {
        var template = new KafkaTemplate<>(producerFactory);
        template.setObservationEnabled(kafkaProperties.getTemplate().isObservationEnabled());
        template.setObservationRegistry(observationRegistry);
        template.setCloseTimeout(kafkaProperties.getTemplate().getCloseTimeout());
        return template;
    }

    /**
     * @return topic de búsquedas con una partición y una réplica
     */
    @Bean
    public NewTopic searchesTopic() {
        return TopicBuilder.name(searchesTopicName)
            .partitions(1)
            .replicas(1)
            .build();
    }

    /**
     * @return topic DLT con una partición y una réplica
     */
    @Bean
    public NewTopic dltTopic() {
        return TopicBuilder.name(dltTopicName)
            .partitions(1)
            .replicas(1)
            .build();
    }
}
