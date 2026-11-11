package com.hotelsearch.domain.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import com.hotelsearch.domain.model.HotelSearchEvent;
import com.hotelsearch.domain.port.HotelSearchPublisher;

class SubmitHotelSearchTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-11-10T12:00:00Z"), ZoneId.of("UTC"));

    private static class RecordingPublisher implements HotelSearchPublisher {
        private HotelSearchEvent last;

        @Override
        public void publish(HotelSearchEvent event) {
            this.last = event;
        }
    }

    /**
     * Verifica que submit ejecuta un request válido, genera searchId y publica evento.
     *
     * Condiciones:
     * - El command tiene hotelId válido, checkIn dentro de la ventana, checkOut posterior
     *   y edades válidas
     * - El Clock está fijo en 2026-11-10
     *
     * Se espera:
     * - Se genere un searchId UUIDv4 de 36 caracteres
     * - La respuesta contenga el searchId generado y la búsqueda completa
     * - Se publique un evento con el searchId y las edades
     */
    @Test
    @DisplayName("submit_ValidRequest_ReturnsSearchIdAndPublishesEvent")
    void submit_ValidRequest_ReturnsSearchIdAndPublishesEvent() {
        // given
        RecordingPublisher publisher = new RecordingPublisher();
        SubmitHotelSearch useCase = new SubmitHotelSearch(publisher, FIXED_CLOCK);
        SubmitHotelSearch.Command command = new SubmitHotelSearch.Command(
            "550e8400-e29b-41d4-a716-446655440000",
            LocalDate.of(2026, 11, 10),
            LocalDate.of(2026, 11, 29),
            List.of(29, 28, 4, 3));

        // when
        SubmitHotelSearch.Result result = useCase.execute(command);

        // then
        assertThat(result.searchId()).as("El searchId generado no debe ser nulo").isNotNull();
        assertThat(result.searchId().toString()).as("El searchId debe tener 36 caracteres").hasSize(36);
        assertThat(result.search().hotelId().value().toString())
            .as("El hotelId debe coincidir con el solicitado")
            .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(result.search().checkIn().value())
            .as("El checkIn debe coincidir con el solicitado")
            .isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(result.search().checkOut().value())
            .as("El checkOut debe coincidir con el solicitado")
            .isEqualTo(LocalDate.of(2026, 11, 29));
        assertThat(result.search().ages().values())
            .as("Las edades deben conservar el orden")
            .containsExactly(29, 28, 4, 3);

        assertThat(publisher.last).as("Se debe publicar un evento").isNotNull();
        assertThat(publisher.last.searchId()).as("El evento debe llevar el searchId generado")
            .isEqualTo(result.searchId());
        assertThat(publisher.last.ages().values()).as("El evento debe llevar las edades")
            .containsExactly(29, 28, 4, 3);
    }

    /**
     * Verifica que cada ejecución válida genera un searchId nuevo aunque el payload sea idántico.
     *
     * Condiciones:
     * - Se ejecutan dos veces el mismo command válido
     *
     * Se espera:
     * - Los searchId generados sean distintos entre sí
     */
    @Test
    @DisplayName("submit_ValidRequest_EachExecutionGeneratesNewSearchId")
    void submit_ValidRequest_EachExecutionGeneratesNewSearchId() {
        // given
        RecordingPublisher publisher = new RecordingPublisher();
        SubmitHotelSearch useCase = new SubmitHotelSearch(publisher, FIXED_CLOCK);
        SubmitHotelSearch.Command command = new SubmitHotelSearch.Command(
            "550e8400-e29b-41d4-a716-446655440000",
            LocalDate.of(2026, 11, 10),
            LocalDate.of(2026, 11, 29),
            List.of(29, 28, 4, 3));

        // when
        SubmitHotelSearch.Result r1 = useCase.execute(command);
        SubmitHotelSearch.Result r2 = useCase.execute(command);

        // then
        assertThat(r1.searchId()).as("Cada ejecución válida debe generar un searchId distinto")
            .isNotEqualTo(r2.searchId());
    }

    /**
     * Verifica que submit rechaza un command nulo.
     *
     * Condiciones:
     * - El command recibido es null
     *
     * Se espera:
     * - Se lance NullPointerException
     */
    @Test
    @DisplayName("submit_NullCommand_ThrowsNullPointerException")
    void submit_NullCommand_ThrowsNullPointerException() {
        // given
        RecordingPublisher publisher = new RecordingPublisher();
        SubmitHotelSearch useCase = new SubmitHotelSearch(publisher, FIXED_CLOCK);

        // when / then
        assertThatThrownBy(() -> useCase.execute(null))
            .as("Un command nulo debe lanzar NullPointerException")
            .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifica que submit rechaza checkIn fuera de la ventana de un mes calendario.
     *
     * Condiciones:
     * - El Clock está fijo en 2026-11-10
     * - La ventana es 2026-10-10 a 2026-12-10
     * - El checkIn está antes o después de esa ventana
     *
     * Se espera:
     * - Se lance IllegalArgumentException en cada caso
     */
    @Test
    @DisplayName("submit_CheckInOutsideWindow_ThrowsIllegalArgumentException")
    void submit_CheckInOutsideWindow_ThrowsIllegalArgumentException() {
        // given
        RecordingPublisher publisher = new RecordingPublisher();
        SubmitHotelSearch useCase = new SubmitHotelSearch(publisher, FIXED_CLOCK);

        // hoy fijo 2026-11-10; ventana: 2026-10-10 a 2026-12-10
        SubmitHotelSearch.Command tooEarly = new SubmitHotelSearch.Command(
            "550e8400-e29b-41d4-a716-446655440000",
            LocalDate.of(2026, 10, 9),
            LocalDate.of(2026, 11, 29),
            List.of(29));
        SubmitHotelSearch.Command tooLate = new SubmitHotelSearch.Command(
            "550e8400-e29b-41d4-a716-446655440000",
            LocalDate.of(2026, 12, 11),
            LocalDate.of(2026, 12, 20),
            List.of(29));

        // when / then
        assertThatThrownBy(() -> useCase.execute(tooEarly))
            .as("Un checkIn antes de la ventana debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(tooLate))
            .as("Un checkIn después de la ventana debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifica que submit acepta los límites inclusivos de la ventana de un mes.
     *
     * Condiciones:
     * - El Clock está fijo en 2026-11-10
     * - Los límites son 2026-10-10 y 2026-12-10
     *
     * Se espera:
     * - Ambos límites sean aceptados
     */
    @Test
    @DisplayName("submit_CheckInAtWindowLimits_Accepts")
    void submit_CheckInAtWindowLimits_Accepts() {
        // given
        RecordingPublisher publisher = new RecordingPublisher();
        SubmitHotelSearch useCase = new SubmitHotelSearch(publisher, FIXED_CLOCK);

        // hoy fijo 2026-11-10; límites: 2026-10-10 y 2026-12-10
        SubmitHotelSearch.Command minLimit = new SubmitHotelSearch.Command(
            "550e8400-e29b-41d4-a716-446655440000",
            LocalDate.of(2026, 10, 10),
            LocalDate.of(2026, 10, 11),
            List.of(29));
        SubmitHotelSearch.Command maxLimit = new SubmitHotelSearch.Command(
            "550e8400-e29b-41d4-a716-446655440000",
            LocalDate.of(2026, 12, 10),
            LocalDate.of(2026, 12, 11),
            List.of(29));

        // when
        SubmitHotelSearch.Result r1 = useCase.execute(minLimit);
        SubmitHotelSearch.Result r2 = useCase.execute(maxLimit);

        // then
        assertThat(r1.search().checkIn().value()).as("El límite inferior de checkIn debe ser aceptado")
            .isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(r2.search().checkIn().value()).as("El límite superior de checkIn debe ser aceptado")
            .isEqualTo(LocalDate.of(2026, 12, 10));
    }

    /**
     * Verifica que submit rechaza checkOut posterior o igual a checkIn.
     *
     * Condiciones:
     * - checkOut es igual a checkIn
     * - checkOut es anterior a checkIn
     *
     * Se espera:
     * - Se lance IllegalArgumentException en cada caso
     */
    @Test
    @DisplayName("submit_CheckOutNotAfterCheckIn_ThrowsIllegalArgumentException")
    void submit_CheckOutNotAfterCheckIn_ThrowsIllegalArgumentException() {
        // given
        RecordingPublisher publisher = new RecordingPublisher();
        SubmitHotelSearch useCase = new SubmitHotelSearch(publisher, FIXED_CLOCK);

        SubmitHotelSearch.Command sameDay = new SubmitHotelSearch.Command(
            "550e8400-e29b-41d4-a716-446655440000",
            LocalDate.of(2026, 11, 10),
            LocalDate.of(2026, 11, 10),
            List.of(29));
        SubmitHotelSearch.Command before = new SubmitHotelSearch.Command(
            "550e8400-e29b-41d4-a716-446655440000",
            LocalDate.of(2026, 11, 29),
            LocalDate.of(2026, 11, 10),
            List.of(29));

        // when / then
        assertThatThrownBy(() -> useCase.execute(sameDay))
            .as("checkOut igual a checkIn debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(before))
            .as("checkOut anterior a checkIn debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
    }

    // <editor-fold desc="Helpers">
    // sin helpers adicionales por ahora
    // </editor-fold>
}
