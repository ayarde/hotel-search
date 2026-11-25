package com.hotelsearch.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.model.HotelSearchEvent;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import com.hotelsearch.application.port.HotelSearchPublisher;

/**
 * Caso de uso: registrar una búsqueda de hotel.
 * Valida el payload, genera searchId (UUIDv4), construye el evento inmutable
 * y lo publica a través del puerto HotelSearchPublisher.
 */
public final class SubmitHotelSearch {

    private final HotelSearchPublisher publisher;
    private final Clock clock;

    /**
     * @param publisher puerto de publicación del evento
     * @param clock reloj que define el día actual para la ventana de check-in
     */
    public SubmitHotelSearch(HotelSearchPublisher publisher, Clock clock) {
        this.publisher = Objects.requireNonNull(publisher, "publisher no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Valida la búsqueda, le asigna un searchId nuevo y publica el evento.
     * Retorna recién cuando el publisher confirma el envío.
     *
     * @param command datos de la búsqueda recibidos por HTTP
     * @return el searchId generado y la búsqueda publicada
     * @throws IllegalArgumentException si algún dato no cumple las reglas
     *         del dominio o la ventana de fechas
     */
    public Result execute(Command command) {
        Objects.requireNonNull(command, "command no puede ser nulo");

        HotelId hotelId = HotelId.of(command.hotelId());
        LocalDate checkInDate = command.checkIn();
        LocalDate checkOutDate = command.checkOut();
        Ages ages = Ages.of(command.ages());

        validateDates(checkInDate, checkOutDate);

        SearchId searchId = SearchId.generate();

        CheckIn checkIn = CheckIn.of(checkInDate);
        CheckOut checkOut = CheckOut.of(checkOutDate);

        HotelSearch search = new HotelSearch(searchId, hotelId, checkIn, checkOut, ages);
        HotelSearchEvent event = new HotelSearchEvent(searchId, hotelId, checkIn, checkOut, ages);

        publisher.publish(event);

        return new Result(searchId, search);
    }

    private void validateDates(LocalDate checkIn, LocalDate checkOut) {
        LocalDate today = LocalDate.now(clock);
        LocalDate minCheckIn = today.minusMonths(1);
        LocalDate maxCheckIn = today.plusMonths(1);

        if (checkIn.isBefore(minCheckIn) || checkIn.isAfter(maxCheckIn)) {
            throw new IllegalArgumentException(
                "checkIn debe estar dentro de la ventana inclusiva de un mes calendario "
                + "hacia atrás y hacia adelante desde hoy (" + minCheckIn + " a " + maxCheckIn + "). Valor: " + checkIn);
        }

        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException(
                "checkOut debe ser posterior a checkIn. checkIn=" + checkIn + ", checkOut=" + checkOut);
        }
    }

    public record Command(String hotelId, LocalDate checkIn, LocalDate checkOut, List<Integer> ages) {
    }

    public record Result(SearchId searchId, HotelSearch search) {
    }
}
