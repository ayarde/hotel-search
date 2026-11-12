package com.hotelsearch.domain.valueobject;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Fecha de check-out. Inmutable.
 */
public record CheckOut(LocalDate value) {

    public CheckOut {
        if (value == null) {
            throw new IllegalArgumentException("checkOut no puede ser nulo");
        }
    }

    public static CheckOut of(LocalDate value) {
        return new CheckOut(value);
    }
}
