package com.hotelsearch.domain.valueobject;

import java.util.List;

/**
 * Lista de edades de los huéspedes. Inmutable, no vacía, máximo 50
 * elementos, sin nulos y cada valor >= 0. El orden importa para la
 * equivalencia.
 */
public record Ages(List<Integer> values) {

    private static final int MAX_SIZE = 50;

    public Ages {
        if (values == null) {
            throw new IllegalArgumentException("ages no puede ser nulo");
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("ages no puede ser vacío");
        }
        if (values.size() > MAX_SIZE) {
            throw new IllegalArgumentException("ages no puede tener más de " + MAX_SIZE + " elementos");
        }
        for (Integer age : values) {
            if (age == null) {
                throw new IllegalArgumentException("ages no puede contener elementos nulos");
            }
            if (age < 0) {
                throw new IllegalArgumentException("cada edad debe ser >= 0, valor: " + age);
            }
        }
        values = List.copyOf(values);
    }

    /**
     * @param values edades en el orden recibido; se copia la lista
     * @return las edades validadas
     */
    public static Ages of(List<Integer> values) {
        return new Ages(values);
    }
}
