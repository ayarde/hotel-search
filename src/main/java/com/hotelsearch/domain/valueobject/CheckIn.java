package com.hotelsearch.domain.valueobject;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Fecha de check-in. Inmutable.
 */
public record CheckIn(LocalDate value) {

    public CheckIn {
        Objects.requireNonNull(value, "checkIn no puede ser nulo");
    }

    public static CheckIn of(LocalDate value) {
        return new CheckIn(value);
    }
}
