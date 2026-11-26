package com.hotelsearch.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hotelsearch.infrastructure.observability.CorrelationContext;
import jakarta.servlet.ServletException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Tests del filtro de correlación de solicitudes HTTP.
 */
class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestCorrelationFilter.class);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private Level previousLevel;

    /**
     * Verifica que el filtro agregue la correlación HTTP y la retire al
     * finalizar la solicitud.
     *
     * Condiciones:
     * - Se procesa una solicitud POST
     *
     * Se espera:
     * - Durante el request existan component, requestId y traceId
     * - Después del request el MDC vuelva a su estado inicial
     */
    @Test
    void doFilter_PostRequest_SetsAndClearsRequestCorrelation() throws Exception {
        // given
        AtomicReference<Map<String, String>> contextDuringRequest = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/search");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(CorrelationContext.TRACE_ID, "trace-parent");

        try {
            // when
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                contextDuringRequest.set(MDC.getCopyOfContextMap()));

            // then
            assertAll(
                () -> assertThat(contextDuringRequest.get())
                    .as("El request debe incluir la correlación de la capa REST")
                    .containsEntry(CorrelationContext.COMPONENT, CorrelationContext.REST_COMPONENT)
                    .containsEntry(CorrelationContext.TRACE_ID, "trace-parent")
                    .doesNotContainKey(CorrelationContext.SEARCH_ID),
                () -> assertThat(contextDuringRequest.get().get(CorrelationContext.REQUEST_ID))
                    .as("Cada request debe recibir un requestId")
                    .isNotBlank(),
                () -> assertThat(MDC.getCopyOfContextMap())
                    .as("El filtro debe restaurar el contexto previo")
                    .containsEntry(CorrelationContext.TRACE_ID, "trace-parent")
                    .doesNotContainKeys(
                        CorrelationContext.COMPONENT,
                        CorrelationContext.REQUEST_ID,
                        CorrelationContext.SEARCH_ID));
        } finally {
            MDC.clear();
        }
    }

    /**
     * Verifica que un searchId válido recibido por query string se incorpore
     * a la correlación del request.
     *
     * Condiciones:
     * - La solicitud GET contiene un UUID válido
     *
     * Se espera:
     * - El searchId forme parte del MDC durante el request
     * - No se registre ningún valor descartado
     */
    @Test
    void doFilter_GetRequestWithValidSearchId_AddsSearchIdToCorrelation() throws Exception {
        // given
        String searchId = "550e8400-e29b-41d4-a716-446655440000";
        AtomicReference<Map<String, String>> contextDuringRequest = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/count");
        request.setParameter("searchId", searchId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
            contextDuringRequest.set(MDC.getCopyOfContextMap()));

        // then
        assertAll(
            () -> assertThat(contextDuringRequest.get())
                .as("Un searchId válido debe entrar al MDC")
                .containsEntry(CorrelationContext.SEARCH_ID, searchId),
            () -> assertThat(appender.list)
                .as("Un searchId válido no se registra como descartado")
                .isEmpty());
    }

    /**
     * Verifica que un request sin searchId no agregue correlación de búsqueda
     * ni registre un descarte.
     *
     * Condiciones:
     * - La solicitud GET no contiene el parámetro searchId
     *
     * Se espera:
     * - El MDC no contenga searchId durante el request
     * - No se registre ningún evento
     */
    @Test
    void doFilter_GetRequestWithoutSearchId_OmitsSearchIdWithoutLogging() throws Exception {
        // given
        AtomicReference<Map<String, String>> contextDuringRequest = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/count");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
            contextDuringRequest.set(MDC.getCopyOfContextMap()));

        // then
        assertAll(
            () -> assertThat(contextDuringRequest.get())
                .as("Sin searchId no hay correlación de búsqueda")
                .doesNotContainKey(CorrelationContext.SEARCH_ID),
            () -> assertThat(appender.list)
                .as("La ausencia del parámetro no es un descarte")
                .isEmpty());
    }

    /**
     * Verifica que un searchId vacío o con solo espacios se trate como
     * ausente.
     *
     * Condiciones:
     * - La solicitud GET contiene searchId con espacios
     *
     * Se espera:
     * - El MDC no contenga searchId durante el request
     * - No se registre ningún evento
     */
    @Test
    void doFilter_GetRequestWithBlankSearchId_OmitsSearchIdWithoutLogging() throws Exception {
        // given
        AtomicReference<Map<String, String>> contextDuringRequest = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/count");
        request.setParameter("searchId", "  ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
            contextDuringRequest.set(MDC.getCopyOfContextMap()));

        // then
        assertAll(
            () -> assertThat(contextDuringRequest.get())
                .as("Un searchId vacío no entra al MDC")
                .doesNotContainKey(CorrelationContext.SEARCH_ID),
            () -> assertThat(appender.list)
                .as("Un searchId vacío se trata como ausente, sin log")
                .isEmpty());
    }

    /**
     * Verifica que el MDC se limpie cuando la cadena de filtros propaga una
     * excepción.
     *
     * Condiciones:
     * - El filtro posterior falla con ServletException
     *
     * Se espera:
     * - La excepción se propague
     * - No queden valores de correlación en el MDC
     */
    @Test
    void doFilter_DownstreamFailure_ClearsCorrelation() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/count");
        MockHttpServletResponse response = new MockHttpServletResponse();
        var failure = new ServletException("fallo simulado");

        // when
        var exception = assertThrows(ServletException.class, () ->
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
                throw failure;
            }));

        // then
        assertThat(exception).isSameAs(failure);
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    /**
     * Verifica que un searchId inválido no se use como dato de correlación
     * y que el descarte quede registrado en DEBUG.
     *
     * Condiciones:
     * - La solicitud contiene un valor que no es UUID
     *
     * Se espera:
     * - El MDC no contenga searchId durante el request
     * - Un único evento DEBUG con el valor descartado y sin excepción adjunta
     */
    @Test
    void doFilter_GetRequestWithInvalidSearchId_OmitsSearchIdAndLogsDebug() throws Exception {
        // given
        AtomicReference<Map<String, String>> contextDuringRequest = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/count");
        request.setParameter("searchId", "no-es-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
            contextDuringRequest.set(MDC.getCopyOfContextMap()));

        // then
        assertThat(appender.list).as("Debe registrarse un único descarte").hasSize(1);
        ILoggingEvent logged = appender.list.getFirst();
        assertAll(
            () -> assertThat(contextDuringRequest.get())
                .as("Un searchId inválido no entra al MDC")
                .doesNotContainKey(CorrelationContext.SEARCH_ID),
            () -> assertThat(logged.getLevel()).isEqualTo(Level.DEBUG),
            () -> assertThat(logged.getFormattedMessage())
                .isEqualTo("searchId descartado para la correlación: no-es-uuid"),
            () -> assertThat(logged.getThrowableProxy())
                .as("El descarte no debe adjuntar la traza de la excepción")
                .isNull());
    }

    /**
     * Verifica que el valor descartado se registre truncado y sin caracteres
     * de control, para que no pueda falsificar líneas de log.
     *
     * Condiciones:
     * - El searchId contiene CR, LF y tab, y supera los 64 caracteres
     *
     * Se espera:
     * - Los caracteres de control se reemplacen por '_'
     * - Se registren solo los primeros 64 caracteres seguidos de '...'
     */
    @Test
    void doFilter_InvalidSearchIdWithControlCharacters_LogsTruncatedSanitizedValue() throws Exception {
        // given
        String injected = "abc\r\nINFO falso\t" + "x".repeat(100);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/count");
        request.setParameter("searchId", injected);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String expectedValue = "abc__INFO falso_" + "x".repeat(48) + "...";

        // when
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        // then
        assertThat(appender.list).as("Debe registrarse un único descarte").hasSize(1);
        String message = appender.list.getFirst().getFormattedMessage();
        assertAll(
            () -> assertThat(message)
                .isEqualTo("searchId descartado para la correlación: " + expectedValue),
            () -> assertThat(message)
                .as("El mensaje no debe contener saltos de línea ni tabs")
                .doesNotContain("\r", "\n", "\t"));
    }

    /**
     * Verifica que los separadores de línea y párrafo de Unicode también se
     * reemplacen, porque algunos visores de logs los muestran como salto.
     *
     * Condiciones:
     * - El searchId contiene U+2028 y U+2029
     *
     * Se espera:
     * - Ambos separadores se registren como '_'
     */
    @Test
    void doFilter_InvalidSearchIdWithUnicodeLineSeparators_LogsSanitizedValue() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/count");
        request.setParameter("searchId", "abc\u2028INFO falso\u2029fin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        // then
        assertThat(appender.list).as("Debe registrarse un único descarte").hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage())
            .isEqualTo("searchId descartado para la correlación: abc_INFO falso_fin");
    }

    /**
     * Verifica que con el logger en INFO no se registre el descarte.
     *
     * Condiciones:
     * - El logger del filtro está en INFO
     * - La solicitud contiene un searchId inválido
     *
     * Se espera:
     * - No se registre ningún evento
     */
    @Test
    void doFilter_InvalidSearchIdAtInfoLevel_DoesNotLog() throws Exception {
        // given
        logger.setLevel(Level.INFO);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/count");
        request.setParameter("searchId", "no-es-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        // then
        List<ILoggingEvent> events = appender.list;
        assertThat(events).as("En INFO el descarte no se registra").isEmpty();
    }

    // <editor-fold desc="Helpers">
    @BeforeEach
    void captureLogs() {
        previousLevel = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void clearMdcAndLogs() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        MDC.clear();
    }
    // </editor-fold>
}
