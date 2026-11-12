package com.hotelsearch.infrastructure.observability;

/**
 * Nombres compartidos para la correlación entre la capa HTTP y Kafka.
 *
 * <p>Los mismos nombres se utilizan para las entradas del MDC y para el
 * header de {@code searchId}. De esta forma, los logs conservados por un
 * consumidor pueden correlacionarse directamente con el evento recibido.</p>
 */
public final class CorrelationContext {

    /** Identificador de la búsqueda. */
    public static final String SEARCH_ID = "searchId";

    /** Identificador de la solicitud HTTP. */
    public static final String REQUEST_ID = "requestId";

    /** Componente que está ejecutando la operación. */
    public static final String COMPONENT = "component";

    /** Identificador de traza propagado por Micrometer Tracing. */
    public static final String TRACE_ID = "traceId";

    /** Identificador de span propagado por Micrometer Tracing. */
    public static final String SPAN_ID = "spanId";

    /** Componente de entrada HTTP. */
    public static final String REST_COMPONENT = "rest";

    /** Componente de publicación Kafka. */
    public static final String KAFKA_PRODUCER_COMPONENT = "kafka-producer";

    /** Componente de consumo Kafka. */
    public static final String KAFKA_CONSUMER_COMPONENT = "kafka-consumer";

    private CorrelationContext() {
    }
}
