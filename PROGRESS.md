# Hotel Search — Progreso de implementación

Estado del proyecto por fases, con el alcance ya implementado, su
validación y lo que queda pendiente.

Última actualización: 2026-11-12 · Rama `main` · Fase 5 implementada

## Estado general

Fases 0, 1, 2, 3, 4, 5 y 8 implementadas y validadas. Las fases 6, 7 y 9
siguen pendientes.

El flujo completo que ya funciona es: POST REST → publicación de un evento
en `hotel_availability_searches` → consumer Kafka → validación estructural →
persistencia transaccional en Oracle → confirmación del offset → GET REST con
la búsqueda y su cantidad de equivalentes.

## Fases implementadas

### Fase 0 — Bootstrap del proyecto

- Commit: `b23e974`
- Spring Boot 4.1.2 con Java 25, Gradle 9.9.0 y Kotlin DSL en un único
  proyecto (sin Maven ni submódulos).
- Group `com.hotelsearch`, artefacto `hotel-search`, estructura hexagonal
  inicial y `domain` sin dependencias de Spring.

### Fase 1 — Dominio y casos de uso

- Commit: `4e31b4c`
- Modelos inmutables (`HotelSearch`, `HotelSearchEvent`) y value objects
  (`SearchId`, `HotelId`, `CheckIn`, `CheckOut`, `Ages`) con sus reglas de
  validación.
- Casos de uso `SubmitHotelSearch`, `CountHotelSearch` y `PersistHotelSearch`,
  con `Clock` inyectado para las reglas temporales.
- Puertos `HotelSearchPublisher` y `HotelSearchRepository`.

### Fase 2 — Persistencia Oracle y migraciones

- Commit: `86cadb3`
- Spring JDBC con HikariCP, Flyway y migración `V1__initial_schema.sql`
  (tabla `hotel_search`, PK `search_id`, CHECK de fechas e índice
  `idx_hotel_search_match`).
- `JdbcHotelSearchRepository`: INSERT directo sin SELECT previo, duplicado por
  PK descartado y resto de errores SQL propagados; consulta de búsqueda y
  count en una sola sentencia con bind parameters.
- Integration tests contra Oracle Free 23.26.3 real.

### Fase 3 — Kafka producer

- Commit: `e603710`
- `KafkaHotelSearchPublisher`: publica con `searchId` como key y espera el
  ACK del broker; un fallo o timeout se propaga como
  `HotelSearchUnavailableException`, que la API REST traduce a 503. No se
  abre transacción Oracle durante la publicación.
- Topics `hotel_availability_searches` y `hotel_search_dlt` creados con una
  partición y replication factor 1.
- Producer con `acks=all`, idempotencia habilitada y timeouts configurables.

### Fase 4 — Kafka consumer, persistencia, reintentos y DLT

- Commit: `bf9e833`
- `HotelSearchKafkaListener` sobre `hotel_availability_searches` con
  `AckMode.RECORD`: el offset se confirma después del commit de Oracle.
- `HotelSearchEventMapper` valida la estructura del evento (UUIDv4, fechas y
  edades) y `InvalidHotelSearchEventException` marca los errores
  permanentes.
- `PersistHotelSearch` es transaccional (`@Transactional`) en la capa
  `application`; el listener no captura ni envuelve excepciones de
  persistencia.
- `KafkaConsumerConfig` con `DefaultErrorHandler`, `FixedBackOff(1000 ms, 3
  reintentos)` y `DeadLetterPublishingRecoverer` hacia `hotel_search_dlt`;
  los errores permanentes van directos al DLT y, si falla la publicación al
  DLT, el offset original no se confirma.
- Sin consumidor del DLT, sin Outbox, sin XA y sin Exactly-once.
- Codec JSON migrado a Jackson 3 (`JacksonJsonSerializer` /
  `JacksonJsonDeserializer`).
- Normalización de paquetes: `domain.usecase` → `application`,
  `domain.port` → `application.port` y `config` → `infrastructure.config`.

### Fase 5 — API REST y contrato HTTP

- Implementación consolidada en el commit de la Fase 5.
- `HotelSearchController` expone `POST /search` y `GET /count`. El POST
  responde 202 Accepted con el `searchId` solo después del ACK de Kafka; el
  GET devuelve la búsqueda completa y el count sin incluir la propia
  búsqueda.
- DTOs inmutables en `infrastructure.input.rest.dto`, con validación HTTP de
  UUID, campos obligatorios y edades. El mapper convierte las fechas
  `dd/MM/yyyy` a `LocalDate` de forma estricta.
- Jackson rechaza propiedades JSON desconocidas y el filtro
  `RequestBodySizeLimitFilter` impone un límite real de 16 KB, incluyendo
  requests sin un `Content-Length` confiable.
- `RestExceptionHandler` devuelve Problem Details RFC 9457 para 400, 404 y
  503. `SearchNotFoundException` distingue un ID inexistente de un ID
  inválido; `HotelSearchUnavailableException` representa la
  indisponibilidad de Kafka.
- OpenAPI 3 con springdoc 3.2.0: `/v3/api-docs` y Swagger UI en
  `/swagger-ui.html`. La documentación incluye esquemas, respuestas y la
  ventana de consistencia eventual entre POST y GET.
- Controller tests y tests de integración REST con Kafka y Oracle reales;
  la suite verifica 202/400/404/503, body mayor de 16 KB, campos
  desconocidos, fechas y UUID inválidos, y el efecto del orden de `ages`
  sobre la equivalencia.

### Fase 8 — JaCoCo, suite de tests y build final

- Commit: `ce7a944`
- Source set `intTest` con tarea `integrationTest` activada con
  `-PintegrationTest=true` e integrada en `check`.
- JaCoCo 0.8.14 con verificación mínima del 80 % sobre `LINE` y `BRANCH`
  del código de producción (excluidos `config/`, `*Configuration.class`,
  `HotelSearchApplication` e `infrastructure/output/kafka/`).

## Fases pendientes

### Fase 6 — Virtual Threads, logging y configuración operativa

- Virtual Threads, HikariCP externalizado (máximo 10 conexiones), logging
  estructurado JSON con MDC, correlación por `searchId` y observation
  habilitada en producer y consumer.

### Fase 7 — Docker, Docker Compose y Flyway end-to-end

- Dockerfile multi-stage (build con JDK 25, runtime con JRE 25 y usuario no
  root), Compose con app, Kafka KRaft y Oracle Free, health checks,
  volúmenes persistentes y `.env.example`.

### Fase 9 — Documentación y entrega

- README con propósito, arquitectura, comandos Docker y Gradle, contrato
  HTTP, consistencia eventual, política de reintentos y DLT.

## Validaciones actuales

Comandos ejecutados con la implementación de la Fase 5:

```bash
./gradlew clean build
./gradlew clean build -PintegrationTest=true
```

- Unit tests: 95 pruebas en 18 clases, 0 fallos, 0 errores, 0 omitidas.
- Integration tests: 19 pruebas en 5 clases, 0 fallos, 0 errores, 0 omitidas.
  Requieren Docker porque levantan Kafka 4.4.0 y Oracle Free 23.26.3 reales.
- Cobertura JaCoCo sobre el código de producción: `LINE` 92,4 %, `BRANCH`
  93,2 %, `INSTRUCTION` 92,1 % y `METHOD` 91,0 % (umbral bloqueante: 80 % en
  líneas y branches).

## Estructura de paquetes actual

- `com.hotelsearch.domain`: modelos, value objects y reglas de negocio.
- `com.hotelsearch.application`: casos de uso (`SubmitHotelSearch`,
  `CountHotelSearch`, `PersistHotelSearch`) y excepciones específicas para
  búsqueda inexistente y Kafka no disponible.
- `com.hotelsearch.application.port`: `HotelSearchPublisher` y
  `HotelSearchRepository`.
- `com.hotelsearch.infrastructure.input.rest`: controller, DTOs, mapper HTTP,
  filtro de body y manejo de Problem Details.
- `com.hotelsearch.infrastructure.input.kafka`: listener, mapper del evento
  y excepción de evento inválido.
- `com.hotelsearch.infrastructure.output.kafka`: publisher Kafka.
- `com.hotelsearch.infrastructure.output.persistence`: repositorio JDBC.
- `com.hotelsearch.infrastructure.config`: configuración de Spring, Kafka,
  JDBC, topics y OpenAPI.

## Decisiones vigentes

- Consistencia eventual con entrega at-least-once; no hay atomicidad
  distribuida entre Kafka y Oracle.
- El commit de Oracle ocurre antes de la confirmación del offset Kafka.
- La redelivery del mismo `searchId` no crea una segunda fila: la PK actúa
  como defensa de redelivery.
- Errores permanentes al DLT; errores transitorios con 3 reintentos
  adicionales y 1 segundo de intervalo.
- JaCoCo verifica `LINE` y `BRANCH` con mínimo 80 %; las demás métricas se
  miden en el informe, pero no bloquean el build.
- Sin idempotencia HTTP, sin Outbox, sin XA y sin consumidor automático del
  DLT.

## Notas para la continuación

- `AGENTS.md`, `IMPLEMENTATION-PLAN.md`, `REQUIREMENTS.md`,
  `TECHNICAL-DECISIONS.md` y `TESTING_RULES.md` siguen sin trackear en Git;
  son documentos de trabajo, no código de la aplicación.
- Al cerrar el contexto de la prueba de integración del consumer, el cliente
  Kafka deja logs `WARN` de conexión a `localhost:9092` porque Testcontainers
  ya detuvo el broker. Es ruido de apagado, no un fallo.
- El escenario de fallo transitorio simula la caída de Oracle pausando el
  contenedor y evictando conexiones del pool, por lo que requiere acceso al
  socket de Docker.
- El siguiente incremento corresponde a la Fase 6: Virtual Threads, logging
  estructurado, correlación y configuración operativa, sin modificar el
  contrato HTTP ni las garantías Kafka/Oracle.
