package com.hotelsearch.infrastructure.input.rest;

import com.hotelsearch.application.CountHotelSearch;
import com.hotelsearch.application.SubmitHotelSearch;
import com.hotelsearch.infrastructure.input.rest.dto.HotelSearchCountResponse;
import com.hotelsearch.infrastructure.input.rest.dto.HotelSearchRequest;
import com.hotelsearch.infrastructure.input.rest.dto.SubmitHotelSearchResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Traduce entre el contrato HTTP y los casos de uso de la aplicación.
 */
@Component
public class HotelSearchRestMapper {

    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$");
    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
        .appendPattern("dd/MM/uuuu")
        .toFormatter(Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT);

    /**
     * Convierte un request HTTP en el comando del caso de uso de registro.
     *
     * @param request payload HTTP validado
     * @return comando con fechas internas {@code LocalDate}
     */
    public SubmitHotelSearch.Command toCommand(HotelSearchRequest request) {
        return new SubmitHotelSearch.Command(
            request.hotelId(),
            parseDate(request.checkIn(), "checkIn"),
            parseDate(request.checkOut(), "checkOut"),
            request.ages());
    }

    /**
     * Convierte el resultado del registro en la respuesta HTTP 202.
     *
     * @param result resultado del caso de uso
     * @return respuesta con el identificador generado
     */
    public SubmitHotelSearchResponse toResponse(SubmitHotelSearch.Result result) {
        return new SubmitHotelSearchResponse(result.searchId().toString());
    }

    /**
     * Convierte el resultado de conteo en la respuesta HTTP.
     *
     * @param result resultado del caso de uso
     * @return respuesta con la búsqueda y su cantidad de equivalentes
     */
    public HotelSearchCountResponse toResponse(CountHotelSearch.Result result) {
        var search = result.search();
        return new HotelSearchCountResponse(
            search.searchId().toString(),
            search.hotelId().toString(),
            formatDate(search.checkIn().value()),
            formatDate(search.checkOut().value()),
            search.ages().values(),
            result.equivalentsCount());
    }

    private LocalDate parseDate(String value, String field) {
        if (!DATE_PATTERN.matcher(value).matches()) {
            throw invalidDate(field, value);
        }
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw invalidDate(field, value);
        }
    }

    private IllegalArgumentException invalidDate(String field, String value) {
        return new IllegalArgumentException(
            field + " debe usar el formato dd/MM/yyyy y representar una fecha válida: " + value);
    }

    private String formatDate(LocalDate value) {
        return DATE_FORMATTER.format(value);
    }
}
