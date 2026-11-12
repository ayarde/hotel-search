package com.hotelsearch.infrastructure.input.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Filtro que impone el límite real de 16 KB sobre los bodies POST.
 *
 * <p>El tamaño declarado en {@code Content-Length} se comprueba antes de
 * leer el body. Si el cliente no declara el tamaño o informa uno menor al
 * real, el filtro lee como máximo 16 KB más un byte para detectar el
 * exceso sin cargar una request grande en memoria.</p>
 */
@Component
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    /** Límite máximo permitido para un body HTTP. */
    public static final int MAX_BODY_BYTES = 16 * 1024;

    private final HandlerExceptionResolver exceptionResolver;

    /**
     * Crea el filtro con el resolver que aplica Problem Details.
     *
     * @param exceptionResolver resolver de excepciones de Spring MVC
     */
    public RequestBodySizeLimitFilter(
        @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver
    ) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            rejectOversizedBody(request, response);
            return;
        }

        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            rejectOversizedBody(request, response);
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private void rejectOversizedBody(HttpServletRequest request, HttpServletResponse response)
        throws ServletException {
        var exception = new RequestBodyTooLargeException(MAX_BODY_BYTES);
        try {
            ModelAndView result = exceptionResolver.resolveException(request, response, null, exception);
            if (result == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
            }
        } catch (Exception e) {
            throw new ServletException("No se pudo generar la respuesta para un body demasiado grande", e);
        }
    }

    /**
     * Request con el body ya leído, de modo que el controller pueda consumir
     * el mismo contenido sin volver a acceder al socket.
     */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream content = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return content.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("No se admite lectura asíncrona del body");
                }

                @Override
                public int read() {
                    return content.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            if (encoding == null) {
                encoding = StandardCharsets.UTF_8.name();
            }
            return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
        }
    }
}
