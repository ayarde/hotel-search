package com.hotelsearch.infrastructure.config;

import com.hotelsearch.application.PersistHotelSearch;
import com.hotelsearch.domain.model.HotelSearchEvent;
import com.hotelsearch.infrastructure.input.kafka.HotelSearchEventMapper;
import com.hotelsearch.infrastructure.input.kafka.HotelSearchKafkaListener;
import com.hotelsearch.infrastructure.input.kafka.InvalidHotelSearchEventException;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuración del consumer Kafka, la política de reintentos y el DLT.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final long RETRY_INTERVAL_MILLIS = 1_000L;
    private static final long MAX_ADDITIONAL_RETRIES = 3L;

    /**
     * Crea el consumer factory con deserialización JSON tipada. El
     * deserializador tolerante a errores permite que un JSON inválido sea
     * recuperado por el error handler en lugar de detener el consumer.
     *
     * @param properties propiedades Kafka gestionadas por Spring Boot
     * @param jsonMapper mapper JSON de la aplicación
     * @return factory de consumers para eventos de búsqueda
     */
    @Bean
    public ConsumerFactory<String, HotelSearchEvent> hotelSearchConsumerFactory(
        KafkaProperties properties,
        JsonMapper jsonMapper
    ) {
        Map<String, Object> configs = properties.buildConsumerProperties();
        var factory = new DefaultKafkaConsumerFactory<String, HotelSearchEvent>(configs);
        factory.setKeyDeserializer(new StringDeserializer());
        factory.setValueDeserializer(new ErrorHandlingDeserializer<>(
            new JacksonJsonDeserializer<>(HotelSearchEvent.class, jsonMapper, false)));
        return factory;
    }

    /**
     * Crea la fábrica del listener con confirmación por registro. El offset
     * se confirma después de que el listener retorne, y el servicio de
     * persistencia ya haya completado el commit de Oracle.
     *
     * @param consumerFactory consumer factory tipado
     * @param errorHandler política de reintentos y recuperación
     * @param observationRegistry registro de observations de Micrometer
     * @param kafkaProperties propiedades Kafka gestionadas por Spring Boot
     * @return fábrica del listener Kafka
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, HotelSearchEvent> kafkaListenerContainerFactory(
        ConsumerFactory<String, HotelSearchEvent> consumerFactory,
        DefaultErrorHandler errorHandler,
        ObservationRegistry observationRegistry,
        KafkaProperties kafkaProperties
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, HotelSearchEvent>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.getContainerProperties().setObservationEnabled(
            kafkaProperties.getListener().isObservationEnabled());
        factory.getContainerProperties().setObservationRegistry(observationRegistry);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    /**
     * Crea la política de errores del consumer. Los errores permanentes van
     * directamente al DLT; los transitorios se reintentan tres veces con un
     * segundo de intervalo.
     *
     * @param kafkaTemplate template usado para publicar en el DLT
     * @param dltTopic topic de mensajes no procesables
     * @return error handler del consumer
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
        KafkaTemplate<String, Object> kafkaTemplate,
        @Value("${app.kafka.topics.hotel-search-dlt}") String dltTopic
    ) {
        var recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (consumerRecord, _) -> new TopicPartition(dltTopic, consumerRecord.partition()));
        recoverer.setFailIfSendResultIsError(true);
        // Ambos topics tienen una sola partición; la topología ya está
        // garantizada por la configuración de topics de la aplicación.
        recoverer.setVerifyPartition(false);

        var errorHandler = new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(RETRY_INTERVAL_MILLIS, MAX_ADDITIONAL_RETRIES));
        errorHandler.setAckAfterHandle(true);
        errorHandler.addNotRetryableExceptions(
            InvalidHotelSearchEventException.class,
            MessageConversionException.class,
            DeserializationException.class,
            DataIntegrityViolationException.class,
            BadSqlGrammarException.class,
            InvalidDataAccessApiUsageException.class);
        return errorHandler;
    }

    /**
     * Crea el mapper de mensajes Kafka.
     *
     * @return mapper del evento
     */
    @Bean
    public HotelSearchEventMapper hotelSearchEventMapper() {
        return new HotelSearchEventMapper();
    }

    /**
     * Crea el listener que procesa los eventos del topic principal.
     *
     * @param mapper mapper del evento
     * @param persistHotelSearch servicio transaccional de persistencia
     * @return listener Kafka
     */
    @Bean
    public HotelSearchKafkaListener hotelSearchKafkaListener(
        HotelSearchEventMapper mapper,
        PersistHotelSearch persistHotelSearch
    ) {
        return new HotelSearchKafkaListener(mapper, persistHotelSearch);
    }
}
