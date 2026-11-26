package com.hotelsearch.infrastructure.output.kafka;

import static com.hotelsearch.domain.model.HotelSearchEventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hotelsearch.application.HotelSearchUnavailableException;
import com.hotelsearch.domain.model.HotelSearchEvent;
import com.hotelsearch.infrastructure.observability.CorrelationContext;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Tests de la traducción de fallos del producer Kafka al error de
 * disponibilidad de la aplicación.
 */
class KafkaHotelSearchPublisherTest {

    private static final String TOPIC = "hotel_availability_searches";

    /**
     * Limpia el flag de interrupción para que un test no contamine al
     * siguiente que corra en el mismo hilo.
     */
    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    /**
     * Verifica que, ante el ACK del broker, el publisher envíe el evento al
     * topic configurado con el searchId como key y retorne sin error.
     *
     * Condiciones:
     * - El broker confirma el envío con su metadata
     *
     * Se espera:
     * - Un único envío al topic hotel_availability_searches
     * - Key igual al searchId y value igual al evento original
     */
    @Test
    void publish_BrokerAcknowledges_SendsEventKeyedBySearchId() {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        doReturn(CompletableFuture.completedFuture(acknowledgedResult()))
            .when(template).send(any(ProducerRecord.class));
        KafkaHotelSearchPublisher publisher = new KafkaHotelSearchPublisher(template, TOPIC);
        HotelSearchEvent event = validEvent();

        // when
        publisher.publish(event);

        // then
        ArgumentCaptor<ProducerRecord<String, Object>> captor =
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        assertAll(
            () -> assertThat(captor.getValue().topic()).as("Debe publicarse en el topic configurado")
                .isEqualTo(TOPIC),
            () -> assertThat(captor.getValue().key()).as("La key debe ser el searchId")
                .isEqualTo(event.searchId().toString()),
            () -> assertThat(captor.getValue().value()).as("El value debe ser el evento sin transformar")
                .isSameAs(event));
    }

    /**
     * Verifica que el evento incluya el header de correlación y que el
     * publisher establezca el MDC durante el envío.
     *
     * Condiciones:
     * - El broker responde correctamente al envío
     *
     * Se espera:
     * - El ProducerRecord incluya el searchId como header
     * - El MDC identifique el componente kafka-producer y la búsqueda
     * - El MDC quede limpio al finalizar
     */
    @Test
    void publish_ValidEvent_AddsCorrelationHeaderAndClearsMdc() {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        SendResult<String, Object> sendResult = mock(SendResult.class);
        var metadata = mock(RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn("hotel_availability_searches");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(12L);
        HotelSearchEvent event = validEvent();
        doAnswer(invocation -> {
            assertThat(MDC.getCopyOfContextMap())
                .as("El envío debe ejecutarse dentro del scope de correlación")
                .containsEntry(CorrelationContext.COMPONENT,
                    CorrelationContext.KAFKA_PRODUCER_COMPONENT)
                .containsEntry(CorrelationContext.SEARCH_ID, event.searchId().toString());
            return CompletableFuture.completedFuture(sendResult);
        }).when(template).send(any(ProducerRecord.class));
        KafkaHotelSearchPublisher publisher =
            new KafkaHotelSearchPublisher(template, TOPIC);

        // when
        publisher.publish(event);

        // then
        ArgumentCaptor<ProducerRecord<String, Object>> captor =
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        var header = captor.getValue().headers().lastHeader(CorrelationContext.SEARCH_ID);
        assertThat(header).as("El evento debe propagar el searchId")
            .isNotNull();
        assertAll(
            () -> assertThat(new String(header.value(), StandardCharsets.UTF_8))
                .isEqualTo(event.searchId().toString()),
            () -> assertThat(MDC.getCopyOfContextMap()).isNull());
    }

    /**
     * Verifica que un timeout confirmado por el future se traduzca a error de
     * disponibilidad.
     *
     * Condiciones:
     * - El future del publish falla con TimeoutException de Kafka
     *
     * Se espera:
     * - Se lance HotelSearchUnavailableException
     */
    @Test
    void publish_BrokerTimeout_ThrowsUnavailableException() {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new TimeoutException("timeout"));
        doReturn(failed).when(template).send(any(ProducerRecord.class));
        KafkaHotelSearchPublisher publisher = new KafkaHotelSearchPublisher(template, TOPIC);

        // when
        var exception = assertThrows(
            HotelSearchUnavailableException.class,
            () -> publisher.publish(validEvent()));

        // then
        assertThat(exception).hasMessage("Timeout publicando en Kafka");
        assertThat(exception.getCause()).isInstanceOf(ExecutionException.class)
            .cause()
            .isInstanceOf(TimeoutException.class);
    }

    /**
     * Verifica que un fallo general confirmado por el future se traduzca a
     * error de disponibilidad.
     *
     * Condiciones:
     * - El future del publish falla con una causa genérica
     *
     * Se espera:
     * - Se lance HotelSearchUnavailableException
     */
    @Test
    void publish_BrokerFailure_ThrowsUnavailableException() {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker caído"));
        doReturn(failed).when(template).send(any(ProducerRecord.class));
        KafkaHotelSearchPublisher publisher = new KafkaHotelSearchPublisher(template, TOPIC);

        // when
        var exception = assertThrows(
            HotelSearchUnavailableException.class,
            () -> publisher.publish(validEvent()));

        // then
        assertThat(exception).hasMessage("Error publicando en Kafka");
        assertThat(exception.getCause()).isInstanceOf(ExecutionException.class)
            .cause()
            .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Verifica que un error Kafka lanzado sincrónicamente por el producer se
     * traduzca a error de disponibilidad.
     *
     * Condiciones:
     * - KafkaTemplate.send lanza KafkaException
     *
     * Se espera:
     * - Se lance HotelSearchUnavailableException con la causa original
     */
    @Test
    void publish_SynchronousKafkaFailure_ThrowsUnavailableException() {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        KafkaException cause = new KafkaException("producer cerrado");
        doThrow(cause).when(template).send(any(ProducerRecord.class));
        KafkaHotelSearchPublisher publisher = new KafkaHotelSearchPublisher(template, TOPIC);

        // when
        var exception = assertThrows(
            HotelSearchUnavailableException.class,
            () -> publisher.publish(validEvent()));

        // then
        assertThat(exception.getCause()).isSameAs(cause);
    }

    /**
     * Verifica que una ExecutionException sin causa se traduzca a error de
     * disponibilidad genérico y no a timeout.
     *
     * Condiciones:
     * - El future del publish falla con ExecutionException sin causa
     *
     * Se espera:
     * - Se lance HotelSearchUnavailableException con el mensaje de error
     *   genérico y la ExecutionException como causa
     */
    @Test
    void publish_ExecutionFailureWithoutCause_ThrowsUnavailableException() throws Exception {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        ExecutionException failure = new ExecutionException("fallo sin causa", null);
        when(future.get()).thenThrow(failure);
        doReturn(future).when(template).send(any(ProducerRecord.class));
        KafkaHotelSearchPublisher publisher = new KafkaHotelSearchPublisher(template, TOPIC);

        // when
        var exception = assertThrows(
            HotelSearchUnavailableException.class,
            () -> publisher.publish(validEvent()));

        // then
        assertThat(exception).hasMessage("Error publicando en Kafka");
        assertThat(exception.getCause()).isSameAs(failure);
    }

    /**
     * Verifica que una interrupción mientras se espera el ACK restablezca el
     * flag de interrupción del hilo y se traduzca a error de disponibilidad.
     *
     * Condiciones:
     * - El hilo es interrumpido mientras espera el future del publish
     *
     * Se espera:
     * - Se lance HotelSearchUnavailableException con la InterruptedException
     *   como causa
     * - El hilo conserve el flag de interrupción
     * - El MDC quede limpio
     */
    @Test
    void publish_InterruptedWhileWaitingAck_RestoresInterruptFlag() throws Exception {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        InterruptedException interruption = new InterruptedException("interrumpido");
        when(future.get()).thenThrow(interruption);
        doReturn(future).when(template).send(any(ProducerRecord.class));
        KafkaHotelSearchPublisher publisher = new KafkaHotelSearchPublisher(template, TOPIC);

        // when
        var exception = assertThrows(
            HotelSearchUnavailableException.class,
            () -> publisher.publish(validEvent()));

        // then
        assertAll(
            () -> assertThat(exception).hasMessage("Publicación interrumpida"),
            () -> assertThat(exception.getCause()).isSameAs(interruption),
            () -> assertThat(Thread.currentThread().isInterrupted())
                .as("El publisher debe restablecer el flag de interrupción")
                .isTrue(),
            () -> assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty());
    }

    /**
     * Verifica que, con DEBUG habilitado, el publisher registre la metadata
     * que devolvió el broker junto con el searchId.
     *
     * Condiciones:
     * - El logger del publisher está en nivel DEBUG
     * - El broker confirma el envío en la partición 0, offset 12
     *
     * Se espera:
     * - Un único evento DEBUG con topic, partición, offset y searchId
     */
    @Test
    void publish_DebugEnabled_LogsBrokerMetadata() {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        doReturn(CompletableFuture.completedFuture(acknowledgedResult()))
            .when(template).send(any(ProducerRecord.class));
        KafkaHotelSearchPublisher publisher = new KafkaHotelSearchPublisher(template, TOPIC);
        HotelSearchEvent event = validEvent();
        Logger logger = (Logger) LoggerFactory.getLogger(KafkaHotelSearchPublisher.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);

        // when
        try {
            publisher.publish(event);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }

        // then
        assertThat(appender.list)
            .as("Debe registrarse un único evento DEBUG con la metadata del broker")
            .singleElement()
            .satisfies(logged -> assertAll(
                () -> assertThat(logged.getLevel()).isEqualTo(Level.DEBUG),
                () -> assertThat(logged.getFormattedMessage()).isEqualTo(
                    "Evento publicado: topic=" + TOPIC + ", partition=0, offset=12, key="
                        + event.searchId())));
    }

    /**
     * Verifica que con el logger en INFO el publisher no registre nada ni
     * consulte la metadata del broker.
     *
     * Condiciones:
     * - El logger del publisher está en nivel INFO
     * - El broker confirma el envío
     *
     * Se espera:
     * - Ningún evento registrado
     * - getRecordMetadata() no se invoca sobre el SendResult
     */
    @Test
    void publish_InfoLevel_DoesNotLogNorReadMetadata() throws Exception {
        // given
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        SendResult<String, Object> sendResult = mock(SendResult.class);
        doReturn(CompletableFuture.completedFuture(sendResult))
            .when(template).send(any(ProducerRecord.class));
        KafkaHotelSearchPublisher publisher = new KafkaHotelSearchPublisher(template, TOPIC);
        Logger logger = (Logger) LoggerFactory.getLogger(KafkaHotelSearchPublisher.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);

        // when
        try {
            publisher.publish(validEvent());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }

        // then
        assertThat(appender.list).as("No debe registrarse ningún evento").isEmpty();
        verify(sendResult, never()).getRecordMetadata();
    }

    // <editor-fold desc="Helpers">
    private SendResult<String, Object> acknowledgedResult() {
        SendResult<String, Object> sendResult = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn(TOPIC);
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(12L);
        return sendResult;
    }

    // </editor-fold>
}
