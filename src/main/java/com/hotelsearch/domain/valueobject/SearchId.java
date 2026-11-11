package com.hotelsearch.domain.valueobject;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identificador de búsqueda generado por el servidor como UUIDv4.
 * Inmutable.
 */
public record SearchId(UUID value) {

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    public SearchId {
        Objects.requireNonNull(value, "searchId no puede ser nulo");
    }

    /**
     * Genera un nuevo searchId como UUIDv4 (servidor).
     */
    public static SearchId generate() {
        return new SearchId(UUID.randomUUID());
    }

    public static SearchId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("searchId no puede ser nulo o vacío");
        }
        if (!UUID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("searchId debe ser un UUID válido: " + value);
        }
        return new SearchId(UUID.fromString(value));
    }

    public static SearchId of(UUID value) {
        return new SearchId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
