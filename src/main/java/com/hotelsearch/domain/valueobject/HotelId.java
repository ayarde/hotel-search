package com.hotelsearch.domain.valueobject;

/**
 * Identificador de hotel definido por el proveedor, por ejemplo
 * {@code 1234aBc}. Es texto libre de 1 a {@value #MAX_LENGTH} caracteres
 * sin espacios en blanco en ninguna posición.
 *
 * <p>El valor se guarda tal como llega: no se aplica trim ni se cambian
 * mayúsculas, así que {@code 1234aBc} y {@code 1234abc} son hoteles
 * distintos. La validación vive en el constructor canónico para que
 * también la respete la deserialización del evento Kafka.</p>
 */
public record HotelId(String value) {

    public static final int MAX_LENGTH = 64;

    public HotelId {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("hotelId no puede ser nulo o vacío");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("hotelId no puede superar " + MAX_LENGTH + " caracteres");
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("hotelId no permite espacios en blanco");
        }
    }

    public static HotelId of(String value) {
        return new HotelId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
