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
        if (value.chars().anyMatch(HotelId::isBlankCharacter)) {
            throw new IllegalArgumentException("hotelId no permite espacios en blanco");
        }
    }

    /**
     * Considera espacio tanto lo que Java trata como whitespace (espacio,
     * tab, saltos de línea) como los separadores Unicode que no entran en
     * esa categoría, por ejemplo el espacio de no separación U+00A0.
     */
    private static boolean isBlankCharacter(int character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }

    public static HotelId of(String value) {
        return new HotelId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
