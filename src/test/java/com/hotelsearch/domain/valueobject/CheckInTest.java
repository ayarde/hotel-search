package com.hotelsearch.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

class CheckInTest {

    /**
     * Verifica que of(LocalDate) acepta una fecha válida.
     *
     * Condiciones:
     * - El valor recibido es una LocalDate no nula
     *
     * Se espera:
     * - El checkIn devuelto contenga la fecha recibida
     */
    @Test
    @DisplayName("of(LocalDate) acepta una fecha válida")
    void of_ValidDate_Accepts() {
        // given
        LocalDate fecha = LocalDate.of(2026, 11, 10);

        // when
        CheckIn checkIn = CheckIn.of(fecha);

        // then
        assertThat(checkIn.value()).as("El checkIn debe ser la fecha recibida").isEqualTo(fecha);
    }

    /**
     * Verifica que of(LocalDate) rechaza nulo.
     *
     * Condiciones:
     * - El valor recibido es null
     *
     * Se espera:
     * - Se lance IllegalArgumentException
     */
    @Test
    @DisplayName("of(LocalDate) rechaza nulo")
    void of_Null_Rejects() {
        // when / then
        assertThatThrownBy(() -> CheckIn.of(null))
            .as("El checkIn nulo debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("checkIn no puede ser nulo");
    }

    /**
     * Verifica que dos CheckIn con la misma fecha son iguales.
     *
     * Condiciones:
     * - Dos objetos CheckIn con la misma fecha
     *
     * Se espera:
     * - Sean iguales
     */
    @Test
    @DisplayName("dos CheckIn con la misma fecha son iguales")
    void equals_SameDate_Equals() {
        // given
        CheckIn a = CheckIn.of(LocalDate.of(2026, 11, 10));
        CheckIn b = CheckIn.of(LocalDate.of(2026, 11, 10));

        // when / then
        assertThat(a).as("Misma fecha debe ser igual").isEqualTo(b);
    }

    /**
     * Verifica que dos CheckIn con fechas distintas no son iguales.
     *
     * Condiciones:
     * - Dos objetos CheckIn con fechas distintas
     *
     * Se espera:
     * - No sean iguales
     */
    @Test
    @DisplayName("dos CheckIn con fechas distintas no son iguales")
    void equals_DifferentDate_NotEquals() {
        // given
        CheckIn a = CheckIn.of(LocalDate.of(2026, 11, 10));
        CheckIn b = CheckIn.of(LocalDate.of(2026, 11, 11));

        // when / then
        assertThat(a).as("Fechas distintas no deben ser iguales").isNotEqualTo(b);
    }
}