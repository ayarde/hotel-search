package com.hotelsearch.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

class HotelIdTest {

    /**
     * Verifica que of(String) acepta un UUID canónico de 36 caracteres.
     *
     * Condiciones:
     * - El string recibido es un UUID en minúsculas con guiones y 36 caracteres
     *
     * Se espera:
     * - El hotelId devuelto contenga el UUID canónico recibido
     */
    @Test
    @DisplayName("of(String) acepta un UUID canónico de 36 caracteres")
    void of_CanonicalUuid_Accepts() {
        // given
        String valor = "550e8400-e29b-41d4-a716-446655440000";

        // when
        HotelId id = HotelId.of(valor);

        // then
        assertThat(id.value().toString()).as("El hotelId debe ser el UUID canónico recibido")
            .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    /**
     * Verifica que of(String) rechaza valores nulos o vacíos.
     *
     * Condiciones:
     * - El valor recibido es null, string vacío o string con solo espacios
     *
     * Se espera:
     * - Se lance IllegalArgumentException en cada caso
     */
    @Test
    @DisplayName("of(String) rechaza nulo o vacío")
    void of_NullOrBlank_Rejects() {
        // given
        // sin preparación previa

        // when / then
        assertThatThrownBy(() -> HotelId.of((String) null))
            .as("El hotelId nulo debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HotelId.of(""))
            .as("El hotelId vacío debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HotelId.of("   "))
            .as("El hotelId con solo espacios debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifica que of(String) rechaza espacios sin normalizar mediante trim.
     *
     * Condiciones:
     * - El string recibido tiene espacios al final, al inicio o en medio
     *
     * Se espera:
     * - Se lance IllegalArgumentException en cada caso (no se aplica trim silencioso)
     */
    @Test
    @DisplayName("of(String) rechaza espacios (no se normaliza con trim)")
    void of_WithSpaces_Rejects() {
        // given
        String conEspacioAlFinal = "550e8400-e29b-41d4-a716-446655440000 ";
        String conEspacioAlInicio = " 550e8400-e29b-41d4-a716-446655440000";
        String conEspacioEnMedio = "550e8400 e29b41d4a716446655440000";

        // when / then
        assertThatThrownBy(() -> HotelId.of(conEspacioAlFinal))
            .as("El hotelId con espacio al final debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HotelId.of(conEspacioAlInicio))
            .as("El hotelId con espacio al inicio debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HotelId.of(conEspacioEnMedio))
            .as("El hotelId con espacio en medio debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifica que of(String) rechaza longitud distinta de 36 caracteres.
     *
     * Condiciones:
     * - El string recibido es demasiado corto o demasiado largo
     *
     * Se espera:
     * - Se lance IllegalArgumentException en cada caso
     */
    @Test
    @DisplayName("of(String) rechaza longitud distinta de 36")
    void of_WrongLength_Rejects() {
        // given
        String demasiadoCorto = "550e8400-e29b-41d4-a716-44665544000";
        String demasiadoLargo = "550e8400-e29b-41d4-a716-4466554400000";

        // when / then
        assertThatThrownBy(() -> HotelId.of(demasiadoCorto))
            .as("El hotelId demasiado corto debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HotelId.of(demasiadoLargo))
            .as("El hotelId demasiado largo debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifica que of(String) rechaza mayúsculas (no es canónico).
     *
     * Condiciones:
     * - El string recibido contiene letras mayúsculas
     *
     * Se espera:
     * - Se lance IllegalArgumentException
     */
    @Test
    @DisplayName("of(String) rechaza mayúsculas (no es canónico)")
    void of_Uppercase_Rejects() {
        // given
        String conMayusculas = "550E8400-E29B-41D4-A716-446655440000";

        // when / then
        assertThatThrownBy(() -> HotelId.of(conMayusculas))
            .as("El hotelId con mayúsculas no es canónico y debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifica que of(String) rechaza formato inválido.
     *
     * Condiciones:
     * - El string recibido no tiene guiones
     * - El string recibido contiene caracteres inválidos
     *
     * Se espera:
     * - Se lance IllegalArgumentException en cada caso
     */
    @Test
    @DisplayName("of(String) rechaza formato inválido")
    void of_InvalidFormat_Rejects() {
        // given
        String sinGuiones = "550e8400e29b41d4a716446655440000";
        String caracteresInvalidos = "gggggggg-gggg-gggg-gggg-gggggggggggg";

        // when / then
        assertThatThrownBy(() -> HotelId.of(sinGuiones))
            .as("El hotelId sin guiones debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HotelId.of(caracteresInvalidos))
            .as("El hotelId con caracteres inválidos debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
    }
}
