package com.hotelsearch.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HotelIdTest {

    /**
     * Verifica que of(String) acepta un identificador de texto libre y lo
     * conserva sin alterar mayúsculas ni minúsculas.
     *
     * Condiciones:
     * - El string recibido es un identificador alfanumérico con mayúsculas y minúsculas
     *
     * Se espera:
     * - El hotelId devuelto contenga exactamente el valor recibido
     */
    @Test
    @DisplayName("of(String) acepta texto libre y conserva mayúsculas y minúsculas")
    void of_FreeText_AcceptsAndPreservesCase() {
        // given
        String valor = "1234aBc";

        // when
        HotelId id = HotelId.of(valor);

        // then
        assertThat(id.value()).as("El hotelId debe conservar el valor recibido sin normalizar")
            .isEqualTo("1234aBc");
    }

    /**
     * Verifica que of(String) sigue aceptando un UUID, porque también es
     * texto válido.
     *
     * Condiciones:
     * - El string recibido es un UUID canónico de 36 caracteres
     *
     * Se espera:
     * - El hotelId devuelto contenga el UUID recibido como texto
     */
    @Test
    @DisplayName("of(String) acepta un UUID como texto válido")
    void of_UuidText_Accepts() {
        // given
        String valor = "550e8400-e29b-41d4-a716-446655440000";

        // when
        HotelId id = HotelId.of(valor);

        // then
        assertThat(id.value()).as("El hotelId debe ser el UUID recibido como texto")
            .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    /**
     * Verifica los límites de longitud aceptados.
     *
     * Condiciones:
     * - Un valor de 1 carácter
     * - Un valor de 64 caracteres, el máximo permitido
     *
     * Se espera:
     * - Ambos valores se acepten sin cambios
     */
    @Test
    @DisplayName("of(String) acepta longitudes de 1 y 64 caracteres")
    void of_LengthLimits_Accepts() {
        // given
        String minimo = "a";
        String maximo = "A".repeat(HotelId.MAX_LENGTH);

        // when
        HotelId idMinimo = HotelId.of(minimo);
        HotelId idMaximo = HotelId.of(maximo);

        // then
        assertAll(
            () -> assertThat(HotelId.MAX_LENGTH).as("El máximo debe ser 64").isEqualTo(64),
            () -> assertThat(idMinimo.value()).as("Debe aceptar 1 carácter").isEqualTo(minimo),
            () -> assertThat(idMaximo.value()).as("Debe aceptar 64 caracteres").isEqualTo(maximo)
        );
    }

    /**
     * Verifica que se rechaza un valor que supera el máximo.
     *
     * Condiciones:
     * - El string recibido tiene 65 caracteres
     *
     * Se espera:
     * - Se lance IllegalArgumentException indicando el máximo
     */
    @Test
    @DisplayName("of(String) rechaza más de 64 caracteres")
    void of_ExceedsMaxLength_Rejects() {
        // given
        String demasiadoLargo = "A".repeat(HotelId.MAX_LENGTH + 1);

        // when / then
        assertThatThrownBy(() -> HotelId.of(demasiadoLargo))
            .as("El hotelId de 65 caracteres debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("hotelId no puede superar 64 caracteres");
    }

    /**
     * Verifica que of(String) rechaza valores nulos o vacíos.
     *
     * Condiciones:
     * - El valor recibido es null o string vacío
     *
     * Se espera:
     * - Se lance IllegalArgumentException en cada caso
     */
    @Test
    @DisplayName("of(String) rechaza nulo o vacío")
    void of_NullOrEmpty_Rejects() {
        // given
        // sin preparación previa

        // when / then
        assertAll(
            () -> assertThatThrownBy(() -> HotelId.of(null))
                .as("El hotelId nulo debe ser rechazado")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("hotelId no puede ser nulo o vacío"),
            () -> assertThatThrownBy(() -> HotelId.of(""))
                .as("El hotelId vacío debe ser rechazado")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("hotelId no puede ser nulo o vacío")
        );
    }

    /**
     * Verifica que se rechaza cualquier espacio en blanco sin normalizar
     * mediante trim.
     *
     * Condiciones:
     * - El valor tiene un espacio en medio, al inicio o al final
     * - El valor contiene un tab o un salto de línea
     * - El valor está compuesto solo por espacios
     *
     * Se espera:
     * - Se lance IllegalArgumentException en cada caso
     */
    @Test
    @DisplayName("of(String) rechaza espacios en blanco en cualquier posición")
    void of_Whitespace_Rejects() {
        // given
        String[] valores = {"1234 aBc", " 1234aBc", "1234aBc ", "1234\taBc", "1234aBc\n", "   "};

        // when / then
        assertAll(Arrays.stream(valores).map(valor ->
            () -> assertThatThrownBy(() -> HotelId.of(valor))
                .as("El hotelId [%s] debe ser rechazado", valor.replace("\t", "\\t").replace("\n", "\\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("hotelId no permite espacios en blanco")));
    }

    /**
     * Verifica que la validación también aplica al constructor canónico,
     * que es el que usa la deserialización del evento Kafka.
     *
     * Condiciones:
     * - Se construye el record directamente con un valor con espacios
     *
     * Se espera:
     * - Se lance IllegalArgumentException
     */
    @Test
    @DisplayName("el constructor canónico aplica las mismas validaciones")
    void constructor_InvalidValue_Rejects() {
        // given
        String conEspacio = "1234 aBc";

        // when / then
        assertThatThrownBy(() -> new HotelId(conEspacio))
            .as("El constructor no debe permitir saltear la validación")
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifica que la comparación es exacta y distingue mayúsculas.
     *
     * Condiciones:
     * - Dos identificadores que solo difieren en mayúsculas
     * - Dos identificadores con el mismo texto
     *
     * Se espera:
     * - Los primeros sean distintos y los segundos iguales
     */
    @Test
    @DisplayName("equals distingue mayúsculas y minúsculas")
    void equals_CaseDifference_AreDistinct() {
        // given
        HotelId original = HotelId.of("1234aBc");

        // when
        HotelId otraCapitalizacion = HotelId.of("1234abc");
        HotelId mismoTexto = HotelId.of("1234aBc");

        // then
        assertAll(
            () -> assertThat(original).as("Difieren en mayúsculas").isNotEqualTo(otraCapitalizacion),
            () -> assertThat(original).as("Mismo texto").isEqualTo(mismoTexto)
        );
    }
}
