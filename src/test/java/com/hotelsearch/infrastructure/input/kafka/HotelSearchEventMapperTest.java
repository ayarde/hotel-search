package com.hotelsearch.infrastructure.input.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hotelsearch.domain.model.HotelSearchEvent;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests de la traducción de eventos Kafka al modelo de dominio.
 */
class HotelSearchEventMapperTest {

    private final HotelSearchEventMapper mapper = new HotelSearchEventMapper();

    /**
     * Verifica que un evento válido se convierte en una búsqueda de dominio
     * con los mismos datos y una lista de edades inmutable.
     *
     * Condiciones:
     * - UUIDv4, fechas correctas y edades válidas
     *
     * Se espera:
     * - La búsqueda conserve todos los valores
     * - La lista de edades no pueda modificarse
     */
    @Test
    void toHotelSearch_ValidEvent_ReturnsImmutableDomainSearch() {
        // given
        SearchId searchId = SearchId.generate();
        HotelSearchEvent event = eventOf(searchId,
            LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 29));

        // when
        var search = mapper.toHotelSearch(event);

        // then
        assertThat(search.searchId()).as("El searchId debe conservarse").isEqualTo(searchId);
        assertThat(search.hotelId().value().toString())
            .as("El hotelId debe conservarse")
            .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(search.checkIn().value()).as("El checkIn debe conservarse")
            .isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(search.checkOut().value()).as("El checkOut debe conservarse")
            .isEqualTo(LocalDate.of(2026, 11, 29));
        assertThat(search.ages().values()).as("Las edades deben conservar el orden")
            .containsExactly(29, 28, 4, 3);
        assertThatThrownBy(() -> search.ages().values().add(50))
            .as("Las edades deben quedar defensivamente copiadas")
            .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Verifica que un evento nulo se rechace como error permanente.
     *
     * Condiciones:
     * - El listener entrega un evento nulo tras un error de deserialización
     *
     * Se espera:
     * - Se lance InvalidHotelSearchEventException
     */
    @Test
    void toHotelSearch_NullEvent_ThrowsInvalidEventException() {
        // when / then
        assertThatThrownBy(() -> mapper.toHotelSearch(null))
            .as("Un evento nulo no puede persistirse")
            .isInstanceOf(InvalidHotelSearchEventException.class)
            .hasMessageContaining("no puede ser nulo");
    }

    /**
     * Verifica que un searchId que no sea UUIDv4 se rechace antes de
     * persistir el evento.
     *
     * Condiciones:
     * - El evento contiene un UUID de versión 1
     *
     * Se espera:
     * - Se lance InvalidHotelSearchEventException
     */
    @Test
    void toHotelSearch_SearchIdNotV4_ThrowsInvalidEventException() {
        // given
        SearchId searchId = new SearchId(UUID.fromString("00000000-0000-1000-8000-000000000000"));
        HotelSearchEvent event = eventOf(searchId,
            LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 29));

        // when / then
        assertThatThrownBy(() -> mapper.toHotelSearch(event))
            .as("El consumer exige un searchId UUIDv4")
            .isInstanceOf(InvalidHotelSearchEventException.class)
            .hasMessageContaining("UUIDv4");
    }

    /**
     * Verifica que un checkOut anterior al checkIn se rechace como error
     * permanente.
     *
     * Condiciones:
     * - El evento contiene fechas invertidas
     *
     * Se espera:
     * - Se lance InvalidHotelSearchEventException
     */
    @Test
    void toHotelSearch_CheckOutNotAfterCheckIn_ThrowsInvalidEventException() {
        // given
        HotelSearchEvent event = eventOf(SearchId.generate(),
            LocalDate.of(2026, 11, 29), LocalDate.of(2026, 11, 10));

        // when / then
        assertThatThrownBy(() -> mapper.toHotelSearch(event))
            .as("El consumer rechaza fechas que contradicen la constraint")
            .isInstanceOf(InvalidHotelSearchEventException.class)
            .hasMessageContaining("checkOut");
    }

    /**
     * Verifica que una estructura incompleta se traduzca a un error
     * permanente con su causa original.
     *
     * Condiciones:
     * - El evento deserializado no contiene searchId
     *
     * Se espera:
     * - Se lance InvalidHotelSearchEventException
     */
    @Test
    void toHotelSearch_IncompleteStructure_ThrowsInvalidEventException() {
        // given
        HotelSearchEvent event = mock(HotelSearchEvent.class);
        when(event.searchId()).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> mapper.toHotelSearch(event))
            .as("Una estructura incompleta no debe llegar a Oracle")
            .isInstanceOf(InvalidHotelSearchEventException.class)
            .hasMessageContaining("estructura inválida")
            .hasCauseInstanceOf(NullPointerException.class);
    }

    /**
     * Verifica que un evento publicado antes del cambio de contrato, con un
     * hotelId UUID, se sigue deserializando y mapeando sin cambios.
     *
     * Condiciones:
     * - JSON con la forma que produce el producer: hotelId como objeto value
     * - El hotelId es un UUID, como en los mensajes previos a la Fase 10
     *
     * Se espera:
     * - El evento se deserialice y el hotelId conserve el UUID como texto
     */
    @Test
    void toHotelSearch_LegacyUuidHotelIdJson_Deserializes() {
        // given
        String json = """
            {"searchId":{"value":"f47ac10b-58cc-4372-a567-0e02b2c3d479"},
             "hotelId":{"value":"550e8400-e29b-41d4-a716-446655440000"},
             "checkIn":{"value":"2026-11-09"},
             "checkOut":{"value":"2026-11-29"},
             "ages":{"values":[29,28]}}
            """;
        HotelSearchEvent event = JsonMapper.builder().build().readValue(json, HotelSearchEvent.class);

        // when
        var search = mapper.toHotelSearch(event);

        // then
        assertThat(search.hotelId().value())
            .as("El hotelId UUID de un mensaje previo debe conservarse como texto")
            .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    // <editor-fold desc="Helpers">
    private HotelSearchEvent eventOf(SearchId searchId, LocalDate checkIn, LocalDate checkOut) {
        return new HotelSearchEvent(
            searchId,
            HotelId.of("550e8400-e29b-41d4-a716-446655440000"),
            CheckIn.of(checkIn),
            CheckOut.of(checkOut),
            Ages.of(List.of(29, 28, 4, 3)));
    }
    // </editor-fold>
}
