package com.hotelsearch.infrastructure.input.rest;

import com.hotelsearch.domain.valueobject.SearchId;
import com.hotelsearch.infrastructure.observability.CorrelationContext;
import com.hotelsearch.infrastructure.observability.MdcScope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro que establece la correlación de una solicitud HTTP.
 *
 * <p>Cada request recibe un identificador propio. Si el request ya contiene
 * un {@code searchId} válido, también se incorpora al MDC; uno inválido
 * se descarta y queda registrado en DEBUG. El scope se cierra siempre,
 * incluso si el controller o uno de los filtros posteriores propaga una
 * excepción.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    /** Longitud máxima del valor descartado que se escribe en el log. */
    private static final int MAX_LOGGED_LENGTH = 64;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        // LinkedHashMap conserva el orden de las entradas del MDC.
        Map<String, String> correlation = new LinkedHashMap<>();
        correlation.put(CorrelationContext.COMPONENT, CorrelationContext.REST_COMPONENT);
        correlation.put(CorrelationContext.REQUEST_ID, UUID.randomUUID().toString());

        validSearchId(request.getParameter("searchId"))
            .ifPresent(searchId -> correlation.put(CorrelationContext.SEARCH_ID, searchId));

        try (MdcScope _ = MdcScope.open(correlation)) {
            filterChain.doFilter(request, response);
        }
    }

    private Optional<String> validSearchId(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(SearchId.of(candidate).toString());
        } catch (IllegalArgumentException _) {
            // Un valor inválido se informará como error HTTP. No se usa como
            // dato de correlación porque no identifica una búsqueda válida.
            // La traza no aporta nada: el motivo ya es el valor recibido.
            if (LOG.isDebugEnabled()) {
                LOG.debug("searchId descartado para la correlación: {}", sanitizeForLog(candidate));
            }
            return Optional.empty();
        }
    }

    /**
     * Prepara un valor recibido del cliente para escribirlo en el log.
     *
     * <p>Recorta el valor a {@value #MAX_LOGGED_LENGTH} caracteres y reemplaza
     * por {@code _} los caracteres de control y los separadores de línea y
     * párrafo de Unicode (U+2028 y U+2029), así ninguno puede abrir una línea
     * de log falsa.</p>
     */
    private static String sanitizeForLog(String value) {
        boolean truncated = value.length() > MAX_LOGGED_LENGTH;
        String visible = truncated ? value.substring(0, MAX_LOGGED_LENGTH) : value;
        StringBuilder sanitized = new StringBuilder(visible.length() + 3);
        visible.chars().forEach(character ->
            sanitized.append(breaksLogLine(character) ? '_' : (char) character));
        if (truncated) {
            sanitized.append("...");
        }
        return sanitized.toString();
    }

    private static boolean breaksLogLine(int character) {
        int type = Character.getType(character);
        return Character.isISOControl(character)
            || type == Character.LINE_SEPARATOR
            || type == Character.PARAGRAPH_SEPARATOR;
    }
}
