package com.hotelsearch.application;

import java.io.Serial;
import com.hotelsearch.domain.valueobject.SearchId;

/**
 * Error de aplicación para una búsqueda válida que todavía no existe en
 * Oracle o que nunca fue persistida.
 */
public final class SearchNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Crea el error con el identificador consultado.
     *
     * @param searchId identificador de la búsqueda no encontrada
     */
    public SearchNotFoundException(SearchId searchId) {
        super("searchId no existe: " + searchId);
    }
}
