package com.hotelsearch.infrastructure.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.MDC;

/**
 * Scope delimitado para las entradas del MDC.
 *
 * <p>Al abrir el scope se conserva una copia del contexto previo y se
 * agregan los valores de correlación. Al cerrarlo se restaura el contexto
 * anterior. Este comportamiento permite que un traceId abierto por la
 * capa de observabilidad permanezca disponible durante una operación
 * anidada sin que este scope lo elimine accidentalmente.</p>
 */
public final class MdcScope implements AutoCloseable {

    private final Map<String, String> previousContext;
    private boolean closed;

    private MdcScope(Map<String, String> previousContext) {
        this.previousContext = previousContext;
    }

    /**
     * Abre un scope con los valores de correlación indicados.
     *
     * @param values valores que se agregan al MDC
     * @return scope que restaura el contexto previo al cerrarse
     */
    public static MdcScope open(Map<String, String> values) {
        Objects.requireNonNull(values, "values no puede ser nulo");

        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        Map<String, String> updatedContext = previousContext == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(previousContext);
        updatedContext.putAll(values);
        MDC.setContextMap(updatedContext);

        return new MdcScope(previousContext);
    }

    /**
     * Abre un scope para un componente de la aplicación.
     *
     * @param component nombre del componente
     * @return scope de correlación
     */
    public static MdcScope component(String component) {
        Objects.requireNonNull(component, "component no puede ser nulo");
        return open(Map.of(CorrelationContext.COMPONENT, component));
    }

    /**
     * Abre un scope para un componente y una búsqueda concretos.
     *
     * @param component nombre del componente
     * @param searchId identificador de la búsqueda
     * @return scope de correlación
     */
    public static MdcScope search(String component, String searchId) {
        Objects.requireNonNull(component, "component no puede ser nulo");
        Objects.requireNonNull(searchId, "searchId no puede ser nulo");
        return open(Map.of(
            CorrelationContext.COMPONENT, component,
            CorrelationContext.SEARCH_ID, searchId));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        if (previousContext == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousContext);
        }
        closed = true;
    }
}
