package com.hotelsearch.domain.valueobject;

import java.time.LocalDate;

/**
 * Fecha de check-out. Inmutable.
 */
public record CheckOut(LocalDate value) {

    public CheckOut {
        if (value == null) {
            throw new IllegalArgumentException("checkOut no puede ser nulo");
        }
    }

    /**
     * @param value fecha, no nula
     * @return la fecha validada
     */
    public static CheckOut of(LocalDate value) {
        return new CheckOut(value);
    }
}
