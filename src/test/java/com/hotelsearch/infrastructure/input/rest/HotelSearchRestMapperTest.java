package com.hotelsearch.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hotelsearch.application.CountHotelSearch;
import com.hotelsearch.application.SubmitHotelSearch;
import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import com.hotelsearch.infrastructure.input.rest.dto.HotelSearchRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests del mapper entre el contrato HTTP y los casos de uso.
 */
class HotelSearchRestMapperTest {

    private final HotelSearchRestMapper mapper = new HotelSearchRestMapper();

    /**
     * Verifica que un request válido se convierte a fechas internas y conserva
     * el orden de las edades.
     *
     * Condiciones:
     * - Las fechas HTTP usan el formato dd/MM/yyyy
     * - La lista de edades contiene cuatro elementos
     *
     * Se espera:
     * - El comando contenga dos LocalDate válidas
     * - El orden de las edades se mantiene
     */
    @Test
    void toCommand_ValidRequest_MapsDatesAndAges() {
        // given
        HotelSearchRequest request = validRequest();

        // when
        SubmitHotelSearch.Command command = mapper.toCommand(request);

        // then
        assertThat(command.hotelId()).isEqualTo(request.hotelId());
        assertThat(command.checkIn()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(command.checkOut()).isEqualTo(LocalDate.of(2026, 11, 29));
        assertThat(command.ages()).containsExactly(29, 28, 4, 3);
    }

    /**
     * Verifica que un request con ages nulas conserva el valor nulo para que
     * Bean Validation pueda producir un mensaje específico.
     *
     * Condiciones:
     * - El request se construye con la lista de edades nula
     *
     * Se espera:
     * - El mapper no intente convertir la lista
     */
    @Test
    void toCommand_NullAges_KeepsNullForValidation() {
        // given
        HotelSearchRequest request = new HotelSearchRequest(
            "550e8400-e29b-41d4-a716-446655440000", "09/11/2026", "29/11/2026", null);

        // when
        SubmitHotelSearch.Command command = mapper.toCommand(request);

        // then
        assertThat(command.ages()).isNull();
    }

    /**
     * Verifica que una fecha con formato distinto de dd/MM/yyyy se rechaza.
     *
     * Condiciones:
     * - El checkIn usa el formato ISO
     *
     * Se espera:
     * - Se lance IllegalArgumentException con un mensaje útil
     */
    @Test
    void toCommand_InvalidDateFormat_ThrowsIllegalArgumentException() {
        // given
        HotelSearchRequest request = new HotelSearchRequest(
            "550e8400-e29b-41d4-a716-446655440000", "2026-11-09", "29/11/2026", List.of(29));

        // when / then
        assertThatThrownBy(() -> mapper.toCommand(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("checkIn")
            .hasMessageContaining("dd/MM/yyyy");
    }

    /**
     * Verifica que una fecha con el formato correcto pero un calendario
     * inválido se rechaza.
     *
     * Condiciones:
     * - El checkIn corresponde al 31 de febrero
     *
     * Se espera:
     * - Se lance IllegalArgumentException
     */
    @Test
    void toCommand_InvalidCalendarDate_ThrowsIllegalArgumentException() {
        // given
        HotelSearchRequest request = new HotelSearchRequest(
            "550e8400-e29b-41d4-a716-446655440000", "31/02/2026", "29/11/2026", List.of(29));

        // when / then
        assertThatThrownBy(() -> mapper.toCommand(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fecha válida");
    }

    /**
     * Verifica que el resultado de registro se convierta en la respuesta
     * HTTP con el identificador generado.
     *
     * Condiciones:
     * - El caso de uso devuelve un searchId UUIDv4
     *
     * Se espera:
     * - La respuesta contenga el mismo identificador
     */
    @Test
    void toResponse_SubmitResult_ReturnsSearchId() {
        // given
        SearchId searchId = SearchId.of("f47ac10b-58cc-4372-a567-0e02b2c3d479");
        SubmitHotelSearch.Result result = new SubmitHotelSearch.Result(searchId, validSearch(searchId));

        // when
        var response = mapper.toResponse(result);

        // then
        assertThat(response.searchId()).isEqualTo(searchId.toString());
    }

    /**
     * Verifica que el resultado de conteo incluya la búsqueda completa, las
     * fechas en el formato HTTP y el conteo de equivalentes.
     *
     * Condiciones:
     * - El caso de uso devuelve una búsqueda con dos equivalentes
     *
     * Se espera:
     * - Todos los campos de la respuesta estén mapeados
     */
    @Test
    void toResponse_CountResult_MapsSearchAndCount() {
        // given
        SearchId searchId = SearchId.of("f47ac10b-58cc-4372-a567-0e02b2c3d479");
        CountHotelSearch.Result result = new CountHotelSearch.Result(validSearch(searchId), 2);

        // when
        var response = mapper.toResponse(result);

        // then
        assertThat(response.searchId()).isEqualTo(searchId.toString());
        assertThat(response.hotelId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(response.checkIn()).isEqualTo("09/11/2026");
        assertThat(response.checkOut()).isEqualTo("29/11/2026");
        assertThat(response.ages()).containsExactly(29, 28, 4, 3);
        assertThat(response.searchCount()).isEqualTo(2);
    }

    // <editor-fold desc="Helpers">
    private HotelSearchRequest validRequest() {
        return new HotelSearchRequest(
            "550e8400-e29b-41d4-a716-446655440000",
            "09/11/2026",
            "29/11/2026",
            List.of(29, 28, 4, 3));
    }

    private HotelSearch validSearch(SearchId searchId) {
        return new HotelSearch(
            searchId,
            HotelId.of("550e8400-e29b-41d4-a716-446655440000"),
            CheckIn.of(LocalDate.of(2026, 11, 9)),
            CheckOut.of(LocalDate.of(2026, 11, 29)),
            Ages.of(List.of(29, 28, 4, 3)));
    }
    // </editor-fold>
}
