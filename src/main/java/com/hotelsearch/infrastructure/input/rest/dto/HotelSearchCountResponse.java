package com.hotelsearch.infrastructure.input.rest.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Respuesta HTTP con una búsqueda persistida y la cantidad de búsquedas
 * equivalentes, excluyendo la propia.
 */
@Schema(name = "HotelSearchCountResponse", description = "Búsqueda y cantidad de equivalentes")
public record HotelSearchCountResponse(
    @Schema(description = "UUID de la búsqueda consultada")
    String searchId,

    @Schema(description = "Identificador del hotel tal como se recibió", example = "1234aBc")
    String hotelId,

    @Schema(
        description = "Fecha de entrada",
        type = "string",
        pattern = "^\\d{2}/\\d{2}/\\d{4}$",
        example = "09/11/2026"
    )
    String checkIn,

    @Schema(
        description = "Fecha de salida",
        type = "string",
        pattern = "^\\d{2}/\\d{2}/\\d{4}$",
        example = "29/11/2026"
    )
    String checkOut,

    @Schema(description = "Edades en el orden que forma parte de la identidad de la búsqueda")
    @ArraySchema(schema = @Schema(type = "integer", minimum = "0"))
    List<Integer> ages,

    @Schema(
        description = "Cantidad de otras búsquedas equivalentes",
        example = "2",
        minimum = "0"
    )
    int searchCount
) {

    /**
     * Expone una copia inmutable de las edades.
     */
    public HotelSearchCountResponse {
        ages = List.copyOf(ages);
    }
}
