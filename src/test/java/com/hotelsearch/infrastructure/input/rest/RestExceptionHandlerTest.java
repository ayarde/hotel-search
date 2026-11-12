package com.hotelsearch.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hotelsearch.infrastructure.input.rest.dto.HotelSearchRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Tests directos de las ramas de validación del manejador de Problem Details.
 */
class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    /**
     * Verifica que los errores de Bean Validation se agrupen por campo y que
     * los errores globales conserven su categoría.
     *
     * Condiciones:
     * - Hay un error de campo con mensaje
     * - Hay un error de campo sin mensaje
     * - Hay un error global
     *
     * Se espera:
     * - Status 400
     * - El mapa de errores contenga los tres casos
     */
    @Test
    void handleMethodArgumentNotValid_FieldAndGlobalErrors_ReturnsDetailedProblem() {
        // given
        HotelSearchRequest target = new HotelSearchRequest(
            "550e8400-e29b-41d4-a716-446655440000", "09/11/2026", "29/11/2026", List.of(29));
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.rejectValue("hotelId", "invalid", "hotelId inválido");
        bindingResult.rejectValue("checkOut", "invalid", null);
        bindingResult.reject("global", "Error global");
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
            mock(MethodParameter.class), bindingResult);
        HttpServletRequest request = request("/search");

        // when
        var response = handler.handleMethodArgumentNotValid(exception, request);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        Map<?, ?> errors = (Map<?, ?>) response.getBody().getProperties().get("errors");
        assertThat(errors.get("hotelId")).isEqualTo(List.of("hotelId inválido"));
        assertThat(errors.get("checkOut")).isEqualTo(List.of("valor inválido"));
        assertThat(errors.get("_global")).isEqualTo(List.of("Error global"));
    }

    /**
     * Verifica que una violación de restricción se traduzca a 400 usando el
     * primer mensaje en orden determinista.
     *
     * Condiciones:
     * - Existen dos restricciones con mensajes distintos
     *
     * Se espera:
     * - Status 400 con el mensaje ordenado más pequeño
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void handleConstraintViolation_MultipleViolations_ReturnsFirstSortedMessage() {
        // given
        ConstraintViolation<String> first = mock(ConstraintViolation.class);
        ConstraintViolation<String> second = mock(ConstraintViolation.class);
        when(first.getMessage()).thenReturn("zeta");
        when(second.getMessage()).thenReturn("alpha");
        ConstraintViolationException exception = new ConstraintViolationException(Set.of(first, second));
        HttpServletRequest request = request("/search");

        // when
        var response = handler.handleConstraintViolation(exception, request);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getDetail()).isEqualTo("alpha");
    }

    // <editor-fold desc="Helpers">
    private HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }
    // </editor-fold>
}
