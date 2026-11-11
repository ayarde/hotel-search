package com.hotelsearch.domain.model;

import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import java.util.Objects;

/**
 * Evento de búsqueda de hotel. Inmutable. Se publica en Kafka.
 */
public record HotelSearchEvent(SearchId searchId, HotelId hotelId, CheckIn checkIn,
                               CheckOut checkOut, Ages ages) {

    public HotelSearchEvent {
        Objects.requireNonNull(searchId, "searchId no puede ser nulo");
        Objects.requireNonNull(hotelId, "hotelId no puede ser nulo");
        Objects.requireNonNull(checkIn, "checkIn no puede ser nulo");
        Objects.requireNonNull(checkOut, "checkOut no puede ser nulo");
        Objects.requireNonNull(ages, "ages no puede ser nulo");
    }
}
