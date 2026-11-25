package com.hotelsearch.domain.valueobject;

import java.time.LocalDate;

/**
 * Fecha de check-in. Inmutable.
 */
public record CheckIn(LocalDate value) {

    public CheckIn {
        if (value == null) {
            throw new IllegalArgumentException("checkIn no puede ser nulo");
        }
    }

    /**
     * @param value fecha, no nula
     * @return la fecha validada
     */
    public static CheckIn of(LocalDate value) {
        return new CheckIn(value);
    }
}
