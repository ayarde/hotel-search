package com.hotelsearch.infrastructure.input.kafka;

import java.io.Serial;

/**
 * Error permanente de estructura o validación de un evento recibido desde
 * Kafka. Los mensajes con este error se envían directamente al DLT.
 */
public class InvalidHotelSearchEventException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Crea el error con un mensaje que identifica la causa funcional.
     *
     * @param message descripción del dato inválido
     */
    public InvalidHotelSearchEventException(String message) {
        super(message);
    }

    /**
     * Crea el error conservando la causa original del deserializador o de
     * las reglas de validación.
     *
     * @param message descripción del dato inválido
     * @param cause causa original
     */
    public InvalidHotelSearchEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
