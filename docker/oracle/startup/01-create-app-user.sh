#!/bin/bash
set -euo pipefail

readonly APP_USER="HOTEL_SEARCH"

raw_password="${APP_USER_PASSWORD:?APP_USER_PASSWORD debe definirse}"
escaped_password=$(printf '%s' "$raw_password" \
  | sed -e "s/'/''/g" -e 's/"/""/g')

# La imagen oficial ejecuta este script en cada arranque. El bloque PL/SQL
# crea el usuario la primera vez y actualiza su contraseña en los siguientes.
sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET DEFINE OFF
ALTER SESSION SET CONTAINER=${ORACLE_PDB};

DECLARE
    user_count PLS_INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO user_count
      FROM all_users
     WHERE username = '${APP_USER}';

    IF user_count = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE USER ${APP_USER} IDENTIFIED BY "${escaped_password}"';
    ELSE
        EXECUTE IMMEDIATE
            'ALTER USER ${APP_USER} IDENTIFIED BY "${escaped_password}"';
    END IF;
END;
/

GRANT CONNECT, RESOURCE TO ${APP_USER};
GRANT UNLIMITED TABLESPACE TO ${APP_USER};
EXIT
SQL
