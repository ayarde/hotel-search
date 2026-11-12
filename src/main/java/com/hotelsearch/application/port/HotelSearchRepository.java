package com.hotelsearch.application.port;

import java.util.Optional;
import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.model.HotelSearchWithCount;
import com.hotelsearch.domain.valueobject.SearchId;

/**
 * Puerto de persistencia de búsquedas de hotel.
 * Implementado por la capa de infraestructura (Oracle JDBC / Flyway).
 */
public interface HotelSearchRepository {

    /**
     * Guarda una búsqueda. Una violación de la PK search_id se considera
     * duplicado (redelivery at-least-once) y se descarta; cualquier otro
     * error SQL se propaga.
     */
    void save(HotelSearch search);

    /**
     * Recupera una búsqueda por su searchId junto con la cantidad de
     * búsquedas equivalentes, excluyendo la propia. Devuelve vacío si el
     * searchId no existe, de modo que un ID inexistente se distinga de un
     * conteo en cero. La búsqueda y el conteo se resuelven con una sola
     * sentencia SQL.
     */
    Optional<HotelSearchWithCount> findByIdWithCount(SearchId searchId);
}
