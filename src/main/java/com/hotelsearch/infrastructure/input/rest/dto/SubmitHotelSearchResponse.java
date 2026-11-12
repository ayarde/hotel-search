package com.hotelsearch.infrastructure.input.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta HTTP de un registro de búsqueda aceptado por Kafka.
 */
@Schema(name = "SubmitHotelSearchResponse", description = "Identificador de la búsqueda aceptada")
public record SubmitHotelSearchResponse(
    @Schema(
        description = "UUIDv4 generado por el servidor",
        example = "f47ac10b-58cc-4372-a567-0e02b2c3d479"
    )
    String searchId
) {
}
