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
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro que establece la correlación de una solicitud HTTP.
 *
 * <p>Cada request recibe un identificador propio. Si el request ya contiene
 * un {@code searchId} válido, también se incorpora al MDC. El scope se
 * cierra siempre, incluso si el controller o uno de los filtros posteriores
 * propaga una excepción.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Map<String, String> correlation = new LinkedHashMap<>();
        correlation.put(CorrelationContext.COMPONENT, CorrelationContext.REST_COMPONENT);
        correlation.put(CorrelationContext.REQUEST_ID, UUID.randomUUID().toString());

        String searchId = validSearchId(request.getParameter("searchId"));
        if (searchId != null) {
            correlation.put(CorrelationContext.SEARCH_ID, searchId);
        }

        try (MdcScope ignored = MdcScope.open(correlation)) {
            filterChain.doFilter(request, response);
        }
    }

    private String validSearchId(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        try {
            return SearchId.of(candidate).toString();
        } catch (IllegalArgumentException ignored) {
            // Un valor inválido se informará como error HTTP. No se usa como
            // dato de correlación porque no identifica una búsqueda válida.
            return null;
        }
    }
}
