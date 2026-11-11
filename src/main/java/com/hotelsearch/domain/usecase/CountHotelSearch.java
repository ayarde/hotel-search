package com.hotelsearch.domain.usecase;

import java.util.Objects;
import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.valueobject.SearchId;
import com.hotelsearch.domain.port.HotelSearchRepository;

/**
 * Caso de uso: consultar una búsqueda y contar búsquedas equivalentes.
 */
public final class CountHotelSearch {

    private final HotelSearchRepository repository;

    public CountHotelSearch(HotelSearchRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
    }

    public Result execute(Query query) {
        Objects.requireNonNull(query, "query no puede ser nulo");

        SearchId searchId = SearchId.of(query.searchId());

        HotelSearch search = repository.findById(searchId)
            .orElseThrow(() -> new IllegalArgumentException("searchId no existe: " + searchId));

        int count = repository.countEquivalents(search, searchId);

        return new Result(search, count);
    }

    public record Query(String searchId) {
    }

    public record Result(HotelSearch search, int equivalentsCount) {
    }
}
