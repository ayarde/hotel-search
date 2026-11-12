package com.hotelsearch.application;

import java.util.Objects;
import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.model.HotelSearchWithCount;
import com.hotelsearch.domain.valueobject.SearchId;
import com.hotelsearch.application.port.HotelSearchRepository;

/**
 * Caso de uso: consultar una búsqueda y contar búsquedas equivalentes.
 *
 * <p>Delega en el puerto la sentencia única que devuelve la búsqueda y su
 * conteo en un solo acceso a Oracle. La búsqueda consultada nunca se
 * cuenta a sí misma; un searchId inexistente se distingue de un conteo
 * en cero mediante un error específico.</p>
 */
public final class CountHotelSearch {

    private final HotelSearchRepository repository;

    public CountHotelSearch(HotelSearchRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
    }

    public Result execute(Query query) {
        Objects.requireNonNull(query, "query no puede ser nulo");

        SearchId searchId = SearchId.of(query.searchId());

        HotelSearchWithCount found = repository.findByIdWithCount(searchId)
            .orElseThrow(() -> new IllegalArgumentException("searchId no existe: " + searchId));

        return new Result(found.search(), found.equivalentsCount());
    }

    public record Query(String searchId) {
    }

    public record Result(HotelSearch search, int equivalentsCount) {
    }
}
