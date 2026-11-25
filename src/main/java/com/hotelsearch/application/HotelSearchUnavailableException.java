package com.hotelsearch.application;

import java.io.Serial;

/**
 * Error de aplicación que representa la indisponibilidad de Kafka durante
 * la publicación de una búsqueda.
 */
public final class HotelSearchUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Crea el error con un mensaje descriptivo.
     *
     * @param message descripción de la indisponibilidad
     */
    public HotelSearchUnavailableException(String message) {
        super(message);
    }

    /**
     * Crea el error con un mensaje descriptivo y la causa original.
     *
     * @param message descripción de la indisponibilidad
     * @param cause causa original informada por Kafka
     */
    public HotelSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
