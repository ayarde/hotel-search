package com.hotelsearch.domain.model;

import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import java.time.LocalDate;
import java.util.List;

/**
 * Eventos de búsqueda compartidos por los tests de los adaptadores Kafka.
 *
 * <p>Las fechas son fijas: estos tests no pasan por la validación temporal
 * de la API, solo serializan, publican o mapean el evento.</p>
 */
public final class HotelSearchEventFixtures {

    private HotelSearchEventFixtures() {
    }

    /**
     * Evento válido con un hotelId de texto libre y cuatro huéspedes.
     *
     * @return evento de búsqueda válido
     */
    public static HotelSearchEvent validEvent() {
        return new HotelSearchEvent(
            SearchId.of("f47ac10b-58cc-4372-a567-0e02b2c3d479"),
            HotelId.of("1234aBc"),
            CheckIn.of(LocalDate.of(2026, 11, 9)),
            CheckOut.of(LocalDate.of(2026, 11, 29)),
            Ages.of(List.of(29, 28, 4, 3)));
    }
}
