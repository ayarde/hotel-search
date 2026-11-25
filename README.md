# Hotel Search

Aplicación Java que recibe búsquedas de hoteles, publica cada búsqueda en
Kafka, la persiste de forma asíncrona en Oracle y permite consultar la
búsqueda junto con la cantidad de búsquedas equivalentes.

El proyecto usa arquitectura hexagonal en un único módulo Gradle:

- `com.hotelsearch.domain`: modelos, value objects e invariantes de negocio.
- `com.hotelsearch.application`: casos de uso y puertos de entrada y salida.
- `com.hotelsearch.infrastructure.input.rest`: API HTTP, DTOs y manejo de errores.
- `com.hotelsearch.infrastructure.input.kafka`: consumer, validación de eventos y correlación.
- `com.hotelsearch.infrastructure.output.kafka`: publicación de eventos.
- `com.hotelsearch.infrastructure.output.persistence`: persistencia JDBC.
- `com.hotelsearch.infrastructure.config`: configuración de Spring, Kafka, JDBC y OpenAPI.
- `com.hotelsearch.infrastructure.observability`: correlación y MDC.

## Flujo principal

```text
POST /search
    -> HotelSearchController
    -> SubmitHotelSearch
    -> KafkaHotelSearchPublisher
    -> hotel_availability_searches
    -> HotelSearchKafkaListener
    -> PersistHotelSearch
    -> JdbcHotelSearchRepository (@Transactional)
    -> Oracle

GET /count?searchId=...
    -> HotelSearchController
    -> CountHotelSearch
    -> JdbcHotelSearchRepository
    -> Oracle
```

La entrega es **at-least-once**. El consumer confirma el offset de Kafka
únicamente después del commit de Oracle. Una redelivery del mismo
`searchId` no crea una segunda fila porque `search_id` es la clave primaria.

## Stack

- Java 25.
- Spring Boot 4.1.2.
- Gradle 9.9.0 mediante Gradle Wrapper y Kotlin DSL.
- Spring JDBC con HikariCP y Flyway.
- Spring Kafka.
- Oracle Database Free 23.26.3.
- Apache Kafka 4.4.0 en modo KRaft.
- JUnit 5, Mockito, Testcontainers y JaCoCo.
- OpenAPI 3 y Swagger UI mediante springdoc.

El proyecto no utiliza Maven ni contiene módulos Gradle adicionales.

## Requisitos

### Para ejecutar el stack con Docker

- Docker Engine.
- Docker Compose v2, disponible como `docker compose`.
- Permisos para construir imágenes y publicar los puertos configurados.

La primera descarga de las imágenes y el arranque inicial de Oracle pueden
tardar varios minutos.

### Para compilar y ejecutar tests fuera de Docker

- JDK 25.
- Acceso a los repositorios desde los que Gradle descarga sus dependencias.
- Docker es necesario para los integration tests porque usan Kafka y Oracle
  reales mediante Testcontainers.

No hace falta instalar Gradle ni Kafka manualmente: el Wrapper descarga
Gradle y Docker Compose levanta la infraestructura.

## Inicio rápido

### 1. Obtener el proyecto

```bash
git clone https://github.com/ayarde/hotel-search.git
cd hotel-search
```

### 2. Crear la configuración local

```bash
cp .env.example .env
```

`.env.example` contiene valores de ejemplo. Reemplazá `ORACLE_PWD` y
`DB_PASSWORD` por credenciales locales antes de compartir o exponer el
entorno. `ORACLE_PWD` administra la instancia de Oracle; `DB_PASSWORD` es la
contraseña del usuario `hotel_search` usado por la aplicación y Flyway.

El archivo `.env` está ignorado por Git y nunca debe versionarse.

### 3. Levantar la aplicación y sus dependencias

```bash
docker compose up --build
```

Este es el comando principal de ejecución. La aplicación espera a que Kafka y
Oracle estén saludables antes de iniciar. Para ejecutarla en segundo plano:

```bash
docker compose up --build -d
```

### 4. Comprobar que la API está disponible

```bash
curl --fail http://localhost:8080/v3/api-docs > /dev/null
```

Swagger UI está disponible en:

- <http://localhost:8080/swagger-ui.html>
- El documento OpenAPI está en <http://localhost:8080/v3/api-docs>.

## API HTTP

### Registrar una búsqueda

```http
POST /search
Content-Type: application/json
```

El check-in debe estar dentro de la ventana inclusiva de un mes calendario
alrededor de la fecha actual. El check-out solo debe ser posterior al
check-in. Las fechas usan el formato `dd/MM/yyyy`.

El siguiente ejemplo usa la fecha actual como check-in y una fecha posterior
para el check-out:

```bash
CHECK_IN=$(date +%d/%m/%Y)
CHECK_OUT=31/12/2099

curl -i http://localhost:8080/search \
  --header 'Content-Type: application/json' \
  --data-binary @- <<JSON
{
  "hotelId": "1234aBc",
  "checkIn": "$CHECK_IN",
  "checkOut": "$CHECK_OUT",
  "ages": [29, 28, 4, 3]
}
JSON
```

Si Kafka confirma el evento, la API responde `202 Accepted`:

```json
{
  "searchId": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
}
```

El `202` confirma la aceptación por Kafka, no la persistencia inmediata en
Oracle. Cada solicitud válida genera un identificador nuevo, incluso si el
payload es idéntico al de otra solicitud.

### Consultar una búsqueda y sus equivalentes

Copiá el `searchId` devuelto por el POST, reemplazá el valor del ejemplo y
ejecutá:

```bash
SEARCH_ID="f47ac10b-58cc-4372-a567-0e02b2c3d479"

curl --get http://localhost:8080/count \
  --data-urlencode "searchId=$SEARCH_ID"
```

Después de que el consumer complete la persistencia, la respuesta es:

```json
{
  "searchId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "hotelId": "1234aBc",
  "checkIn": "12/11/2026",
  "checkOut": "19/11/2026",
  "ages": [29, 28, 4, 3],
  "searchCount": 0
}
```

`searchCount` excluye la búsqueda consultada. Por eso, después de registrar
una sola búsqueda, el resultado es `0`. Dos búsquedas son equivalentes
cuando tienen el mismo `hotelId`, check-in, check-out y lista de edades en el
mismo orden.

El GET puede responder `404 Not Found` durante la ventana entre el ACK de
Kafka y el commit de Oracle. Repetí la consulta después de que el consumer
termine.

### Ejemplo: varias búsquedas equivalentes

El siguiente ejemplo registra tres búsquedas con el mismo payload. Cada POST
genera un `searchId` distinto:

```bash
CHECK_IN=$(date +%d/%m/%Y)
CHECK_OUT=31/12/2099
PAYLOAD=$(cat <<JSON
{
  "hotelId": "1234aBc",
  "checkIn": "$CHECK_IN",
  "checkOut": "$CHECK_OUT",
  "ages": [29, 28, 4, 3]
}
JSON
)

for request in 1 2 3; do
  curl --silent --show-error --fail \
    http://localhost:8080/search \
    --header 'Content-Type: application/json' \
    --data "$PAYLOAD"
  printf '\n'
done
```

El request no incluye `searchId`: el servidor genera un UUIDv4 nuevo para
cada POST. El flujo es conceptualmente:

```text
POST 1 -> searchId A
POST 2 -> searchId B
POST 3 -> searchId C

GET /count?searchId=A -> encuentra A, B y C; searchCount = 2
```

Después de que el consumer persista las tres búsquedas, consultá cualquiera
de los `searchId` devueltos:

```bash
SEARCH_ID="f47ac10b-58cc-4372-a567-0e02b2c3d479"

curl --get http://localhost:8080/count \
  --data-urlencode "searchId=$SEARCH_ID"
```

La respuesta contiene:

```json
{
  "searchId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "hotelId": "1234aBc",
  "checkIn": "12/11/2026",
  "checkOut": "19/11/2026",
  "ages": [29, 28, 4, 3],
  "searchCount": 2
}
```

Con un volumen que no contiene otras búsquedas equivalentes, hay tres
búsquedas en total y `searchCount` es `2` porque excluye la búsqueda
consultada. Si se consultara cualquiera de las otras dos, la respuesta también
tendría `searchCount: 2`.

El contador abarca todas las búsquedas equivalentes que ya están persistidas,
no solamente las creadas por la última ejecución del ejemplo. Oracle conserva
su volumen entre reinicios: si el payload ya tenía `N` búsquedas equivalentes,
después de tres POST nuevos el resultado será `N + 2`. Para reproducir un
resultado aislado, se debe usar un `hotelId` nuevo o levantar Oracle con un
volumen limpio.

### Validación y errores

- `hotelId` es texto de 1 a 64 caracteres, por ejemplo `1234aBc`, sin
  espacios en blanco. Se guarda tal como llega y la comparación distingue
  mayúsculas: `1234aBc` y `1234abc` son hoteles distintos.
- `checkIn` y `checkOut` deben usar `dd/MM/yyyy`.
- `ages` debe contener entre 1 y 50 enteros no negativos.
- El body HTTP no puede superar 16 KB.
- Las propiedades JSON desconocidas se rechazan.
- Los errores usan Problem Details RFC 9457.

Respuestas principales:

- `202 Accepted`: Kafka aceptó el evento.
- `400 Bad Request`: el request no cumple el contrato.
- `404 Not Found`: el `searchId` todavía no existe en Oracle.
- `503 Service Unavailable`: Kafka no está disponible o no confirmó la publicación.

## Consistencia eventual

El POST y la persistencia en Oracle no forman una transacción distribuida:

1. La API valida el request y genera un `searchId`.
2. El producer publica el evento usando `searchId` como key.
3. La API responde `202` después del ACK de Kafka.
4. El consumer valida el evento y abre una transacción Oracle.
5. Oracle confirma el INSERT.
6. Spring Kafka confirma el offset.

Una ventana temporal entre los pasos 3 y 5 es esperable. No se implementa
Outbox, idempotencia HTTP, XA, Exactly-once ni un mecanismo de recuperación
del publisher.

## Reintentos y DLT

El consumer usa `DefaultErrorHandler` con `FixedBackOff`:

- tres reintentos adicionales;
- un segundo de intervalo entre reintentos;
- después de agotarlos, el mensaje se publica en `hotel_search_dlt`.

Los errores permanentes, como un evento con estructura inválida o una
violación SQL no recuperable, van directamente al DLT. Una duplicación por
`search_id` se descarta como redelivery y no se considera un error.

Si la publicación en el DLT falla, el offset original no se confirma. No hay
un consumidor automático del DLT ni un procedimiento de reprocesamiento.

## Configuración y puertos

### Variables de entorno

Variables obligatorias para Docker Compose:

- `ORACLE_PWD`: contraseña administrativa de la instancia Oracle.
- `DB_PASSWORD`: contraseña del usuario de esquema `hotel_search`.

Variables opcionales y sus valores por defecto:

- `APP_PORT=8080`.
- `KAFKA_EXTERNAL_PORT=9092`.
- `ORACLE_PORT=1521`.
- `DB_POOL_MAX_SIZE=10`.
- `SHUTDOWN_TIMEOUT=20s`.
- `KAFKA_CONSUMER_GROUP_ID=hotel-search-consumer`.
- `KAFKA_DELIVERY_TIMEOUT_MS=10000`.
- `KAFKA_REQUEST_TIMEOUT_MS=3000`.
- `KAFKA_CLOSE_TIMEOUT=10s`.
- `KAFKA_CLUSTER_ID=4L6g3nShT-eMCtK--X86sw`.

Dentro de Docker, la aplicación usa el listener interno de Kafka y la base
Oracle `FREEPDB1`. Los puertos `9092` y `1521` se publican para clientes
ejecutados en el host.

### Puertos publicados

- Aplicación HTTP: `http://localhost:8080`.
- Kafka para clientes del host: `localhost:9092`.
- Oracle para clientes del host: `localhost:1521`.

Para cambiar los puertos publicados, modificá `APP_PORT`,
`KAFKA_EXTERNAL_PORT` u `ORACLE_PORT` en `.env` antes de iniciar el stack.

## Builds y tests

### Build habitual

```bash
./gradlew build
```

Compila el proyecto, ejecuta los tests unitarios, verifica el umbral de
JaCoCo y genera el JAR ejecutable. Los integration tests no se ejecutan en
este comando.

Para regenerar todos los artefactos desde cero:

```bash
./gradlew clean build
```

### Build completo

```bash
./gradlew build -PintegrationTest=true
```

Ejecuta los tests unitarios y los integration tests con Kafka y Oracle reales
mediante Testcontainers. Docker debe estar disponible.

El comando equivalente desde cero es:

```bash
./gradlew clean build -PintegrationTest=true
```

### Informe de cobertura

La tarea de cobertura exige al menos 80 % de líneas y branches sobre el
código de producción incluido en la medición. Para generar el informe:

```bash
./gradlew jacocoTestReport
```

Reportes disponibles:

- Tests unitarios: `build/reports/tests/test/index.html`.
- Tests de integración: `build/reports/tests/integrationTest/index.html`.
- Cobertura: `build/reports/jacoco/test/html/index.html`.

## Operación con Docker Compose

Mostrar el estado de los servicios:

```bash
docker compose ps
```

Seguir los logs de la aplicación:

```bash
docker compose logs -f app
```

Detener y remover los contenedores, conservando los volúmenes:

```bash
docker compose down
```

Detener el stack y eliminar también los volúmenes de Kafka y Oracle:

```bash
docker compose down -v
```

El último comando borra los datos persistidos. Usalo solamente cuando quieras
comenzar desde cero.

## Limitaciones del entorno local

- Kafka usa un único broker en modo KRaft.
- Cada topic tiene una partición y replication factor 1.
- No hay alta disponibilidad ni réplicas de Kafka.
- La entrega es at-least-once, no Exactly-once.
- No existe atomicidad distribuida entre Kafka y Oracle.
- El DLT no tiene consumidor automático ni procedimiento de reprocesamiento.
- No hay idempotencia HTTP: un reintento del cliente puede crear otro
  `searchId`.
- La aplicación no implementa autenticación ni autorización; el entorno está
  pensado para ejecución local.

Estas restricciones son deliberadas y forman parte del diseño del proyecto.
