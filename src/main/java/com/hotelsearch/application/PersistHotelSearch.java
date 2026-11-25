package com.hotelsearch.application;

import com.hotelsearch.application.port.HotelSearchRepository;
import com.hotelsearch.domain.model.HotelSearch;
import java.util.Objects;

/**
 * Caso de uso: persistir una búsqueda de hotel.
 * Utilizado por el consumidor de Kafka para guardar el evento en Oracle.
 *
 * <p>No declara transacciones: la demarcación pertenece al adaptador que
 * implementa {@link HotelSearchRepository}, así la capa de aplicación no
 * depende de Spring.</p>
 */
public class PersistHotelSearch {

    private final HotelSearchRepository repository;

    public PersistHotelSearch(HotelSearchRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
    }

    public void execute(HotelSearch search) {
        Objects.requireNonNull(search, "search no puede ser nulo");
        repository.save(search);
    }
}
