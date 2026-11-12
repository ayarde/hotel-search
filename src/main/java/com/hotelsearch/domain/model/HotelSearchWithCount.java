package com.hotelsearch.domain.model;

import java.util.Objects;

/**
 * Resultado de la consulta única de búsqueda y conteo: la búsqueda
 * recuperada junto con la cantidad de búsquedas equivalentes,
 * excluyendo la propia. Proyecta en un solo acceso la sentencia SQL
 * definida para el caso de uso de conteo.
 */
public record HotelSearchWithCount(HotelSearch search, int equivalentsCount) {

    public HotelSearchWithCount {
        Objects.requireNonNull(search, "search no puede ser nulo");
    }
}
