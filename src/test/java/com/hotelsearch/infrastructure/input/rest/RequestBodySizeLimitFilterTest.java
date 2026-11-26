package com.hotelsearch.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Tests del filtro que impone el límite real del body HTTP.
 */
class RequestBodySizeLimitFilterTest {

    /**
     * Verifica que un body mayor que el límite se rechace aunque el cliente
     * no declare un Content-Length confiable.
     *
     * Condiciones:
     * - Content-Length informa 16 KB
     * - El contenido real tiene un byte más que el máximo
     *
     * Se espera:
     * - Se resuelva una RequestBodyTooLargeException
     * - La cadena de filtros no reciba la request
     */
    @Test
    void doFilter_BodyExceedsLimitWithoutTrustedContentLength_RejectsRequest() throws Exception {
        // given
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        when(resolver.resolveException(any(), any(), isNull(), any(RequestBodyTooLargeException.class)))
            .thenAnswer(invocation -> {
                MockHttpServletResponse response = invocation.getArgument(1);
                response.setStatus(400);
                return new ModelAndView();
            });
        RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(resolver);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/search") {
            @Override
            public long getContentLengthLong() {
                return RequestBodySizeLimitFilter.MAX_BODY_BYTES;
            }
        };
        request.setContent(new byte[RequestBodySizeLimitFilter.MAX_BODY_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        verify(resolver).resolveException(
            same(request),
            same(response),
            isNull(),
            any(RequestBodyTooLargeException.class));
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    /**
     * Verifica que el filtro conserve una respuesta 400 si el resolver de
     * Spring MVC no produce una vista para la excepción.
     *
     * Condiciones:
     * - El resolver devuelve null
     * - Content-Length supera el máximo
     *
     * Se espera:
     * - La respuesta quede en status 400
     */
    @Test
    void doFilter_ResolverReturnsNull_SendsBadRequest() throws Exception {
        // given
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        when(resolver.resolveException(any(), any(), isNull(), any(RequestBodyTooLargeException.class)))
            .thenReturn(null);
        RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(resolver);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/search");
        request.setContent(new byte[RequestBodySizeLimitFilter.MAX_BODY_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertAll(
            () -> assertThat(response.getStatus()).isEqualTo(400),
            () -> assertThat(response.getErrorMessage()).contains("16384 bytes"),
            () -> assertThat(chain.getRequest()).isNull());
    }

    /**
     * Verifica que el filtro traduzca a ServletException el fallo de
     * sendError, conservando la IOException original como causa.
     *
     * Condiciones:
     * - El resolver devuelve null
     * - response.sendError lanza IOException
     *
     * Se espera:
     * - ServletException con el mensaje del filtro
     * - La IOException como causa
     */
    @Test
    void doFilter_SendErrorFails_ThrowsServletExceptionWithCause() throws Exception {
        // given
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        when(resolver.resolveException(any(), any(), isNull(), any(RequestBodyTooLargeException.class)))
            .thenReturn(null);
        RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(resolver);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/search");
        request.setContent(new byte[RequestBodySizeLimitFilter.MAX_BODY_BYTES + 1]);
        MockHttpServletResponse response = spy(new MockHttpServletResponse());
        IOException failure = new IOException("conexión cerrada");
        doThrow(failure).when(response).sendError(anyInt(), anyString());
        MockFilterChain chain = new MockFilterChain();

        // when
        ServletException exception = assertThrows(
            ServletException.class,
            () -> filter.doFilter(request, response, chain));

        // then
        assertAll(
            () -> assertThat(exception)
                .hasMessage("No se pudo generar la respuesta para un body demasiado grande"),
            () -> assertThat(exception.getCause()).isSameAs(failure),
            () -> assertThat(chain.getRequest()).isNull());
    }

    /**
     * Verifica que una excepción runtime del resolver se propague sin
     * envolverla.
     *
     * Condiciones:
     * - El resolver lanza una RuntimeException
     *
     * Se espera:
     * - Se propague la misma instancia
     * - La cadena de filtros no reciba la request
     */
    @Test
    void doFilter_ResolverThrowsRuntimeException_PropagatesSameInstance() throws Exception {
        // given
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        IllegalStateException failure = new IllegalStateException("resolver roto");
        when(resolver.resolveException(any(), any(), isNull(), any(RequestBodyTooLargeException.class)))
            .thenThrow(failure);
        RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(resolver);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/search");
        request.setContent(new byte[RequestBodySizeLimitFilter.MAX_BODY_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> filter.doFilter(request, response, chain));

        // then
        assertThat(exception).isSameAs(failure);
        assertThat(chain.getRequest()).isNull();
    }

}
