package com.hotelsearch.infrastructure.config;

import static com.hotelsearch.domain.model.HotelSearchEventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hotelsearch.application.PersistHotelSearch;
import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.model.HotelSearchEvent;
import com.hotelsearch.infrastructure.input.kafka.InvalidHotelSearchEventException;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.messaging.converter.MessageConversionException;
import tools.jackson.databind.json.JsonMapper;

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
        ConsumerFactory<String, HotelSearchEvent> consumerFactory = mock();

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
        ConsumerFactory<String, HotelSearchEvent> consumerFactory = mock();
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
        KafkaTemplate<String, Object> template = mock();
        when(template.send(anyProducerRecord()))
            .thenReturn(CompletableFuture.completedFuture(null));
        DefaultErrorHandler handler = config.kafkaErrorHandler(template, DLT_TOPIC);
        ConsumerRecord<String, Object> consumerRecord = record();

        // when
        handler.handleOne(
            new InvalidHotelSearchEventException("evento inválido"),
            consumerRecord,
            mock(Consumer.class),
            mock(MessageListenerContainer.class));

        // then
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.captor();
        verify(template).send(captor.capture());
        assertThat(captor.getValue().topic()).as("El mensaje debe ir al DLT configurado")
            .isEqualTo(DLT_TOPIC);
        assertThat(captor.getValue().key()).as("La key original debe conservarse")
            .isEqualTo(consumerRecord.key());
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
        KafkaTemplate<String, Object> template = mock();
        when(template.send(anyProducerRecord()))
            .thenReturn(CompletableFuture.completedFuture(null));
        DefaultErrorHandler handler = config.kafkaErrorHandler(template, DLT_TOPIC);
        ConsumerRecord<String, Object> consumerRecord = record();

        // when
        for (int attempt = 0; attempt < 3; attempt++) {
            handler.handleOne(
                new DataAccessResourceFailureException("Oracle no disponible"),
                consumerRecord,
                mock(Consumer.class),
                mock(MessageListenerContainer.class));
        }
        verify(template, never()).send(anyProducerRecord());

        handler.handleOne(
            new DataAccessResourceFailureException("Oracle no disponible"),
            consumerRecord,
            mock(Consumer.class),
            mock(MessageListenerContainer.class));

        // then
        verify(template).send(anyProducerRecord());
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
        KafkaTemplate<String, Object> template = mock();
        CompletableFuture<SendResult<String, Object>> failedSend =
            new CompletableFuture<>();
        failedSend.completeExceptionally(new IllegalStateException("broker no disponible"));
        when(template.send(anyProducerRecord())).thenReturn(failedSend);
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
        verify(template).send(anyProducerRecord());
    }

    /**
     * Verifica que el consumer factory tome las propiedades de Spring Boot y
     * use deserializadores de texto para la key y JSON tolerante para el value.
     *
     * Condiciones:
     * - Las propiedades Kafka definen bootstrap servers y group id
     *
     * Se espera:
     * - La configuración del consumer conserve ambas propiedades
     * - La key use StringDeserializer
     * - El value use ErrorHandlingDeserializer
     */
    @Test
    void hotelSearchConsumerFactory_Configured_UsesStringKeyAndTolerantJsonValue() {
        // given
        var properties = new KafkaProperties();
        properties.setBootstrapServers(List.of("kafka:29092"));
        properties.getConsumer().setGroupId("hotel-search-consumer");

        // when
        var factory = (DefaultKafkaConsumerFactory<String, HotelSearchEvent>)
            config.hotelSearchConsumerFactory(properties, JsonMapper.builder().build());

        // then
        assertAll(
            () -> assertThat(factory.getConfigurationProperties())
                .as("El consumer debe usar las propiedades de Spring Boot")
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, List.of("kafka:29092"))
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "hotel-search-consumer"),
            () -> assertThat(factory.getKeyDeserializer()).isInstanceOf(StringDeserializer.class),
            () -> assertThat(factory.getValueDeserializer()).isInstanceOf(ErrorHandlingDeserializer.class));
    }

    /**
     * Verifica que el deserializador del value convierta un JSON válido en el
     * evento de búsqueda.
     *
     * Condiciones:
     * - El payload es el JSON de un evento válido
     *
     * Se espera:
     * - Se obtenga un evento igual al original
     */
    @Test
    void hotelSearchConsumerFactory_ValidJson_DeserializesEvent() {
        // given
        JsonMapper jsonMapper = JsonMapper.builder().build();
        HotelSearchEvent event = validEvent();
        byte[] payload = jsonMapper.writeValueAsBytes(event);
        // El deserializador pertenece al factory, que lo comparte con cada
        // consumer que crea; no lo cierra quien solo lo consulta.
        @SuppressWarnings("PMD.CloseResource")
        var deserializer = config.hotelSearchConsumerFactory(new KafkaProperties(), jsonMapper)
            .getValueDeserializer();

        // when
        HotelSearchEvent result = deserializer.deserialize(
            "hotel_availability_searches", new RecordHeaders(), payload);

        // then
        assertThat(result).isEqualTo(event);
    }

    /**
     * Verifica que un JSON inválido no detenga el consumer: el deserializador
     * devuelve null y deja el error en un header para el error handler.
     *
     * Condiciones:
     * - El payload no es JSON
     *
     * Se espera:
     * - El value deserializado sea null
     * - Los headers incluyan la excepción de deserialización del value
     */
    @Test
    void hotelSearchConsumerFactory_InvalidJson_ReportsErrorInHeader() {
        // given
        // Mismo motivo que en el test anterior: el deserializador es del factory.
        @SuppressWarnings("PMD.CloseResource")
        var deserializer = config.hotelSearchConsumerFactory(
                new KafkaProperties(), JsonMapper.builder().build())
            .getValueDeserializer();
        var headers = new RecordHeaders();

        // when
        HotelSearchEvent result = deserializer.deserialize(
            "hotel_availability_searches",
            headers,
            "no es json".getBytes(StandardCharsets.UTF_8));

        // then
        assertThat(result).as("Un JSON inválido no debe producir evento")
            .isNull();
        assertThat(headers.lastHeader(KafkaUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER))
            .as("El error debe quedar disponible para el error handler")
            .isNotNull();
    }

    /**
     * Verifica que cada tipo de error clasificado como permanente se envíe al
     * DLT en el primer intento, sin consumir los reintentos.
     *
     * Condiciones:
     * - El listener falla con uno de los errores no reintentables
     * - La publicación al DLT responde correctamente
     *
     * Se espera:
     * - El registro se recupere en el primer intento
     * - Se publique una sola vez en hotel_search_dlt
     */
    @ParameterizedTest
    @MethodSource("permanentErrors")
    void kafkaErrorHandler_NotRetryableError_RecoversOnFirstAttempt(RuntimeException error) {
        // given
        KafkaTemplate<String, Object> template = mock();
        when(template.send(anyProducerRecord()))
            .thenReturn(CompletableFuture.completedFuture(null));
        DefaultErrorHandler handler = config.kafkaErrorHandler(template, DLT_TOPIC);

        // when
        boolean handled = handler.handleOne(
            error,
            record(),
            mock(Consumer.class),
            mock(MessageListenerContainer.class));

        // then
        assertThat(handled).as("%s debe recuperarse sin reintentos", error.getClass().getSimpleName())
            .isTrue();
        verify(template).send(anyProducerRecord());
    }

    /**
     * Verifica que el listener creado por la configuración mapee el evento y
     * lo entregue al caso de uso de persistencia.
     *
     * Condiciones:
     * - El registro contiene un evento válido
     *
     * Se espera:
     * - PersistHotelSearch reciba una búsqueda con los mismos searchId,
     *   hotelId, fechas y edades que el evento
     */
    @Test
    void hotelSearchKafkaListener_ValidRecord_PersistsMappedSearch() {
        // given
        PersistHotelSearch persistHotelSearch = mock(PersistHotelSearch.class);
        var listener = config.hotelSearchKafkaListener(config.hotelSearchEventMapper(), persistHotelSearch);
        HotelSearchEvent event = validEvent();
        var consumerRecord = new ConsumerRecord<>(
            "hotel_availability_searches", 0, 12L, event.searchId().toString(), event);

        // when
        listener.consume(consumerRecord);

        // then
        ArgumentCaptor<HotelSearch> captor = ArgumentCaptor.forClass(HotelSearch.class);
        verify(persistHotelSearch).execute(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new HotelSearch(
            event.searchId(), event.hotelId(), event.checkIn(), event.checkOut(), event.ages()));
    }

    // <editor-fold desc="Helpers">
    private static Stream<RuntimeException> permanentErrors() {
        return Stream.of(
            new InvalidHotelSearchEventException("evento inválido"),
            new MessageConversionException("conversión fallida"),
            new DeserializationException("JSON inválido", new byte[0], false, null),
            new DataIntegrityViolationException("constraint violada"),
            new BadSqlGrammarException("insert", "insert into hotel_search", new SQLException("ORA-00942")),
            new InvalidDataAccessApiUsageException("uso inválido"));
    }


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

    // any(ProducerRecord.class) devuelve el tipo raw y genera un warning
    // unchecked; any() toma el tipo genérico del retorno.
    private static ProducerRecord<String, Object> anyProducerRecord() {
        return any();
    }
    // </editor-fold>
}
