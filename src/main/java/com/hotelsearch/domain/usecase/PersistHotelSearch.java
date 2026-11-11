package com.hotelsearch.domain.usecase;

import java.util.Objects;
import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.port.HotelSearchRepository;

/**
 * Caso de uso: persistir una búsqueda de hotel.
 * Utilizado por el consumidor de Kafka para guardar el evento en Oracle.
 */
public final class PersistHotelSearch {

    private final HotelSearchRepository repository;

    public PersistHotelSearch(HotelSearchRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
    }

    public void execute(HotelSearch search) {
        Objects.requireNonNull(search, "search no puede ser nulo");
        repository.save(search);
    }
}
