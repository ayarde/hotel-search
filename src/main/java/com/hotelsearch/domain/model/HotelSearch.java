package com.hotelsearch.domain.model;

import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import java.util.Objects;

/**
 * Búsqueda de hotel. Modelo de dominio inmutable.
 */
public record HotelSearch(SearchId searchId, HotelId hotelId, CheckIn checkIn,
                          CheckOut checkOut, Ages ages) {

    public HotelSearch {
        Objects.requireNonNull(searchId, "searchId no puede ser nulo");
        Objects.requireNonNull(hotelId, "hotelId no puede ser nulo");
        Objects.requireNonNull(checkIn, "checkIn no puede ser nulo");
        Objects.requireNonNull(checkOut, "checkOut no puede ser nulo");
        Objects.requireNonNull(ages, "ages no puede ser nulo");
    }
}
