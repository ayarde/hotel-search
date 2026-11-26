package com.hotelsearch.infrastructure.output.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hotelsearch.infrastructure.observability.CorrelationContext;
import com.hotelsearch.infrastructure.observability.MdcScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.oracle.OracleContainer;

import com.hotelsearch.domain.model.HotelSearchEvent;
import com.hotelsearch.application.port.HotelSearchPublisher;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;

/**
 * Integration tests del publisher Kafka contra un broker real (Testcontainers).
 *
 * <p>Verifica que el evento se publica con el searchId como key, el
 * payload JSON es correcto y se recibe el ACK del broker.</p>
 *
 * Condiciones del entorno:
 * - Imagen Kafka pinneada a 4.4.0 (KRaft), compatible con la versión
 *   de Spring Kafka gestionada por Boot 4.1.2.
 * - El módulo oficial kafka incluye su propia wait strategy y
 *   @ServiceConnection conecta el bootstrap-servers al template.
 * - El estado se limpia porque cada test usa un topic dedicado
 *   (creado por KafkaProducerConfig al arrancar el contexto).
 */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class KafkaHotelSearchPublisherIT {

    private static final Logger log = LoggerFactory.getLogger(KafkaHotelSearchPublisherIT.class);

    @Container
    private static final KafkaContainer kafka =
        new KafkaContainer("apache/kafka:4.4.0")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    @Container
    @ServiceConnection
    private static final OracleContainer oracle =
        new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private HotelSearchPublisher publisher;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private Tracer tracer;

    /**
     * Verifica que un evento válido se publica y recibe ACK del broker.
     *
     * Condiciones:
     * - Evento con searchId, hotelId, fechas y edades válidas
     *
     * Se espera:
     * - El send() completa sin excepción (ACK recibido)
     * - El topic contiene el mensaje con key = searchId
     * - El payload JSON incluye todos los campos del evento
     */
    @Test
    void publish_ValidEvent_ReceivesAckAndPayloadIsCorrect() throws ExecutionException, InterruptedException {
        // given
        SearchId searchId = SearchId.generate();
        HotelSearchEvent event = eventOf(searchId);

        // when
        publisher.publish(event);

        // then - consumir con consumer directo para verificar payload
        var receivedEvent = consumeEvent(searchId);
        assertThat(receivedEvent).as("Debe recibir el mensaje publicado").isNotNull();
        assertAll(
            () -> assertThat(receivedEvent.searchId()).as("Key/searchId debe coincidir").isEqualTo(searchId),
            () -> assertThat(receivedEvent.hotelId()).as("HotelId debe coincidir").isEqualTo(event.hotelId()),
            () -> assertThat(receivedEvent.checkIn()).as("CheckIn debe coincidir").isEqualTo(event.checkIn()),
            () -> assertThat(receivedEvent.checkOut()).as("CheckOut debe coincidir")
                .isEqualTo(event.checkOut()),
            () -> assertThat(receivedEvent.ages().values()).as("Edades deben coincidir en orden")
                .containsExactly(29, 28, 4, 3));
    }

    /**
     * Verifica que el mensaje se publica con key = searchId (no nula).
     *
     * Condiciones:
     * - Evento publicado
     *
     * Se espera:
     * - La key del mensaje Kafka es el searchId en string
     */
    @Test
    void publish_MessageKey_IsSearchId() throws ExecutionException, InterruptedException {
        // given
        SearchId searchId = SearchId.generate();
        HotelSearchEvent event = eventOf(searchId);

        // when
        publisher.publish(event);

        // then - consumir el record crudo para inspeccionar la key
        var record = consumeRecord();
        assertThat(record).as("Debe recibir el record").isNotNull();
        assertThat(record.key()).as("Key debe ser el searchId").isEqualTo(searchId.value().toString());
    }

    /**
     * Verifica que el producer propague el contexto de tracing y el
     * searchId dentro de los headers del mensaje.
     *
     * Condiciones:
     * - Existe un span padre activo durante la publicación
     * - Kafka recibe el evento publicado
     *
     * Se espera:
     * - El mensaje incluya el header searchId
     * - El mensaje incluya el contexto W3C de tracing
     */
    @Test
    void publish_WithinTrace_PropagatesSearchIdAndTraceHeaders() throws Exception {
        // given
        SearchId searchId = SearchId.generate();
        HotelSearchEvent event = eventOf(searchId);
        Span span = tracer.nextSpan().name("phase6-publish").start();

        // when
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            publisher.publish(event);
        } finally {
            span.end();
        }

        // then
        ConsumerRecord<String, Object> record = consumeRecord(searchId.toString());
        var searchIdHeader = record.headers().lastHeader(CorrelationContext.SEARCH_ID);
        assertThat(searchIdHeader).as("El searchId debe viajar en un header")
            .isNotNull();
        assertAll(
            () -> assertThat(new String(searchIdHeader.value(), StandardCharsets.UTF_8))
                .isEqualTo(searchId.toString()),
            () -> assertThat(record.headers().lastHeader("traceparent"))
                .as("El contexto de tracing debe propagarse a Kafka")
                .isNotNull());
    }

    /**
     * Verifica que el logging estructurado incluya los datos de correlación
     * sin exponer el payload de la búsqueda.
     *
     * Condiciones:
     * - Existe un traceId y un spanId activos
     * - El MDC contiene component y searchId
     *
     * Se espera:
     * - El log sea un objeto JSON
     * - Include searchId, traceId, spanId y component
     * - No incluya la lista de edades
     */
    @Test
    void logging_CorrelationScope_WritesJsonWithCorrelationFields(CapturedOutput output) throws Exception {
        // given
        String marker = "phase6-structured-log";
        String searchId = SearchId.generate().toString();
        Span span = tracer.nextSpan().name("phase6-logging").start();

        // when
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            try (MdcScope scope = MdcScope.search(
                CorrelationContext.KAFKA_PRODUCER_COMPONENT, searchId)) {
                log.info(marker);
            }
        } finally {
            span.end();
        }

        // then
        String jsonLine = output.getAll().lines()
            .filter(line -> line.contains(marker))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No se capturó el log estructurado esperado"));
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var json = mapper.readValue(jsonLine, tools.jackson.databind.JsonNode.class);
        assertAll(
            () -> assertThat(json.get("timestamp").asString()).isNotBlank(),
            () -> assertThat(json.get("level").asString()).isEqualTo("INFO"),
            () -> assertThat(json.get("logger").asString())
                .isEqualTo(KafkaHotelSearchPublisherIT.class.getName()),
            () -> assertThat(json.get("searchId").asString()).isEqualTo(searchId),
            () -> assertThat(json.get("component").asString())
                .isEqualTo(CorrelationContext.KAFKA_PRODUCER_COMPONENT),
            () -> assertThat(json.get("traceId").asString()).isNotBlank(),
            () -> assertThat(json.get("spanId").asString()).isNotBlank(),
            () -> assertThat(json.has("ages"))
                .as("El log no debe contener el payload de la búsqueda")
                .isFalse(),
            () -> assertThat(org.slf4j.MDC.getCopyOfContextMap()).isNullOrEmpty());
    }

    /**
     * Verifica que publicar un evento nulo lanza excepción.
     *
     * Condiciones:
     * - Se intenta publicar null
     *
     * Se espera:
     * - Se lanza NullPointerException o IllegalArgumentException
     */
    @Test
    void publish_NullEvent_ThrowsException() {
        // when / then
        assertThrows(NullPointerException.class, () -> publisher.publish(null),
            "Publicar evento nulo debe fallar");
    }

    // <editor-fold desc="Helpers">
    private HotelSearchEvent eventOf(SearchId searchId) {
        return new HotelSearchEvent(
            searchId,
            HotelId.of("550e8400-e29b-41d4-a716-446655440000"),
            CheckIn.of(LocalDate.of(2026, 11, 10)),
            CheckOut.of(LocalDate.of(2026, 11, 29)),
            Ages.of(List.of(29, 28, 4, 3))
        );
    }

    private HotelSearchEvent consumeEvent(SearchId expectedSearchId) {
        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of("hotel_availability_searches"));
            var records = consumer.poll(Duration.ofSeconds(10));
            for (var record : records.records("hotel_availability_searches")) {
                var bytes = (byte[]) record.value();
                try {
                    var mapper = JsonMapper.builder().findAndAddModules().build();
                    var event = mapper.readValue(bytes, HotelSearchEvent.class);
                    if (event.searchId().equals(expectedSearchId)) {
                        return event;
                    }
                } catch (tools.jackson.core.JacksonException e) {
                    throw new IllegalStateException("Error deserializando evento", e);
                }
            }
            throw new IllegalStateException("No se recibió mensaje con searchId " + expectedSearchId);
        }
    }

    private ConsumerRecord<String, Object> consumeRecord() {
        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of("hotel_availability_searches"));
            var records = consumer.poll(Duration.ofSeconds(10));
            for (var record : records.records("hotel_availability_searches")) {
                return record;
            }
            throw new IllegalStateException("No se recibió mensaje en el topic");
        }
    }

    private ConsumerRecord<String, Object> consumeRecord(String expectedSearchId) {
        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of("hotel_availability_searches"));
            var records = consumer.poll(Duration.ofSeconds(10));
            for (var record : records.records("hotel_availability_searches")) {
                if (expectedSearchId.equals(record.key())) {
                    return record;
                }
            }
            throw new IllegalStateException(
                "No se recibió mensaje con searchId " + expectedSearchId);
        }
    }

    private KafkaConsumer<String, Object> createConsumer() {
        var props = Map.<String, Object>of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.ByteArrayDeserializer.class
        );
        return new KafkaConsumer<>(props);
    }
    // </editor-fold>
}
