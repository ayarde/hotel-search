package com.hotelsearch.infrastructure.config;

import static com.hotelsearch.domain.model.HotelSearchEventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hotelsearch.domain.model.HotelSearchEvent;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Context tests de la configuración del producer Kafka y de los topics.
 *
 * <p>Levantan solo {@link KafkaProducerConfig} con sus colaboradores. Ningún
 * bean abre conexiones contra el broker al crearse, así que no hace falta
 * Kafka real.</p>
 */
class KafkaProducerConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(KafkaPropertiesBinding.class, KafkaProducerConfig.class)
        .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
        .withBean(ObservationRegistry.class, ObservationRegistry::create)
        .withPropertyValues(
            "app.kafka.topics.hotel-availability-searches=hotel_availability_searches",
            "app.kafka.topics.hotel-search-dlt=hotel_search_dlt");

    /**
     * Verifica que, sin propiedades externas, el producer use los valores por
     * defecto que garantizan durabilidad y un timeout acotado.
     *
     * Condiciones:
     * - No se definen propiedades spring.kafka.*; KafkaProperties se enlaza
     *   desde el entorno vacío
     *
     * Se espera:
     * - Broker localhost:9092, acks=all e idempotencia habilitada
     * - delivery timeout de 10000 ms y request timeout de 3000 ms
     * - Key serializada como texto y value con JacksonJsonSerializer
     */
    @Test
    void producerFactory_NoExternalProperties_UsesDurableDefaults() {
        // given
        contextRunner
            // when
            .run(context -> {
                // then
                assertThat(context.getStartupFailure()).isNull();
                DefaultKafkaProducerFactory<?, ?> factory = context.getBean(DefaultKafkaProducerFactory.class);
                assertAll(
                    () -> assertThat(factory.getConfigurationProperties())
                        .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
                        .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                        .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
                        .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "10000")
                        .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "3000")
                        .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class),
                    () -> assertThat(factory.getValueSerializer()).isInstanceOf(JacksonJsonSerializer.class));
            });
    }

    /**
     * Verifica que las propiedades spring.kafka.* del entorno reemplacen los
     * valores por defecto del producer.
     *
     * Condiciones:
     * - Se definen bootstrap servers, acks, idempotencia y timeouts
     *
     * Se espera:
     * - La configuración del producer refleje cada valor externo
     */
    @Test
    void producerFactory_ExternalProperties_OverrideDefaults() {
        // given
        contextRunner
            .withPropertyValues(
                "spring.kafka.bootstrap-servers=kafka:29092",
                "spring.kafka.producer.acks=1",
                "spring.kafka.producer.enable-idempotence=false",
                "spring.kafka.producer.delivery-timeout-ms=4321",
                "spring.kafka.producer.request-timeout-ms=2345")
            // when
            .run(context -> {
                // then
                assertThat(context.getStartupFailure()).isNull();
                DefaultKafkaProducerFactory<?, ?> factory = context.getBean(DefaultKafkaProducerFactory.class);
                assertThat(factory.getConfigurationProperties())
                    .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:29092")
                    .containsEntry(ProducerConfig.ACKS_CONFIG, "1")
                    .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "false")
                    .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "4321")
                    .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "2345");
            });
    }

    /**
     * Verifica que el value se serialice como JSON plano, sin headers de tipo,
     * para que el consumer no dependa de nombres de clases del producer.
     *
     * Condiciones:
     * - Se serializa un evento válido con headers vacíos
     *
     * Se espera:
     * - No se agregue ningún header
     * - El JSON se pueda leer como HotelSearchEvent igual al original
     */
    @Test
    void producerFactory_ValueSerializer_WritesPlainJsonWithoutTypeHeaders() {
        // given
        HotelSearchEvent event = validEvent();
        var headers = new RecordHeaders();
        contextRunner
            // when
            .run(context -> {
                ProducerFactory<String, Object> factory = context.<ProducerFactory<String, Object>>getBeanProvider(
                    ResolvableType.forClassWithGenerics(ProducerFactory.class, String.class, Object.class))
                .getObject();
                byte[] payload = factory.getValueSerializer()
                    .serialize("hotel_availability_searches", headers, event);

                // then
                assertThat(headers.toArray()).as("No debe propagarse información de tipo")
                    .isEmpty();
                assertThat(JsonMapper.builder().build().readValue(payload, HotelSearchEvent.class))
                    .isEqualTo(event);
            });
    }

    /**
     * Verifica que el KafkaTemplate declarado en el contexto use el producer
     * factory de la aplicación.
     *
     * Condiciones:
     * - El contexto crea el producer factory y el template
     *
     * Se espera:
     * - El template use el mismo producer factory del contexto
     */
    @Test
    void kafkaTemplate_Context_UsesApplicationProducerFactory() {
        // given
        contextRunner
            // when
            .run(context -> {
                // then
                assertThat(context.getStartupFailure()).isNull();
                KafkaTemplate<?, ?> template = context.getBean(KafkaTemplate.class);
                assertThat(template.getProducerFactory())
                    .isSameAs(context.getBean(DefaultKafkaProducerFactory.class));
            });
    }

    /**
     * Verifica que el KafkaTemplate aplique las propiedades
     * spring.kafka.template.* enlazadas desde el entorno: cada envío crea una
     * observation y el producer se cierra con el timeout configurado.
     *
     * Condiciones:
     * - spring.kafka.template.observation-enabled=true
     * - spring.kafka.template.close-timeout=7s
     * - El producer es un mock y el registro de observations las registra
     *
     * Se espera:
     * - El envío inicie la observation spring.kafka.template
     * - El producer se cierre con un timeout de 7 segundos
     */
    @Test
    void kafkaTemplate_TemplateProperties_ObservesSendAndClosesWithTimeout() {
        // given
        Producer<String, Object> producer = mock();
        ProducerFactory<String, Object> producerFactory = mock();
        when(producerFactory.createProducer()).thenReturn(producer);
        var metadata = new RecordMetadata(
            new TopicPartition("hotel_availability_searches", 0), 0L, 0, 0L, 0, 0);
        when(producer.send(any(), any())).thenAnswer(invocation -> {
            Callback callback = invocation.getArgument(1);
            callback.onCompletion(metadata, null);
            return CompletableFuture.completedFuture(metadata);
        });
        List<String> observations = new CopyOnWriteArrayList<>();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(recordingHandler(observations));
        contextRunner
            .withPropertyValues(
                "spring.kafka.template.observation-enabled=true",
                "spring.kafka.template.close-timeout=7s")
            .run(context -> {
                // Se instancia la configuración sin el proxy CGLIB del contexto,
                // que devolvería el KafkaTemplate singleton e ignoraría los mocks.
                KafkaTemplate<String, Object> template = new KafkaProducerConfig(context.getEnvironment())
                    .kafkaTemplate(producerFactory, observationRegistry, context.getBean(KafkaProperties.class));

                // when
                template.send(new ProducerRecord<>("hotel_availability_searches", "key", "value"));

                // then
                assertThat(observations).as("Cada envío debe crear una observation para la correlación")
                    .containsExactly("spring.kafka.template");
                verify(producer).send(any(), any());
                verify(producer).close(Duration.ofSeconds(7));
            });
    }

    /**
     * Verifica que los topics principal y DLT se declaren con los nombres
     * configurados, una partición y replication factor 1.
     *
     * Condiciones:
     * - app.kafka.topics define ambos nombres
     *
     * Se espera:
     * - Dos NewTopic con el nombre configurado, 1 partición y 1 réplica
     */
    @Test
    void topics_ConfiguredNames_DeclareSinglePartitionTopics() {
        // given
        contextRunner
            // when
            .run(context -> {
                // then
                NewTopic searches = context.getBean("searchesTopic", NewTopic.class);
                NewTopic dlt = context.getBean("dltTopic", NewTopic.class);
                assertThat(List.of(searches, dlt))
                    .extracting(NewTopic::name, NewTopic::numPartitions, NewTopic::replicationFactor)
                    .containsExactly(
                        tuple("hotel_availability_searches", 1, (short) 1),
                        tuple("hotel_search_dlt", 1, (short) 1));
            });
    }

    // <editor-fold desc="Helpers">
    private static ObservationHandler<Observation.Context> recordingHandler(List<String> names) {
        return new ObservationHandler<>() {
            @Override
            public void onStart(Observation.Context context) {
                names.add(context.getName());
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        };
    }

    /**
     * Enlaza {@link KafkaProperties} desde el entorno del test, igual que lo
     * hace Spring Boot en la aplicación.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(KafkaProperties.class)
    static class KafkaPropertiesBinding {
    }

    // </editor-fold>
}
