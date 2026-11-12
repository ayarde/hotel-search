package com.hotelsearch.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Tests del scope de correlación del MDC.
 */
class MdcScopeTest {

    /**
     * Verifica que el scope agregue sus valores y restaure el contexto
     * previo al cerrarse.
     *
     * Condiciones:
     * - El MDC ya contiene un traceId del contexto padre
     *
     * Se espera:
     * - Durante el scope existan traceId, component y searchId
     * - Al cerrarlo se conserve traceId y se retiren los valores propios
     */
    @Test
    void open_ExistingParentContext_RestoresPreviousValues() {
        // given
        MDC.put(CorrelationContext.TRACE_ID, "trace-parent");

        try {
            // when
            try (MdcScope ignored = MdcScope.search(
                CorrelationContext.KAFKA_PRODUCER_COMPONENT, "search-1")) {
                // then
                assertThat(MDC.getCopyOfContextMap())
                    .as("El scope debe combinar el contexto padre y sus propios valores")
                    .containsEntry(CorrelationContext.TRACE_ID, "trace-parent")
                    .containsEntry(CorrelationContext.COMPONENT,
                        CorrelationContext.KAFKA_PRODUCER_COMPONENT)
                    .containsEntry(CorrelationContext.SEARCH_ID, "search-1");
            }

            // then
            assertThat(MDC.getCopyOfContextMap())
                .as("El cierre debe restaurar el contexto padre")
                .containsEntry(CorrelationContext.TRACE_ID, "trace-parent")
                .doesNotContainKeys(CorrelationContext.COMPONENT, CorrelationContext.SEARCH_ID);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Verifica que el cierre de un scope sin contexto previo vacíe el MDC.
     *
     * Condiciones:
     * - El MDC está vacío antes de abrir el scope
     *
     * Se espera:
     * - El MDC quede vacío después de cerrar el scope
     */
    @Test
    void close_NoPreviousContext_ClearsMdc() {
        // given
        MDC.clear();

        // when
        try (MdcScope ignored = MdcScope.component(CorrelationContext.REST_COMPONENT)) {
            // then
            assertThat(MDC.get(CorrelationContext.COMPONENT))
                .isEqualTo(CorrelationContext.REST_COMPONENT);
        }

        // then
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    /**
     * Verifica que cerrar dos veces el mismo scope no sobrescriba un
     * contexto creado después del primer cierre.
     *
     * Condiciones:
     * - El scope ya se cerró una vez
     * - Otro componente modificó el MDC posteriormente
     *
     * Se espera:
     * - El segundo cierre sea una operación segura y no restore el contexto
     */
    @Test
    void close_CalledTwice_DoesNotOverwriteNewerContext() {
        // given
        MDC.clear();
        MdcScope scope = MdcScope.component(CorrelationContext.REST_COMPONENT);
        scope.close();
        MDC.put(CorrelationContext.COMPONENT, "otro-componente");

        try {
            // when
            scope.close();

            // then
            assertThat(MDC.get(CorrelationContext.COMPONENT))
                .as("El segundo cierre no debe restaurar un contexto obsoleto")
                .isEqualTo("otro-componente");
        } finally {
            MDC.clear();
        }
    }

    /**
     * Verifica que los argumentos obligatorios del scope sean validados.
     *
     * Condiciones:
     * - Se intenta abrir un scope con valores nulos
     *
     * Se espera:
     * - Se rechace cada argumento nulo con NullPointerException
     */
    @Test
    void open_NullArguments_ThrowsNullPointerException() {
        // given / when / then
        assertAll(
            () -> assertThatNullPointerException()
                .isThrownBy(() -> MdcScope.open(null)),
            () -> assertThatNullPointerException()
                .isThrownBy(() -> MdcScope.component(null)),
            () -> assertThatNullPointerException()
                .isThrownBy(() -> MdcScope.search(null, "search-1")),
            () -> assertThatNullPointerException()
                .isThrownBy(() -> MdcScope.search(CorrelationContext.REST_COMPONENT, null)));
    }

}
