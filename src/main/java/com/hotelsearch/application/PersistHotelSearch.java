package com.hotelsearch.application;

import com.hotelsearch.application.port.HotelSearchRepository;
import com.hotelsearch.domain.model.HotelSearch;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: persistir una búsqueda de hotel.
 * Utilizado por el consumidor de Kafka para guardar el evento en Oracle.
 */
public class PersistHotelSearch {

    private final HotelSearchRepository repository;

    public PersistHotelSearch(HotelSearchRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
    }

    @Transactional
    public void execute(HotelSearch search) {
        Objects.requireNonNull(search, "search no puede ser nulo");
        repository.save(search);
    }
}
