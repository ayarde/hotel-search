package com.hotelsearch.infrastructure.input.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.awaitility.Awaitility.await;

import com.hotelsearch.domain.model.HotelSearchEvent;
import com.zaxxer.hikari.HikariDataSource;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.oracle.OracleContainer;

/**
 * Integration tests del consumer Kafka contra Kafka y Oracle reales.
 *
 * <p>Verifica el circuito completo de consumo, commit de Oracle,
 * confirmación de offsets, tratamiento de duplicados y publicación en el
 * DLT.</p>
 *
 * Condiciones del entorno:
 * - Kafka 4.4.0 en modo KRaft con una partición por topic
 * - Oracle Database Free 23.26.3 con Flyway y la migración V1
 * - El consumer usa el grupo estable hotel-search-consumer
 */
@Testcontainers
@SpringBootTest(properties = {
    "spring.datasource.hikari.connection-timeout=2000",
    "spring.datasource.hikari.validation-timeout=1000"
})
class HotelSearchKafkaConsumerIT {

    private static final String MAIN_TOPIC = "hotel_availability_searches";
    private static final String DLT_TOPIC = "hotel_search_dlt";
    private static final String CONSUMER_GROUP = "hotel-search-consumer";
    private static final String HOTEL_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String BLOCKED_HOTEL_ID = "11111111-1111-4111-8111-111111111111";
    private static final String TEST_CONSTRAINT = "ck_phase4_blocked_hotel";

    @Container
    private static final KafkaContainer KAFKA =
        new KafkaContainer("apache/kafka:4.4.0")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    @Container
    @ServiceConnection
    private static final OracleContainer ORACLE =
        new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart");

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private ConcurrentKafkaListenerContainerFactory<String, HotelSearchEvent> listenerFactory;

    @Autowired
    private HikariDataSource dataSource;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    /**
     * Limpia la tabla antes de cada escenario para que las aserciones
     * dependan únicamente del mensaje enviado por el test.
     */
    @BeforeEach
    void cleanTable() {
        jdbcTemplate.update("DELETE FROM hotel_search");
    }

    /**
     * Verifica que un evento válido se persista en Oracle y que su offset se
     * confirme después del commit de la base.
     *
     * Condiciones:
     * - Kafka y Oracle están disponibles
     * - El evento cumple las reglas de estructura
     *
     * Se espera:
     * - Exista una fila con el searchId publicado
     * - El grupo haya confirmado el offset recibido
     */
    @Test
    void consume_ValidEvent_PersistsSearchAndCommitsOffset() throws Exception {
        // given
        SearchId searchId = SearchId.generate();
        HotelSearchEvent event = eventOf(searchId, HOTEL_ID,
            LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 29));

        // when
        long offset = publish(event);

        // then
        assertAll(
            () -> await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(rowCount(searchId)).as("El evento debe persistirse")
                    .isEqualTo(1)),
            () -> await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(committedOffset()).as("El offset debe confirmarse tras el commit")
                    .hasValueSatisfying(value -> assertThat(value.offset())
                        .isGreaterThanOrEqualTo(offset + 1))));
    }

    /**
     * Verifica que la redelivery del mismo searchId no cree una segunda fila.
     *
     * Condiciones:
     * - El mismo evento se publica dos veces
     *
     * Se espera:
     * - Oracle contenga una sola fila para el searchId
     * - El consumer procese ambos registros y confirme el segundo offset
     */
    @Test
    void consume_DuplicateEvent_PersistsSingleRowAndCommitsOffset() throws Exception {
        // given
        SearchId searchId = SearchId.generate();
        HotelSearchEvent event = eventOf(searchId, HOTEL_ID,
            LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 29));

        // when
        long firstOffset = publish(event);
        long secondOffset = publish(event);

        // then
        assertAll(
            () -> assertThat(secondOffset).as("La publicación debe avanzar el offset del topic")
                .isGreaterThan(firstOffset),
            () -> await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(rowCount(searchId)).as("El duplicado no debe crear otra fila")
                    .isEqualTo(1)),
            () -> await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(committedOffset()).as("El segundo offset debe confirmarse")
                    .hasValueSatisfying(value -> assertThat(value.offset())
                        .isGreaterThanOrEqualTo(secondOffset + 1))));
    }

    /**
     * Verifica que un JSON inválido vaya directamente al DLT.
     *
     * Condiciones:
     * - El valor publicado no puede deserializarse como evento
     *
     * Se espera:
     * - El mensaje aparezca en hotel_search_dlt
     * - El intento de entrega registrado sea 1
     */
    @Test
    void consume_MalformedJson_PublishesDirectlyToDlt() throws Exception {
        // given
        String searchId = SearchId.generate().toString();

        // when
        publishRaw(searchId, "{esto-no-es-json".getBytes(StandardCharsets.UTF_8));

        // then
        ConsumerRecord<String, byte[]> recovered = awaitDltRecord(searchId);
        assertThat(deliveryAttempt(recovered)).as("El error permanente no debe reintentarse")
            .isEqualTo(1);
    }

    /**
     * Verifica que una violación de constraint en Oracle revierta la
     * transacción y sea enviada directamente al DLT.
     *
     * Condiciones:
     * - La tabla tiene una constraint temporal para un hotel de prueba
     * - El evento es estructuralmente válido pero viola esa constraint
     *
     * Se espera:
     * - No exista ninguna fila para el searchId
     * - El mensaje aparezca en el DLT después del primer intento
     */
    @Test
    void consume_DatabaseConstraintViolation_RollsBackAndPublishesDirectlyToDlt() throws Exception {
        // given
        jdbcTemplate.execute("ALTER TABLE hotel_search ADD CONSTRAINT " + TEST_CONSTRAINT
            + " CHECK (hotel_id <> '" + BLOCKED_HOTEL_ID + "')");
        SearchId searchId = SearchId.generate();
        HotelSearchEvent event = eventOf(searchId, BLOCKED_HOTEL_ID,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));

        try {
            // when
            publish(event);

            // then
            ConsumerRecord<String, byte[]> recovered = awaitDltRecord(searchId.toString());
            assertThat(rowCount(searchId)).as("La transacción debe revertirse")
                .isZero();
            assertThat(deliveryAttempt(recovered)).as("Una violación SQL es permanente")
                .isEqualTo(1);
        } finally {
            jdbcTemplate.execute("ALTER TABLE hotel_search DROP CONSTRAINT " + TEST_CONSTRAINT);
        }
    }

    /**
     * Verifica que un error transitorio de Oracle se reintente tres veces y
     * luego se publique en el DLT.
     *
     * Condiciones:
     * - Oracle se detiene antes de publicar el evento
     * - Kafka permanece disponible para recibir el mensaje y publicar el DLT
     *
     * Se espera:
     * - El mensaje aparezca en el DLT en el cuarto intento total
     * - No exista ninguna fila para el searchId
     */
    @Test
    void consume_TransientOracleFailure_RetriesThreeTimesThenPublishesToDlt() throws Exception {
        // given
        SearchId searchId = SearchId.generate();
        HotelSearchEvent event = eventOf(searchId, HOTEL_ID,
            LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 29));
        dataSource.getHikariPoolMXBean().softEvictConnections();
        ORACLE.getDockerClient().pauseContainerCmd(ORACLE.getContainerId()).exec();

        ConsumerRecord<String, byte[]> recovered;
        try {
            // when
            publish(event);

            // then
            recovered = awaitDltRecord(searchId.toString());
        } finally {
            ORACLE.getDockerClient().unpauseContainerCmd(ORACLE.getContainerId()).exec();
            dataSource.getHikariPoolMXBean().softEvictConnections();
        }

        assertThat(deliveryAttempt(recovered)).as("Debe haber un intento inicial y tres reintentos")
            .isEqualTo(4);
        await().atMost(Duration.ofSeconds(15)).ignoreExceptions().untilAsserted(() ->
            assertThat(rowCount(searchId)).as("El evento no debe persistirse")
                .isZero());
    }

    /**
     * Verifica que el listener container tenga habilitadas las observations
     * de Micrometer para propagar el contexto de tracing.
     *
     * Condiciones:
     * - El contexto Kafka está inicializado
     *
     * Se espera:
     * - El container tenga observationEnabled activo
     */
    @Test
    void consume_ListenerConfigured_EnablesObservations() {
        // given / when
        var containerProperties = listenerFactory.getContainerProperties();

        // then
        assertThat(containerProperties.isObservationEnabled())
            .as("El listener debe crear observations")
            .isTrue();
    }

    /**
     * Verifica que los topics tengan la topología acordada y que no exista
     * un listener subscribed al DLT.
     *
     * Condiciones:
     * - El contexto Kafka está inicializado
     *
     * Se espera:
     * - Ambos topics tengan una partición y un réplica
     * - El único listener esté subscribed al topic principal
     */
    @Test
    void consume_TopologyAndListeners_MatchPhase4Contract() throws Exception {
        // given / when
        Map<String, TopicDescription> descriptions = describeTopics();

        // then
        // Cada grupo verifica primero el tamaño y después accede al primer
        // elemento, así que esas dos aserciones quedan en orden dentro del grupo.
        assertAll(
            () -> {
                assertThat(descriptions.get(MAIN_TOPIC).partitions())
                    .as("El topic principal debe tener una partición")
                    .hasSize(1);
                assertThat(descriptions.get(MAIN_TOPIC).partitions().getFirst().replicas())
                    .as("El topic principal debe tener una réplica")
                    .hasSize(1);
            },
            () -> {
                assertThat(descriptions.get(DLT_TOPIC).partitions()).as("El DLT debe tener una partición")
                    .hasSize(1);
                assertThat(descriptions.get(DLT_TOPIC).partitions().getFirst().replicas())
                    .as("El DLT debe tener una réplica")
                    .hasSize(1);
            },
            () -> {
                assertThat(listenerRegistry.getListenerContainers()).as("Debe existir un único listener")
                    .hasSize(1);
                assertThat(listenerRegistry.getListenerContainers().iterator().next()
                    .getContainerProperties().getTopics())
                    .contains(MAIN_TOPIC)
                    .doesNotContain(DLT_TOPIC);
            });
    }

    // <editor-fold desc="Helpers">
    private HotelSearchEvent eventOf(
        SearchId searchId,
        String hotelId,
        LocalDate checkIn,
        LocalDate checkOut
    ) {
        return new HotelSearchEvent(
            searchId,
            HotelId.of(hotelId),
            CheckIn.of(checkIn),
            CheckOut.of(checkOut),
            Ages.of(List.of(29, 28, 4, 3)));
    }

    private long publish(HotelSearchEvent event) throws InterruptedException, ExecutionException, TimeoutException {
        var result = kafkaTemplate.send(
            MAIN_TOPIC,
            event.searchId().toString(),
            event).get(10, TimeUnit.SECONDS);
        return result.getRecordMetadata().offset();
    }

    private long publishRaw(String key, byte[] value)
        throws InterruptedException, ExecutionException, TimeoutException {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        try (var producer = new KafkaProducer<String, byte[]>(properties)) {
            return producer.send(new ProducerRecord<>(MAIN_TOPIC, key, value))
                .get(10, TimeUnit.SECONDS)
                .offset();
        }
    }

    private ConsumerRecord<String, byte[]> awaitDltRecord(String expectedKey) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-verifier-" + System.nanoTime());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        try (var consumer = new KafkaConsumer<String, byte[]>(properties)) {
            consumer.subscribe(List.of(DLT_TOPIC));
            long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
            while (System.nanoTime() < deadline) {
                var records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, byte[]> consumerRecord : records) {
                    if (expectedKey.equals(consumerRecord.key())) {
                        return consumerRecord;
                    }
                }
            }
        }
        throw new AssertionError("No se recibió el mensaje " + expectedKey + " en el DLT");
    }

    private int deliveryAttempt(ConsumerRecord<String, byte[]> consumerRecord) {
        var header = consumerRecord.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT);
        assertThat(header).as("El registro debe incluir el contador de intentos")
            .isNotNull();
        return ByteBuffer.wrap(header.value()).getInt();
    }

    private Optional<OffsetAndMetadata> committedOffset() throws Exception {
        try (AdminClient admin = adminClient()) {
            Map<TopicPartition, OffsetAndMetadata> offsets = admin
                .listConsumerGroupOffsets(CONSUMER_GROUP)
                .partitionsToOffsetAndMetadata()
                .get(10, TimeUnit.SECONDS);
            return Optional.ofNullable(offsets.get(new TopicPartition(MAIN_TOPIC, 0)));
        }
    }

    private Map<String, TopicDescription> describeTopics() throws Exception {
        try (AdminClient admin = adminClient()) {
            return admin.describeTopics(List.of(MAIN_TOPIC, DLT_TOPIC))
                .allTopicNames()
                .get(10, TimeUnit.SECONDS);
        }
    }

    private AdminClient adminClient() {
        return AdminClient.create(Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()));
    }

    private int rowCount(SearchId searchId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM hotel_search WHERE search_id = ?",
            Integer.class,
            searchId.toString());
        return count == null ? 0 : count;
    }

    // </editor-fold>
}
