package com.hotelsearch.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

class AgesTest {

    /**
     * Verifica que of(List) acepta una lista válida de edades.
     *
     * Condiciones:
     * - La lista de edades no es nula, no está vacía, tiene máximo 50 elementos,
     *   no contiene nulos y cada valor es mayor o igual a cero.
     *
     * Se espera:
     * - La lista de edades se conserve en el orden recibido
     * - El tamaño de la lista sea correcto
     */
    @Test
    @DisplayName("of(List) acepta una lista válida de edades")
    void of_ValidAges_Accepts() {
        // given
        List<Integer> edades = Arrays.asList(29, 28, 4, 3);

        // when
        Ages ages = Ages.of(edades);

        // then
        assertThat(ages.values()).as("Las edades deben conservar el orden")
            .containsExactly(29, 28, 4, 3);
        assertThat(ages.values().size()).as("El tamaño debe ser 4").isEqualTo(4);
    }

    /**
     * Verifica que of(List) rechaza lista nula o vacía.
     *
     * Condiciones:
     * - El valor recibido es null o una lista vacía
     *
     * Se espera:
     * - Se lance IllegalArgumentException en cada caso
     */
    @Test
    @DisplayName("of(List) rechaza lista nula o vacía")
    void of_NullOrEmpty_Rejects() {
        // given
        // sin preparación previa

        // when / then
        assertThatThrownBy(() -> Ages.of(null))
            .as("La lista de edades nula debe ser rechazada")
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ages.of(List.of()))
            .as("La lista de edades vacía debe ser rechazada")
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifica que of(List) rechaza más de 50 elementos.
     *
     * Condiciones:
     * - La lista tiene 51 elementos
     *
     * Se espera:
     * - Se lance IllegalArgumentException
     */
    @Test
    @DisplayName("of(List) rechaza más de 50 elementos")
    void of_MoreThan50Elements_Rejects() {
        // given
        List<Integer> demasiadas = java.util.stream.IntStream.range(0, 51).boxed().toList();

        // when / then
        assertThatThrownBy(() -> Ages.of(demasiadas))
            .as("Más de 50 edades debe ser rechazado")
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifica que of(List) acepta exactamente 50 elementos.
     *
     * Condiciones:
     * - La lista tiene exactamente 50 elementos
     *
     * Se espera:
     * - Se acepte sin lanzar excepción
     */
    @Test
    @DisplayName("of(List) acepta exactamente 50 elementos")
    void of_Exactly50Elements_Accepts() {
        // given
        List<Integer> cincuenta = java.util.stream.IntStream.range(0, 50).boxed().toList();

        // when
        Ages ages = Ages.of(cincuenta);

        // then
        assertThat(ages.values().size()).as("Debe aceptar exactamente 50 edades").isEqualTo(50);
    }

    /**
     * Verifica que of(List) rechaza elemento nulo.
     *
     * Condiciones:
     * - La lista contiene al menos un elemento nulo
     *
     * Se espera:
     * - Se lance IllegalArgumentException
     */
    @Test
    @DisplayName("of(List) rechaza elemento nulo")
    void of_NullElement_Rejects() {
        // given
        List<Integer> conNulo = Arrays.asList(29, null, 4);

        // when / then
        assertThatThrownBy(() -> Ages.of(conNulo))
            .as("Una edad nula debe ser rechazada")
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifica que of(List) rechaza edad negativa.
     *
     * Condiciones:
     * - La lista contiene al menos un valor negativo
     *
     * Se espera:
     * - Se lance IllegalArgumentException
     */
    @Test
    @DisplayName("of(List) rechaza edad negativa")
    void of_NegativeAge_Rejects() {
        // given
        List<Integer> conNegativo = Arrays.asList(29, -1, 4);

        // when / then
        assertThatThrownBy(() -> Ages.of(conNegativo))
            .as("Una edad negativa debe ser rechazada")
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifica que la lista devuelta es inmutable (defensive copy).
     *
     * Condiciones:
     * - Se intenta agregar un elemento o limpiar la lista devuelta
     *
     * Se espera:
     * - Se lance UnsupportedOperationException al modificar la lista
     */
    @Test
    @DisplayName("la lista devuelta es inmutable (defensive copy)")
    void values_IsImmutable() {
        // given
        Ages ages = Ages.of(Arrays.asList(29, 28, 4, 3));

        // when / then
        assertThatThrownBy(() -> ages.values().add(5))
            .as("La lista de edades debe ser inmutable")
            .isInstanceOf(java.lang.UnsupportedOperationException.class);
        assertThatThrownBy(() -> ages.values().clear())
            .as("La lista de edades no debe permitir limpiarse")
            .isInstanceOf(java.lang.UnsupportedOperationException.class);
    }

    /**
     * Verifica que dos listas iguales en distinto orden no son equivalentes.
     *
     * Condiciones:
     * - Dos objetos Ages con los mismos elementos pero en orden distinto
     *
     * Se espera:
     * - No sean iguales, ya que el orden importa para la equivalencia
     */
    @Test
    @DisplayName("dos listas iguales en distinto orden no son equivalentes")
    void equals_DifferentOrder_NotEquivalent() {
        // given
        Ages a = Ages.of(Arrays.asList(29, 28, 4, 3));
        Ages b = Ages.of(Arrays.asList(28, 29, 4, 3));

        // when / then
        assertThat(a).as("El orden de las edades importa para la equivalencia")
            .isNotEqualTo(b);
    }

    /**
     * Verifica que dos listas iguales en mismo orden son equivalentes.
     *
     * Condiciones:
     * - Dos objetos Ages con los mismos elementos en el mismo orden
     *
     * Se espera:
     * - Sean iguales
     */
    @Test
    @DisplayName("dos listas iguales en mismo orden son equivalentes")
    void equals_SameOrder_Equivalent() {
        // given
        Ages a = Ages.of(Arrays.asList(29, 28, 4, 3));
        Ages b = Ages.of(Arrays.asList(29, 28, 4, 3));

        // when / then
        assertThat(a).as("Mismas edades en el mismo orden son equivalentes")
            .isEqualTo(b);
    }
}
