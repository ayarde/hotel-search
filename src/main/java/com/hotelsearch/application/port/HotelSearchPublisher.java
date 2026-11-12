package com.hotelsearch.application.port;

import com.hotelsearch.domain.model.HotelSearchEvent;

/**
 * Puerto de publicación de eventos de búsqueda de hotel.
 * Implementado por la capa de infraestructura (Kafka producer).
 */
public interface HotelSearchPublisher {

    /**
     * Publica un evento de búsqueda. El llamador espera la confirmación
     * (ACK) del broker antes de considerar la operación exitosa.
     */
    void publish(HotelSearchEvent event);
}
