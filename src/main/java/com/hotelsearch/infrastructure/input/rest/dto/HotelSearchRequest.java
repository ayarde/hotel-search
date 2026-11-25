package com.hotelsearch.infrastructure.input.rest.dto;

import com.hotelsearch.domain.valueobject.HotelId;
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
    @Size(
        max = HotelId.MAX_LENGTH,
        message = "hotelId no puede superar " + HotelId.MAX_LENGTH + " caracteres"
    )
    @Pattern(regexp = HOTEL_ID_PATTERN, message = "hotelId no permite espacios en blanco")
    @Schema(
        description = "Identificador del hotel definido por el proveedor. Texto sin espacios "
            + "en blanco; se compara de forma exacta",
        example = "1234aBc",
        minLength = 1,
        maxLength = HotelId.MAX_LENGTH,
        pattern = HOTEL_ID_PATTERN
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
     * Caracteres que {@link HotelId} considera espacio en blanco, listados
     * de forma explícita: tabs y saltos de línea, separadores de control
     * U+001C a U+001F y los separadores Unicode de espacio, línea y
     * párrafo. No se usa {@code \\p{javaWhitespace}} porque OpenAPI publica
     * el patrón con sintaxis ECMAScript y los clientes no lo entenderían.
     * {@code HotelSearchRequestTest} verifica que coincide con el dominio.
     */
    public static final String HOTEL_ID_PATTERN =
        "^[^\\u0009-\\u000D\\u001C-\\u0020\\u00A0\\u1680\\u2000-\\u200A"
            + "\\u2028\\u2029\\u202F\\u205F\\u3000]*$";

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
