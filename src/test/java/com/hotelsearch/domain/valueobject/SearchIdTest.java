package com.hotelsearch.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

class SearchIdTest {

    /**
     * Verifica que generate() crea dos UUIDv4 distintos en cada llamada.
     *
     * Condiciones:
     * - Sin preparación previa
     *
     * Se espera:
     * - Los dos searchId generados sean distintos entre sí
     * - Ambos sean UUIDv4 válidos
     * - Cada uno tenga 36 caracteres
     */
    @Test
    @DisplayName("generate() crea un UUIDv4 distinto en cada llamada")
    void generate_CreatesDistinctV4Ids() {
        // given
        // sin preparación previa

        // when
        SearchId id1 = SearchId.generate();
        SearchId id2 = SearchId.generate();

        // then
        assertAll(
            () -> assertThat(id1).as("Los searchId generados deben ser distintos").isNotEqualTo(id2),
            () -> assertThat(isV4(id1.value())).as("El primer searchId debe ser UUIDv4").isTrue(),
            () -> assertThat(isV4(id2.value())).as("El segundo searchId debe ser UUIDv4").isTrue(),
            () -> assertThat(id1.toString()).as("El searchId debe tener 36 caracteres").hasSize(36),
            () -> assertThat(id2.toString()).as("El searchId debe tener 36 caracteres").hasSize(36));
    }

    /**
     * Verifica que of(String) acepta un UUID válido.
     *
     * Condiciones:
     * - El string recibido es un UUID canónico válido
     *
     * Se espera:
     * - El valor del searchId coincida con el UUID recibido
     */
    @Test
    @DisplayName("of(String) acepta un UUID válido")
    void of_ValidUuid_Accepts() {
        // given
        String valor = "550e8400-e29b-41d4-a716-446655440000";

        // when
        SearchId id = SearchId.of(valor);

        // then
        assertThat(id.value()).as("El valor del searchId debe coincidir con el UUID recibido")
            .isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
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
        assertAll(
            () -> assertThatThrownBy(() -> SearchId.of((String) null))
                .as("El searchId nulo debe ser rechazado")
                .isInstanceOf(IllegalArgumentException.class),
            () -> assertThatThrownBy(() -> SearchId.of(""))
                .as("El searchId vacío debe ser rechazado")
                .isInstanceOf(IllegalArgumentException.class),
            () -> assertThatThrownBy(() -> SearchId.of("   "))
                .as("El searchId con solo espacios debe ser rechazado")
                .isInstanceOf(IllegalArgumentException.class));
    }

    /**
     * Verifica que of(String) rechaza un string que no es un UUID válido.
     *
     * Condiciones:
     * - El string recibido no tiene formato de UUID
     * - El string recibido es un UUID incompleto
     *
     * Se espera:
     * - Se lance IllegalArgumentException en cada caso
     */
    @Test
    @DisplayName("of(String) rechaza un string que no es UUID")
    void of_InvalidUuid_Rejects() {
        // given
        String valor = "no-es-un-uuid";
        String valorIncompleto = "550e8400-e29b-41d4-a716";

        // when / then
        assertThatThrownBy(() -> SearchId.of(valor))
            .as("Un string que no es UUID debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SearchId.of(valorIncompleto))
            .as("Un UUID incompleto debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifica que dos búsquedas iguales pueden tener distintos searchId.
     *
     * Condiciones:
     * - Se ejecutan dos llamadas a generate()
     *
     * Se espera:
     * - Los searchId generados sean distintos aunque los datos de las búsquedas sean iguales
     */
    @Test
    @DisplayName("dos búsquedas iguales pueden tener distintos searchId")
    void equalSearches_CanHaveDifferentIds() {
        // given
        // sin preparación previa

        // when
        SearchId id1 = SearchId.generate();
        SearchId id2 = SearchId.generate();

        // then
        assertThat(id1).as("Cada búsqueda genera un searchId distinto aunque los datos sean iguales")
            .isNotEqualTo(id2);
    }

    // <editor-fold desc="Helpers">
    private static boolean isV4(UUID uuid) {
        // Version: 0100 (bits 12-15 de time_hi = 0100)
        long version = (uuid.getMostSignificantBits() >> 12) & 0xF;
        return version == 0x4;
    }
    // </editor-fold>
}
