package com.hotelsearch.domain.port;

import java.util.Optional;
import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.valueobject.SearchId;

/**
 * Puerto de persistencia de búsquedas de hotel.
 * Implementado por la capa de infraestructura (Oracle JDBC / Flyway).
 */
public interface HotelSearchRepository {

    /**
     * Guarda una búsqueda. Si ya existe una con el mismo searchId (PK)
     * se considera duplicado y se trata seg\u00fan la implementación.
     */
    void save(HotelSearch search);

    /**
     * Busca una búsqueda por su searchId.
     */
    Optional<HotelSearch> findById(SearchId searchId);

    /**
     * Cuenta búsquedas equivalentes a la dada, excluyendo la con el
     * searchId indicado. Devuelve cero si no hay otras iguales.
     */
    int countEquivalents(HotelSearch search, SearchId excludeSearchId);
}
