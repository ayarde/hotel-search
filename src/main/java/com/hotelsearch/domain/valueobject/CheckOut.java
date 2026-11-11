package com.hotelsearch.domain.valueobject;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Fecha de check-out. Inmutable.
 */
public record CheckOut(LocalDate value) {

    public CheckOut {
        Objects.requireNonNull(value, "checkOut no puede ser nulo");
    }

    public static CheckOut of(LocalDate value) {
        return new CheckOut(value);
    }
}
