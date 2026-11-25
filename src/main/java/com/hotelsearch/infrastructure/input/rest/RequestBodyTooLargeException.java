package com.hotelsearch.infrastructure.input.rest;

import java.io.Serial;

/**
 * Error interno para un body HTTP que supera el límite permitido.
 */
public final class RequestBodyTooLargeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Crea el error con el límite permitido.
     *
     * @param maxBodyBytes límite máximo en bytes
     */
    public RequestBodyTooLargeException(int maxBodyBytes) {
        super("El body de la solicitud no puede superar " + maxBodyBytes + " bytes");
    }
}
