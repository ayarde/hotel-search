package com.hotelsearch.infrastructure.input.rest;

import com.hotelsearch.application.HotelSearchUnavailableException;
import com.hotelsearch.application.SearchNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Traduce los errores de la aplicación y del contrato HTTP a Problem
 * Details RFC 9457.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
// Un método por tipo de excepción es la forma que define Spring MVC para
// @ExceptionHandler. Dividir la clase repartiría el contrato de errores HTTP.
@SuppressWarnings("PMD.TooManyMethods")
public class RestExceptionHandler {

    private static final String INVALID_REQUEST_TITLE = "Solicitud inválida";

    private static final String GLOBAL_ERRORS_KEY = "_global";

    /**
     * Representa un request inválido validado por Bean Validation.
     *
     * @param exception excepción de validación
     * @param request request que produjo el error
     * @return detalle con los errores por campo
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        BindingResult bindingResult = exception.getBindingResult();
        // Primero los errores por campo y después los globales, en el orden
        // en que los informa Bean Validation.
        Map<String, List<String>> errors = Stream.concat(
                bindingResult.getFieldErrors().stream()
                    .map(error -> Map.entry(error.getField(), messageOf(error))),
                bindingResult.getGlobalErrors().stream()
                    .map(error -> Map.entry(GLOBAL_ERRORS_KEY, messageOf(error))))
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                LinkedHashMap::new,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        ProblemDetail problem = problem(
            HttpStatus.BAD_REQUEST,
            "urn:hotel-search:problem:invalid-request",
            INVALID_REQUEST_TITLE,
            "El request contiene valores que no cumplen el contrato.",
            request);
        problem.setProperty("errors", errors);
        return response(HttpStatus.BAD_REQUEST, problem);
    }

    /**
     * Representa un body JSON mal formado o con propiedades desconocidas.
     *
     * @param exception excepción de lectura del body
     * @param request request que produjo el error
     * @return detalle con la causa útil
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        Throwable cause = exception.getMostSpecificCause();
        String detail = "El cuerpo de la solicitud no contiene un JSON válido.";
        if (cause instanceof UnrecognizedPropertyException unknownProperty) {
            detail = "Propiedad JSON desconocida: " + unknownProperty.getPropertyName();
        }

        return response(HttpStatus.BAD_REQUEST, problem(
            HttpStatus.BAD_REQUEST,
            "urn:hotel-search:problem:invalid-json",
            INVALID_REQUEST_TITLE,
            detail,
            request));
    }

    /**
     * Representa un parámetro obligatorio ausente.
     *
     * @param exception excepción del parámetro ausente
     * @param request request que produjo el error
     * @return detalle del error
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(
        MissingServletRequestParameterException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, problem(
            HttpStatus.BAD_REQUEST,
            "urn:hotel-search:problem:missing-parameter",
            INVALID_REQUEST_TITLE,
            "El parámetro '" + exception.getParameterName() + "' es obligatorio",
            request));
    }

    /**
     * Representa una violación de una restricción de Bean Validation.
     *
     * @param exception excepción de restricción
     * @param request request que produjo el error
     * @return detalle con los mensajes de las restricciones violadas
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
        ConstraintViolationException exception,
        HttpServletRequest request
    ) {
        String detail = exception.getConstraintViolations().stream()
            .map(violation -> violation.getMessage())
            .sorted()
            .findFirst()
            .orElse("El request contiene valores inválidos");

        return response(HttpStatus.BAD_REQUEST, problem(
            HttpStatus.BAD_REQUEST,
            "urn:hotel-search:problem:invalid-request",
            INVALID_REQUEST_TITLE,
            detail,
            request));
    }

    /**
     * Representa un body que supera el límite permitido.
     *
     * @param exception excepción del límite de body
     * @param request request que produjo el error
     * @return detalle del error
     */
    @ExceptionHandler(RequestBodyTooLargeException.class)
    public ResponseEntity<ProblemDetail> handleBodyTooLarge(
        RequestBodyTooLargeException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, problem(
            HttpStatus.BAD_REQUEST,
            "urn:hotel-search:problem:body-too-large",
            INVALID_REQUEST_TITLE,
            exception.getMessage(),
            request));
    }

    /**
     * Representa una validación fallida en el dominio o en el mapper HTTP.
     *
     * @param exception excepción de validación
     * @param request request que produjo el error
     * @return detalle del error
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
        IllegalArgumentException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, problem(
            HttpStatus.BAD_REQUEST,
            "urn:hotel-search:problem:invalid-request",
            INVALID_REQUEST_TITLE,
            exception.getMessage(),
            request));
    }

    /**
     * Representa una búsqueda válida que no existe en Oracle.
     *
     * @param exception excepción de búsqueda inexistente
     * @param request request que produjo el error
     * @return detalle 404
     */
    @ExceptionHandler(SearchNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleSearchNotFound(
        SearchNotFoundException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, problem(
            HttpStatus.NOT_FOUND,
            "urn:hotel-search:problem:search-not-found",
            "Búsqueda no encontrada",
            exception.getMessage(),
            request));
    }

    /**
     * Representa la indisponibilidad de Kafka.
     *
     * @param exception excepción de publicación
     * @param request request que produjo el error
     * @return detalle 503
     */
    @ExceptionHandler(HotelSearchUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleKafkaUnavailable(
        HotelSearchUnavailableException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, problem(
            HttpStatus.SERVICE_UNAVAILABLE,
            "urn:hotel-search:problem:kafka-unavailable",
            "Kafka no disponible",
            exception.getMessage(),
            request));
    }

    private ProblemDetail problem(
        HttpStatus status,
        String type,
        String title,
        String detail,
        HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    private ResponseEntity<ProblemDetail> response(HttpStatus status, ProblemDetail problem) {
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }

    private String messageOf(ObjectError error) {
        String message = error.getDefaultMessage();
        return message == null || message.isBlank() ? "valor inválido" : message;
    }
}
