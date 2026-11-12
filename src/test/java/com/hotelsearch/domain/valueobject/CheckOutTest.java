package com.hotelsearch.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

class CheckOutTest {

    /**
     * Verifica que of(LocalDate) acepta una fecha válida.
     *
     * Condiciones:
     * - El valor recibido es una LocalDate no nula
     *
     * Se espera:
     * - El checkOut devuelto contenga la fecha recibida
     */
    @Test
    @DisplayName("of(LocalDate) acepta una fecha válida")
    void of_ValidDate_Accepts() {
        // given
        LocalDate fecha = LocalDate.of(2026, 11, 29);

        // when
        CheckOut checkOut = CheckOut.of(fecha);

        // then
        assertThat(checkOut.value()).as("El checkOut debe ser la fecha recibida").isEqualTo(fecha);
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
        assertThatThrownBy(() -> CheckOut.of(null))
            .as("El checkOut nulo debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("checkOut no puede ser nulo");
    }

    /**
     * Verifica que dos CheckOut con la misma fecha son iguales.
     *
     * Condiciones:
     * - Dos objetos CheckOut con la misma fecha
     *
     * Se espera:
     * - Sean iguales
     */
    @Test
    @DisplayName("dos CheckOut con la misma fecha son iguales")
    void equals_SameDate_Equals() {
        // given
        CheckOut a = CheckOut.of(LocalDate.of(2026, 11, 29));
        CheckOut b = CheckOut.of(LocalDate.of(2026, 11, 29));

        // when / then
        assertThat(a).as("Misma fecha debe ser igual").isEqualTo(b);
    }

    /**
     * Verifica que dos CheckOut con fechas distintas no son iguales.
     *
     * Condiciones:
     * - Dos objetos CheckOut con fechas distintas
     *
     * Se espera:
     * - No sean iguales
     */
    @Test
    @DisplayName("dos CheckOut con fechas distintas no son iguales")
    void equals_DifferentDate_NotEquals() {
        // given
        CheckOut a = CheckOut.of(LocalDate.of(2026, 11, 29));
        CheckOut b = CheckOut.of(LocalDate.of(2026, 11, 30));

        // when / then
        assertThat(a).as("Fechas distintas no deben ser iguales").isNotEqualTo(b);
    }
}