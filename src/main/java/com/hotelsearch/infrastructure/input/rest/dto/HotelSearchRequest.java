package com.hotelsearch.infrastructure.input.rest.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Payload HTTP de una búsqueda de hotel.
 *
 * <p>Las fechas se reciben como texto para poder aplicar de forma estricta
 * el formato público {@code dd/MM/yyyy}. El adaptador REST las convierte a
 * {@code LocalDate} antes de invocar el caso de uso.</p>
 */
@Schema(name = "HotelSearchRequest", description = "Búsqueda de hotel que se registrará")
public record HotelSearchRequest(
    @NotBlank(message = "hotelId es obligatorio")
    @Pattern(
        regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        message = "hotelId debe ser un UUID canónico válido"
    )
    @Schema(
        description = "UUID canónico del hotel",
        example = "550e8400-e29b-41d4-a716-446655440000",
        pattern = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )
    String hotelId,

    @NotBlank(message = "checkIn es obligatorio")
    @Schema(
        description = "Fecha de entrada",
        type = "string",
        pattern = "^\\d{2}/\\d{2}/\\d{4}$",
        example = "09/11/2026"
    )
    String checkIn,

    @NotBlank(message = "checkOut es obligatorio")
    @Schema(
        description = "Fecha de salida",
        type = "string",
        pattern = "^\\d{2}/\\d{2}/\\d{4}$",
        example = "29/11/2026"
    )
    String checkOut,

    @NotNull(message = "ages es obligatorio")
    @Size(min = 1, max = 50, message = "ages debe contener entre 1 y 50 elementos")
    @ArraySchema(
        minItems = 1,
        maxItems = 50,
        schema = @Schema(type = "integer", minimum = "0")
    )
    List<@NotNull(message = "ages no puede contener elementos nulos")
        @Min(value = 0, message = "cada edad debe ser mayor o igual que 0") Integer> ages
) {

    /**
     * Protege la lista de edades frente a modificaciones posteriores a la
     * deserialización del request.
     */
    public HotelSearchRequest {
        if (ages != null) {
            ages = Collections.unmodifiableList(new ArrayList<>(ages));
        }
    }
}
