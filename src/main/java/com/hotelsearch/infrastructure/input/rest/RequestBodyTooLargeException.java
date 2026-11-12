package com.hotelsearch.infrastructure.input.rest;

/**
 * Error interno para un body HTTP que supera el límite permitido.
 */
public final class RequestBodyTooLargeException extends RuntimeException {

    /**
     * Crea el error con el límite permitido.
     *
     * @param maxBodyBytes límite máximo en bytes
     */
    public RequestBodyTooLargeException(int maxBodyBytes) {
        super("El body de la solicitud no puede superar " + maxBodyBytes + " bytes");
    }
}
