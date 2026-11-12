package com.hotelsearch.infrastructure.input.kafka;

import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.model.HotelSearchEvent;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;

/**
 * Traduce el evento deserializado de Kafka al modelo de dominio que se
 * persiste en Oracle.
 */
public class HotelSearchEventMapper {

    /**
     * Valida la estructura básica del evento y construye una búsqueda
     * inmutable con los value objects del dominio.
     *
     * <p>La ventana temporal de check-in no se vuelve a validar aquí: el
     * evento ya fue aceptado por la API y puede consumirse con demora.</p>
     *
     * @param event evento recibido desde Kafka
     * @return búsqueda lista para persistir
     * @throws InvalidHotelSearchEventException si el evento no cumple las
     *         invariantes de estructura del dominio
     */
    public HotelSearch toHotelSearch(HotelSearchEvent event) {
        if (event == null) {
            throw new InvalidHotelSearchEventException("El evento de búsqueda no puede ser nulo");
        }

        try {
            SearchId searchId = SearchId.of(event.searchId().value().toString());
            if (searchId.value().version() != 4) {
                throw new InvalidHotelSearchEventException("El searchId debe ser un UUIDv4");
            }

            HotelId hotelId = HotelId.of(event.hotelId().value().toString());
            CheckIn checkIn = CheckIn.of(event.checkIn().value());
            CheckOut checkOut = CheckOut.of(event.checkOut().value());
            if (!checkOut.value().isAfter(checkIn.value())) {
                throw new InvalidHotelSearchEventException(
                    "El checkOut debe ser posterior al checkIn");
            }

            Ages ages = Ages.of(event.ages().values());
            return new HotelSearch(searchId, hotelId, checkIn, checkOut, ages);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidHotelSearchEventException("El evento de búsqueda tiene una estructura inválida", e);
        }
    }
}
