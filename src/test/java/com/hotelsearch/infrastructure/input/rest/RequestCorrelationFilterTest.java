package com.hotelsearch.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hotelsearch.infrastructure.observability.CorrelationContext;
import jakarta.servlet.ServletException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Tests del filtro de correlación de solicitudes HTTP.
 */
class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

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
            assertThat(contextDuringRequest.get())
                .as("El request debe incluir la correlación de la capa REST")
                .containsEntry(CorrelationContext.COMPONENT, CorrelationContext.REST_COMPONENT)
                .containsEntry(CorrelationContext.TRACE_ID, "trace-parent")
                .doesNotContainKey(CorrelationContext.SEARCH_ID);
            assertThat(contextDuringRequest.get().get(CorrelationContext.REQUEST_ID))
                .as("Cada request debe recibir un requestId")
                .isNotBlank();
            assertThat(MDC.getCopyOfContextMap())
                .as("El filtro debe restaurar el contexto previo")
                .containsEntry(CorrelationContext.TRACE_ID, "trace-parent")
                .doesNotContainKeys(
                    CorrelationContext.COMPONENT,
                    CorrelationContext.REQUEST_ID,
                    CorrelationContext.SEARCH_ID);
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
     */
    @Test
    void doFilter_GetRequestWithValidSearchId_AddsSearchIdToCorrelation() throws Exception {
        // given
        String searchId = "550e8400-e29b-41d4-a716-446655440000";
        AtomicReference<Map<String, String>> contextDuringRequest = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/count");
        request.setParameter("searchId", searchId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            // when
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                contextDuringRequest.set(MDC.getCopyOfContextMap()));

            // then
            assertThat(contextDuringRequest.get())
                .containsEntry(CorrelationContext.SEARCH_ID, searchId);
        } finally {
            MDC.clear();
        }
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
     * Verifica que un searchId inválido no se use como dato de correlación.
     *
     * Condiciones:
     * - La solicitud contiene un valor que no es UUID
     *
     * Se espera:
     * - El MDC no contenga searchId durante el request
     */
    @Test
    void doFilter_GetRequestWithInvalidSearchId_OmitsSearchId() throws Exception {
        // given
        AtomicReference<Map<String, String>> contextDuringRequest = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/count");
        request.setParameter("searchId", "no-es-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
            contextDuringRequest.set(MDC.getCopyOfContextMap()));

        // then
        assertThat(contextDuringRequest.get()).doesNotContainKey(CorrelationContext.SEARCH_ID);
    }

    // <editor-fold desc="Helpers">
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }
    // </editor-fold>
}
