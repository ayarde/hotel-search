-- Migración V1: esquema inicial de hotel_search
-- ================================================
-- Crea la tabla hotel_search y el índice de consulta, según el modelo
-- acordado en TECHNICAL-DECISIONS.md (§7 Oracle). Los identificadores
-- SQL van en minúsculas.
-- ================================================

CREATE TABLE hotel_search (
    search_id   VARCHAR2(36 CHAR) NOT NULL,
    hotel_id    VARCHAR2(36 CHAR) NOT NULL,
    check_in    DATE NOT NULL,
    check_out   DATE NOT NULL,
    ages        VARCHAR2(600 CHAR) NOT NULL,
    created_at  TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_hotel_search PRIMARY KEY (search_id),
    CONSTRAINT ck_hotel_search_dates CHECK (check_in < check_out)
);

-- Índice de consulta: igualdad de ages se resuelve en el predicado,
-- no en la clave del índice (puede superar la longitud de clave).
CREATE INDEX idx_hotel_search_match
    ON hotel_search (
        hotel_id,
        check_in,
        check_out
    );
