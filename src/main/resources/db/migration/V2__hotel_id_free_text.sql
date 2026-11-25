-- Migración V2: hotel_id como texto libre
-- ================================================
-- El identificador del hotel lo define el proveedor (por ejemplo 1234aBc)
-- y puede tener hasta 64 caracteres. V1 lo había creado con el tamaño de
-- un UUID; se amplía acá para no editar una migración ya aplicada.
-- Ampliar un VARCHAR2 conserva los datos y el índice idx_hotel_search_match.
-- ================================================

ALTER TABLE hotel_search MODIFY (hotel_id VARCHAR2(64 CHAR));
