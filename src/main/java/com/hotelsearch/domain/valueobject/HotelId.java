package com.hotelsearch.domain.valueobject;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identificador de hotel. Debe ser un UUID válido, canónico, de 36
 * caracteres. No se permiten espacios ni normalización mediante trim
 * silencioso. Inmutable.
 */
public record HotelId(UUID value) {

    private static final Pattern CANONICAL_UUID_PATTERN = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    );

    public HotelId {
        Objects.requireNonNull(value, "hotelId no puede ser nulo");
    }

    public static HotelId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("hotelId no puede ser nulo o vacío");
        }
        if (value.contains(" ")) {
            throw new IllegalArgumentException("hotelId no permite espacios: " + value);
        }
        if (value.length() != 36) {
            throw new IllegalArgumentException("hotelId debe tener 36 caracteres: " + value);
        }
        if (!CANONICAL_UUID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("hotelId debe ser un UUID canónico (minúsculas): " + value);
        }
        return new HotelId(UUID.fromString(value));
    }

    public static HotelId of(UUID value) {
        return new HotelId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
