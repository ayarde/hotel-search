package com.hotelsearch.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;

class HotelSearchTest {

    /**
     * Verifica que HotelSearch es inmutable y conserva el orden de las edades.
     *
     * Condiciones:
     * - Todos los argumentos son válidos y no nulos
     *
     * Se espera:
     * - Todos los getters devuelvan los valores asignados
     * - Las edades conserven el orden recibido
     */
    @Test
    @DisplayName("HotelSearch es inmutable y conserva el orden de las edades")
    void constructor_ValidData_CreatesImmutableInstance() {
        // given
        SearchId id = SearchId.generate();
        HotelId hotel = HotelId.of("550e8400-e29b-41d4-a716-446655440000");
        CheckIn checkIn = CheckIn.of(LocalDate.of(2026, 11, 9));
        CheckOut checkOut = CheckOut.of(LocalDate.of(2026, 11, 29));
        Ages ages = Ages.of(List.of(29, 28, 4, 3));

        // when
        HotelSearch search = new HotelSearch(id, hotel, checkIn, checkOut, ages);

        // then
        assertThat(search.searchId()).as("El searchId debe ser el asignado").isEqualTo(id);
        assertThat(search.hotelId()).as("El hotelId debe ser el asignado").isEqualTo(hotel);
        assertThat(search.checkIn()).as("El checkIn debe ser el asignado").isEqualTo(checkIn);
        assertThat(search.checkOut()).as("El checkOut debe ser el asignado").isEqualTo(checkOut);
        assertThat(search.ages().values()).as("Las edades deben conservar el orden")
            .containsExactly(29, 28, 4, 3);
        assertThat(search.ages().values().size()).as("El tamaño de las edades debe ser 4").isEqualTo(4);
    }

    /**
     * Verifica que constructor rechaza argumentos nulos.
     *
     * Condiciones:
     * - Cualquier argumento del constructor es null
     *
     * Se espera:
     * - Se lance NullPointerException para cada argumento nulo
     */
    @Test
    @DisplayName("constructor rechaza argumentos nulos")
    void constructor_NullArguments_Rejects() {
        // given
        HotelId hotel = HotelId.of("550e8400-e29b-41d4-a716-446655440000");
        CheckIn checkIn = CheckIn.of(LocalDate.of(2026, 11, 9));
        CheckOut checkOut = CheckOut.of(LocalDate.of(2026, 11, 29));
        Ages ages = Ages.of(List.of(29, 28, 4, 3));

        // when / then
        assertThatThrownBy(() -> new HotelSearch(null, hotel, checkIn, checkOut, ages))
            .as("Un searchId nulo debe ser rechazado")
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HotelSearch(SearchId.generate(), null, checkIn, checkOut, ages))
            .as("Un hotelId nulo debe ser rechazado")
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HotelSearch(SearchId.generate(), hotel, null, checkOut, ages))
            .as("Un checkIn nulo debe ser rechazado")
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HotelSearch(SearchId.generate(), hotel, checkIn, null, ages))
            .as("Un checkOut nulo debe ser rechazado")
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HotelSearch(SearchId.generate(), hotel, checkIn, checkOut, null))
            .as("Un ages nulo debe ser rechazado")
            .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifica que dos búsquedas con los mismos datos pero distinto searchId son distintas.
     *
     * Condiciones:
     * - Dos instancias de HotelSearch con el mismo hotelId, checkIn, checkOut y ages
     *   pero con searchId distinto
     *
     * Se espera:
     * - No sean iguales, ya que el searchId las diferencia
     */
    @Test
    @DisplayName("dos búsquedas con los mismos datos pero distinto searchId son distintas")
    void equals_DifferentIds_AreNotEqual() {
        // given
        HotelId hotel = HotelId.of("550e8400-e29b-41d4-a716-446655440000");
        CheckIn checkIn = CheckIn.of(LocalDate.of(2026, 11, 9));
        CheckOut checkOut = CheckOut.of(LocalDate.of(2026, 11, 29));
        Ages ages = Ages.of(List.of(29, 28, 4, 3));

        HotelSearch a = new HotelSearch(SearchId.generate(), hotel, checkIn, checkOut, ages);
        HotelSearch b = new HotelSearch(SearchId.generate(), hotel, checkIn, checkOut, ages);

        // when / then
        assertThat(a).as("Distintos searchId hacen que las búsquedas sean distintas")
            .isNotEqualTo(b);
    }

    /**
     * Verifica que dos búsquedas con el mismo searchId y mismos datos son iguales.
     *
     * Condiciones:
     * - Dos instancias de HotelSearch con el mismo searchId, hotelId, checkIn, checkOut y ages
     *
     * Se espera:
     * - Sean iguales y tengan el mismo hashCode
     */
    @Test
    @DisplayName("dos búsquedas con el mismo searchId y mismos datos son iguales")
    void equals_SameIdAndData_AreEqual() {
        // given
        SearchId id = SearchId.generate();
        HotelId hotel = HotelId.of("550e8400-e29b-41d4-a716-446655440000");
        CheckIn checkIn = CheckIn.of(LocalDate.of(2026, 11, 9));
        CheckOut checkOut = CheckOut.of(LocalDate.of(2026, 11, 29));
        Ages ages = Ages.of(List.of(29, 28, 4, 3));

        HotelSearch a = new HotelSearch(id, hotel, checkIn, checkOut, ages);
        HotelSearch b = new HotelSearch(id, hotel, checkIn, checkOut, ages);

        // when / then
        assertThat(a).as("Mismo searchId y mismos datos hacen que las búsquedas sean iguales")
            .isEqualTo(b);
        assertThat(a.hashCode()).as("El hashCode debe ser igual para instancias iguales")
            .isEqualTo(b.hashCode());
    }
}
