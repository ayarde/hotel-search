# Hotel Search — Progreso de implementación

Estado del proyecto por fases, con el alcance ya implementado, su
validación y lo que queda pendiente.

Última actualización: 2026-11-25 · Rama `docs/cierre-plan-correcciones` · Fases 0–15 implementadas

## Estado general

Fases 0 a 9 implementadas y validadas. Después de la revisión externa se
abrió `CORRECTIONS-PLAN.md` (Fases 10 a 15); las seis fases están
implementadas.

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
- `PersistHotelSearch` era transaccional (`@Transactional`) en la capa
  `application`; desde la Fase 11 la transacción la abre el adaptador JDBC; el listener no captura ni envuelve excepciones de
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

- Commit: `8817c78`
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

### Fase 6 — Virtual Threads, logging y configuración operativa

- Commit: `a6e0082`
- Virtual Threads habilitados con `spring.threads.virtual.enabled` y HikariCP
  configurado con un máximo inicial de 10 conexiones.
- Bootstrap servers, JDBC, credenciales, timeouts del producer, timeout de
  cierre de Kafka y timeout de shutdown externalizados mediante variables de
  entorno.
- Logging estructurado JSON con el formato nativo `logstash` de Spring Boot
  4.1. El MDC incorpora `requestId`, `searchId`, `traceId`, `spanId` y
  `component`, y se restaura al finalizar cada operación, también ante
  errores.
- El producer propaga `searchId` y el contexto de tracing en headers Kafka;
  el consumer recupera esa correlación. Las observations están habilitadas
  explícitamente en `KafkaTemplate` y en el listener container.
- Micrometer Tracing usa el bridge OpenTelemetry únicamente para correlación.
  No se incorpora ningún exportador OTLP, Zipkin o Jaeger.
- Shutdown web graceful con timeout por fase configurable y cierre acotado del
  producer Kafka.
- Context test de configuración, tests unitarios del MDC y tests de
  integración que verifican el JSON, los headers de correlación y la
  propagación W3C del tracing.

### Fase 7 — Docker, Docker Compose y Flyway end-to-end

- Commit: `b96827f`
- `Dockerfile` multi-stage: builder con Eclipse Temurin JDK 25 y runtime con
  JRE 25. El runtime usa el usuario no root `hotelsearch` (UID 10001) y
  contiene únicamente el JAR ejecutable de Spring Boot.
- `compose.yaml` con los servicios `app`, `kafka` y `oracle`. La aplicación
  depende de que Kafka y Oracle estén `healthy` antes de iniciar.
- Kafka 4.4.0 en KRaft combinado, con listener interno para la aplicación,
  listener externo para el host y listener de controller no publicado.
- Oracle Database Free 23.26.3.0 oficial, con volumen persistente en
  `/opt/oracle/oradata` y script idempotente para aprovisionar el usuario
  dedicado `hotel_search` y sus permisos de esquema.
- Health checks reales para Kafka y Oracle, volúmenes nombrados para Kafka y
  Oracle, y tiempo de gracia para el shutdown graceful de los tres servicios.
- `.env.example` versionado con puertos, timeouts y credenciales de ejemplo;
  `.env` continúa ignorado y `.dockerignore` evita incluir secretos en el
  contexto de build.
- Flyway aplica `V1__initial_schema.sql` automáticamente al arrancar y
  conserva el historial entre reinicios.

### Fase 8 — JaCoCo, suite de tests y build final

- Commit: `ce7a944`
- Source set `intTest` con tarea `integrationTest` activada con
  `-PintegrationTest=true` e integrada en `check`.
- JaCoCo 0.8.14 con verificación mínima del 80 % sobre `LINE` y `BRANCH`
  del código de producción (excluidos `config/`, `*Configuration.class`,
  `HotelSearchApplication` e `infrastructure/output/kafka/`).

### Fase 9 — Documentación y entrega

- Commit: `d601f44`
- `README.md` documenta propósito, arquitectura, stack, requisitos,
  onboarding, configuración de `.env`, puertos, API, ejemplos, OpenAPI,
  builds, operación de Docker Compose y ubicaciones de los reportes.
- La guía explica la consistencia eventual, el orden entre el commit de
  Oracle y el ACK de Kafka, la entrega at-least-once, los tres reintentos
  adicionales, el DLT y las limitaciones del broker único.
- El comando principal `docker compose up --build`, los dos comandos de build
  y la eliminación opcional de volúmenes están documentados.
- Los ejemplos generan el check-in con la fecha local para no quedar
  obsoletos por la ventana temporal del dominio. El README muestra tanto el
  resultado `searchCount = 0` para una única búsqueda como el resultado
  `searchCount = 2` al consultar una de tres búsquedas equivalentes.
- `.env` continúa ignorado, `.env.example` solo contiene valores de ejemplo
  y el README no incorpora secretos.

### Fase 10 — Contrato de `hotelId` como texto

- Rama: `fix/fase-10-hotel-id-texto`. Commits: `125a003` (dominio y
  adaptadores) y `3a11b2d` (migración e integration tests); la
  documentación va en el commit que cierra la fase.
- `HotelId` pasa a texto de 1 a 64 caracteres, sin espacios en blanco, sin
  trim ni cambio de mayúsculas. La validación está en el constructor
  canónico, así que también aplica al deserializar el evento Kafka.
- El DTO HTTP valida longitud y espacios con Bean Validation y OpenAPI
  muestra el ejemplo `1234aBc`. Un `hotelId` con formato UUID sigue siendo
  válido.
- El DTO toma el máximo de `HotelId.MAX_LENGTH` y comparte una sola regex
  portable (`HotelSearchRequest.HOTEL_ID_PATTERN`) entre Bean Validation y
  OpenAPI. Un test recorre cada carácter del plano BMP y verifica que la
  regex y `HotelId` aceptan exactamente lo mismo.
- `V2__hotel_id_free_text.sql` amplía `hotel_id` a `VARCHAR2(64 CHAR)` sin
  editar `V1`.
- Validación: `./gradlew build -PintegrationTest=true` en verde con 115
  unit tests y 23 integration tests, sin fallos ni tests omitidos.
- `docker compose up --build` sobre un volumen nuevo: Flyway aplicó 2
  migraciones. Tres POST con `hotelId` `1234aBc` respondieron 202 y el
  count devolvió `1234aBc` sin cambios con `searchCount: 2`. Un `hotelId`
  con espacio y uno de 65 caracteres respondieron 400 con su mensaje.

### Fase 11 — Capa de aplicación libre de Spring

- Rama: `refactor/fase-11-aplicacion-sin-spring`.
- `PersistHotelSearch` ya no importa Spring ni declara `@Transactional`.
- La transacción se declara con `@Transactional` en
  `JdbcHotelSearchRepository.save`. Se eligió la anotación y no un
  `TransactionTemplate` porque el bean lo crea `JdbcOutputConfig` y Spring
  lo proxya; `save` nunca se autoinvoca. El duplicado por PK se sigue
  descartando dentro de la transacción y cualquier otro error SQL se
  propaga y provoca rollback.
- Se actualizaron los Javadocs de `AppConfig.persistHotelSearch` y
  `JdbcOutputConfig`, `TECHNICAL-DECISIONS.md` §6 y §8 y el diagrama de
  flujo del `README.md`.
- Regla verificable: `LayerDependencyRulesTest` recorre las fuentes de
  `application` y `domain` y falla si alguna referencia
  `org.springframework`. No suma dependencias.
- `JdbcHotelSearchRepositoryTest` verifica que `save` está anotado y
  `JdbcHotelSearchRepositoryIT` verifica que el bean real es un proxy AOP con
  `TransactionInterceptor`.
- Validación: `grep -rn "org.springframework"` sobre `application` y
  `domain` sin resultados. `./gradlew build -PintegrationTest=true` en verde
  con 124 unit tests y 24 integration tests (incluido
  `HotelSearchKafkaConsumerIT`: duplicado, commit de offset y DLT), sin
  fallos ni tests omitidos. JaCoCo: `LINE` 93,5 % y `BRANCH` 91,3 %.

### Fase 12 — Cobertura real sin exclusiones artificiales

- Rama: `test/fase-12-cobertura-real`.
- `coverageExcludes` pasa de globs amplios a una lista explícita de clases,
  cada una con su motivo: `HotelSearchApplication`, `AppConfig`,
  `DataSourceConfig`, `JdbcOutputConfig`, `KafkaOutputConfig` y
  `OpenApiConfig`. Se quitaron `**/config/**`, `**/*Configuration.class` y
  `**/infrastructure/output/kafka/**`; `KafkaConsumerConfig`,
  `KafkaProducerConfig` y `KafkaHotelSearchPublisher` quedan medidos.
- Nueva tarea `jacocoUnfilteredReport`, sin exclusiones y solo informativa
  (`build/reports/jacoco/unfiltered/html/index.html`). `test` genera ambos
  reportes y luego corre `jacocoTestCoverageVerification`.
- `KafkaHotelSearchPublisherTest`: ACK exitoso (topic, key y value),
  timeout con la cadena de causas, `ExecutionException` sin causa,
  `InterruptedException` que restablece el flag de interrupción, además de
  los casos existentes de otra causa y `KafkaException` síncrona.
- `KafkaConsumerConfigTest`: consumer factory (propiedades, deserializadores,
  JSON válido e inválido), cada error no reintentable va al DLT en el primer
  intento y el listener declarado persiste el evento mapeado.
- `KafkaProducerConfigTest` (nuevo, con `ApplicationContextRunner`): valores
  por defecto del producer, overrides externos, JSON sin headers de tipo,
  propiedades del `KafkaTemplate` y topics con 1 partición y 1 réplica.
- Mutaciones manuales de control: quitar `interrupt()` del publisher o
  `BadSqlGrammarException` de la lista no reintentable hace fallar los tests.
- Umbral: con el mínimo subido temporalmente a 0,99,
  `jacocoTestCoverageVerification` falla con "Rule violated for bundle
  hotel-search"; se restauró a 0,80.
- Cobertura antes, con las exclusiones originales: `LINE` 93,7 % (326/348) y
  `BRANCH` 91,5 % (86/94). Con las exclusiones reducidas y sin tests nuevos:
  `LINE` 84,9 % (372/438) y `BRANCH` 90,8 % (89/98). Sin ninguna exclusión:
  `LINE` 81,6 % (373/457) y `BRANCH` 90,8 % (89/98).
- Cobertura después, con las exclusiones reducidas: `LINE` 95,0 % (416/438) y
  `BRANCH` 91,8 % (90/98). Sin ninguna exclusión: `LINE` 91,2 % (417/457) y
  `BRANCH` 91,8 % (90/98).
- Validación: `./gradlew clean build -PintegrationTest=true` en verde con 142
  unit tests y 24 integration tests, sin fallos ni tests omitidos.
- Correcciones de la revisión nativa (aprobada, hallazgos no bloqueantes):
  `KafkaProducerConfigTest` enlaza `KafkaProperties` desde el entorno y
  verifica `spring.kafka.template.*` por comportamiento observable (la
  observation del envío y `close(7s)` del producer), sin reflexión sobre
  campos privados. El evento de prueba vive en `HotelSearchEventFixtures`,
  los tests usan imports en lugar de nombres calificados y `assertThrows`
  de forma uniforme, y el test del listener compara la búsqueda completa.

### Fase 13 — Análisis estático y code smells

- Rama: `refactor/fase-13-analisis-estatico`.
- Plugin `pmd` de Gradle con PMD 7.29.0, fijado en
  `gradle/libs.versions.toml`, y ruleset propio en `config/pmd/ruleset.xml`.
  Analiza `main`, corre como parte de `check` y tiene `maxFailures = 10`.
- El ruleset incluye las ocho categorías de reglas Java completas. Se
  excluyen diez reglas puntuales, cada una con su motivo:
  `MethodArgumentCouldBeFinal`, `LocalVariableCouldBeFinal`,
  `LocalVariableDeclarationShouldBeAtStartOfBlock`, `UseExplicitTypes`,
  `AtLeastOneConstructor`, `LongVariable`, `ShortMethodName`,
  `OnlyOneReturn`, `CallSuperInConstructor` y `LawOfDemeter`. Hay tres
  reglas ajustadas por propiedades: `CommentRequired` no exige comentarios
  en campos, `CommentSize` admite hasta 20 líneas de 100 caracteres y
  `EmptyCatchBlock` acepta un catch vacío si tiene un comentario.
- Hallazgos antes: 396 con todas las reglas de las ocho categorías, sin
  exclusiones, y 53 con el ruleset final (21 de documentación, 12 de error
  prone, 6 de best practices, 6 de code style, 3 de multithreading, 3 de
  design y 2 de performance). Después: 0.
- Correcciones: `serialVersionUID` en las cuatro excepciones, imports sin
  uso, nombres calificados innecesarios, campos de `KafkaProducerConfig` que
  coincidían con métodos `@Bean`, la causa conservada en el error de fecha
  del mapper REST, una constante para la versión de UUID y otra para el
  título "Solicitud inválida", y la agrupación de errores de validación
  con streams en lugar de loops. `KafkaHotelSearchPublisher` baja su
  complejidad ciclomática separando la traducción de fallos y protege el
  log DEBUG con `isDebugEnabled`. Además, `Javadoc` en los constructores y
  métodos públicos que no lo tenían.
- `RequestBodySizeLimitFilter` captura `IOException` en lugar de
  `Exception`. Es el único checked que puede lanzar `sendError`; si el
  resolver de Spring lanzara una excepción runtime, ahora se propaga tal
  cual en vez de llegar envuelta en `ServletException`. En los dos casos el
  contenedor responde 500.
- Siete `@SuppressWarnings("PMD...")`, cada uno con un comentario:
  `TooManyMethods` en `RestExceptionHandler`, `DataClass` en
  `CorrelationContext`, `ImplicitFunctionalInterface` en
  `HotelSearchPublisher`, `UseConcurrentHashMap` en un mapa local de
  `RequestCorrelationFilter`, `CloseResource` en el serializador que cierra
  el producer factory, `DoNotUseThreads` al restablecer el flag de
  interrupción y `AvoidCatchingGenericException` en
  `HotelSearchEventMapper`, que traduce los nulos de
  `Objects.requireNonNull`.
- Nuevo test `publish_DebugEnabled_LogsBrokerMetadata`: fija el log DEBUG
  con la metadata del broker y cubre la guarda.
- Correcciones de la revisión nativa (review-9abfca2dcf38b252, aprobada;
  se atendieron los hallazgos no bloqueantes): comentario ampliado de
  `maxFailures = 10` (máximo de CORRECTIONS-PLAN.md, hoy 0 hallazgos),
  Javadoc corregido de `publicationFailure` (el log es el mismo; la
  distinción timeout/otros está en el mensaje de la excepción) y cuatro
  tests nuevos: `sendError` con `IOException`, `RuntimeException` del
  resolver propagada tal cual, logger en INFO sin lectura de metadata y
  `FieldError` sin mensaje. Este último fija que `messageOf` devuelve
  "valor inválido", así que el `Map.entry` nunca recibe un valor nulo.
  Validación final: `./gradlew build -PintegrationTest=true` en verde con
  148 unit tests y 24 integration tests, PMD con 0 hallazgos y JaCoCo con
  exclusiones reducidas en `LINE` 95,5 % (421/441) y `BRANCH` 91,7 % (88/96).
- Validación: `./gradlew pmdMain` informa 0 hallazgos
  (`build/reports/pmd/main.html`). Al quitar temporalmente una exclusión,
  PMD encuentra 15 hallazgos y el build falla con "15 PMD rule violations
  were found". `./gradlew clean build -PintegrationTest=true` en verde con
  143 unit tests y 24 integration tests, y `pmdMain` dentro de `check`.
  Con el test nuevo, `./gradlew build` pasa con 144 unit tests. JaCoCo con
  exclusiones reducidas: `LINE` 95,0 % (419/441) y `BRANCH` 91,7 % (88/96).
  Sin exclusiones: `LINE` 91,3 % (420/460) y `BRANCH` 91,7 % (88/96).

### Fase 14 — Agrupar aserciones con `assertAll`

- Rama: `test/fase-14-assertall`.
- Relevamiento: un script cuenta, por método de test, las llamadas
  `assert*` y `assertThat` que quedan fuera de un `assertAll`. No cuenta los
  `verify` de Mockito ni el `assertThrows` cuyo resultado se guarda, porque
  ese es el paso `when` y las aserciones siguientes dependen de él.
- Antes: 35 de 161 métodos tenían más de 2 aserciones sueltas (28 unit y
  7 integration). Después: 0 de 161.
- Las guardas de dependencia quedan delante del grupo: el `isNotNull` del
  `searchId` y del evento publicado en `SubmitHotelSearchTest`, el
  `getStartupFailure()` de los context tests, el `executed.await` antes de
  leer el hilo, el `isPresent` antes de `Optional.get()` y los headers antes
  de leer su valor. En `HotelSearchKafkaConsumerIT` cada tamaño y el acceso
  al primer elemento que protege comparten una misma lambda del
  `assertAll`, para que se ejecuten en orden.
- Las esperas con Awaitility se agrupan como ejecutables del `assertAll`; se
  siguen evaluando en el mismo orden.
- Validación: `./gradlew clean build -PintegrationTest=true` en verde con
  148 unit tests y 24 integration tests, sin fallos ni tests omitidos. PMD
  sigue en 0 hallazgos. JaCoCo con exclusiones reducidas queda igual que
  en la validación final de la Fase 13, después de las correcciones de la
  revisión: `LINE` 95,5 % (421/441) y `BRANCH` 91,7 % (88/96). El 95,0 %
  (419/441) que figura arriba es la medición anterior a esas correcciones.

### Fase 15 — Sugerencias: constraints de versiones y `validSearchId`

- Rama: `chore/fase-15-sugerencias`.
- Las versiones fijadas por CVE (Jackson 3.2.4, jackson-annotations 2.22,
  commons-codec 1.23.0 y json-smart 2.6.1) pasan al catálogo
  `gradle/libs.versions.toml` y se declaran como `constraints` con
  `because("CVE-...")`. Se quitan las dependencias directas usadas solo para
  fijar versiones, incluido `json-smart` en `testImplementation`.
- Hallazgo: el plugin `io.spring.dependency-management` reescribe los
  constraints, el `enforcedPlatform` y `resolutionStrategy.force` con las
  versiones que gestiona Spring Boot. Solo con constraints se resolvía
  `jackson-databind` 3.1.7 y `json-smart` 2.6.0, y con `force` pasaba lo
  mismo: lo que fijaba las versiones eran las dependencias directas. Se
  quitó `force`, porque no tenía efecto, y las mismas coordenadas del
  catálogo se declaran en `dependencyManagement { dependencies { ... } }`,
  que es lo que el plugin respeta. Se evaluó reemplazar el plugin por
  `platform(SpringBootPlugin.BOM_COORDINATES)`: funciona sin `force`, pero
  agrega `aopalliance` 1.0 al runtime y cambia la gestión de todas las
  dependencias, así que se mantuvo el plugin.
- `./gradlew dependencies` antes y después resuelve las mismas versiones en
  `compileClasspath`, `runtimeClasspath`, `testRuntimeClasspath` e
  `intTestRuntimeClasspath`. La única diferencia es que `commons-codec` ya
  no aparece en runtime, compile y test, porque solo lo traía la declaración
  directa; en integration tests lo trae Testcontainers y resuelve 1.23.0.
- `RequestCorrelationFilter.validSearchId` devuelve `Optional<String>`. Un
  `searchId` inválido no entra al MDC y se registra en `DEBUG` sin la traza,
  recortado a 64 caracteres y con los caracteres de control reemplazados
  por `_`, para que un CR o LF no pueda abrir una línea de log falsa.
- Observaciones de la revisión, resueltas en la misma fase:
  - El sanitizado también reemplaza los separadores Unicode U+2028 y U+2029,
    que algunos visores de logs muestran como salto de línea.
  - La tarea `verifyCvePinnedVersions`, que corre en `check`, falla si
    alguna librería fijada por CVE resuelve una versión distinta de la del
    catálogo en `runtimeClasspath`, `testRuntimeClasspath` o
    `intTestRuntimeClasspath`. Así, si un cambio del plugin o de Spring Boot
    vuelve a pisar el pin, el build no pasa en silencio. Se comprobó quitando
    el pin: la tarea falla y lista los 12 desvíos.
- Validación final, con las observaciones incluidas:
  `./gradlew clean build -PintegrationTest=true` en verde con 153 unit tests
  y 24 integration tests, sin fallos ni tests omitidos, y con
  `verifyCvePinnedVersions` ejecutada dentro de `check`. PMD en 0
  hallazgos. JaCoCo con exclusiones reducidas: `LINE` 95,6 % (433/453) y
  `BRANCH` 93,6 % (103/110).

### PMD sobre los tests

- Rama: `chore/pmd-test-ruleset`.
- Ruleset propio para unit e integration tests en
  `config/pmd/ruleset-test.xml`, aplicado por `pmdTest` y `pmdIntTest`,
  que corren en `check` con el mismo límite de 10 hallazgos. Parte de las
  mismas categorías y exclusiones que el ruleset principal, excluye lo que
  en tests solo es ruido y agrega reglas propias para `TESTING_RULES.md`:
  naming `method_State`, helpers privados al final de la clase e imágenes
  de Testcontainers con versión fijada.
- `LinguisticNaming` se excluye: tests como `toCommand_ValidRequest_...`
  empiezan con el nombre del método bajo prueba, como pide §2, y la regla
  los toma como conversiones que devuelven void.
- El Javadoc obligatorio de §4 no se verifica con PMD: los tests son
  package-private y `CommentRequired` solo controla miembros públicos y
  protegidos. Sigue siendo revisión manual.
- Las reglas `TestHelpersAtEnd` y `PinnedContainerImage` se probaron con un
  fixture fuera del repo: marcan imágenes sin tag, `:latest` y un helper
  entre dos tests, y no marcan una imagen con versión.
- Hallazgos iniciales: 34 en `src/test` y 39 en `src/intTest`. Se
  corrigieron en el código: containers y logger `static final` en
  mayúsculas, como el patrón de §8; campos antes de los métodos; variables
  `record` renombradas a `consumerRecord`; `_` para variables sin uso;
  `a`/`b` renombradas a `first`/`second`; imports y nombres calificados
  innecesarios. Tres `@SuppressWarnings("PMD.CloseResource")` comentados:
  el deserializador que se consulta del factory y el scope que el test
  cierra a mano dos veces.
- `TESTING_RULES.md` §5 decía que los tests son públicos; se corrigió a
  package-private, que es lo que exige JUnit 5 y lo que ya usaba el código.
- Validación: `./gradlew clean build -PintegrationTest=true` en verde con
  153 unit tests y 24 integration tests, sin fallos ni tests omitidos. PMD
  con 0 hallazgos en `main`, `test` e `intTest`. JaCoCo sin cambios: `LINE`
  95,6 % (433/453) y `BRANCH` 93,6 % (103/110).

## Validaciones actuales

### Cierre del plan de correcciones (Fases 10 a 15)

- `./gradlew clean build -PintegrationTest=true`, ejecutado al cerrar la
  Fase 15: 153 unit tests y 24 integration tests, sin fallos ni tests
  omitidos. `check` incluye `pmdMain` y `verifyCvePinnedVersions`. Desde
  "PMD sobre los tests", también `pmdTest` y `pmdIntTest`, con el mismo
  resultado de tests y cobertura.
- Cobertura JaCoCo con exclusiones reducidas a la configuración pura:
  `LINE` 95,6 % (433/453) y `BRANCH` 93,6 % (103/110). Umbral bloqueante:
  80 % en líneas y branches.
- PMD: 0 hallazgos sobre el código de producción, los unit tests y los
  integration tests (límite: 10 por tarea).
- Docker Compose, validado en la Fase 10 sobre un volumen nuevo: Flyway
  aplica `V1` y `V2`, el POST con `hotelId` `1234aBc` responde 202 y el GET
  devuelve `1234aBc` sin cambios. Las Fases 11 a 15 no tocaron Compose, el
  Dockerfile ni las migraciones, y el stack no se volvió a levantar después
  de ellas.

### Validación del stack en la Fase 9

Comandos ejecutados después de implementar la Fase 9, tanto en el working
tree como en una copia limpia del estado entregable. Los números de esta
sección corresponden a ese momento:

```bash
./gradlew clean build
./gradlew clean build -PintegrationTest=true
./gradlew jacocoTestReport
docker compose config --quiet
docker compose up --build -d
docker compose ps
```

- Unit tests: 110 pruebas en 21 clases, 0 fallos, 0 errores, 0 omitidas.
- Integration tests: 22 pruebas en 5 clases, 0 fallos, 0 errores, 0 omitidas.
  Requieren Docker porque levantan Kafka 4.4.0 y Oracle Free 23.26.3 reales.
- Cobertura JaCoCo sobre el código de producción: `LINE` 93,5 %, `BRANCH`
  91,3 %, `INSTRUCTION` 93,0 % y `METHOD` 91,8 % (umbral bloqueante: 80 % en
  líneas y branches).
- La configuración de Compose es válida y levanta los tres servicios. Kafka y
  Oracle alcanzan estado `healthy`; la aplicación responde HTTP y se ejecuta
  con UID 10001.
- La guía se validó desde una copia limpia del estado entregable, con un
  proyecto Compose y volúmenes nuevos. Ambos builds finalizan
  correctamente y el stack se levantó siguiendo el README.
- OpenAPI responde `200` en `/v3/api-docs` y Swagger UI responde `200`
  después de seguir su redirección.
- El flujo end-to-end desde la copia limpia devuelve `202` en dos POST con
  búsquedas equivalentes, genera `searchId` distintos y responde
  `searchCount = 1` en el GET.
- El stack de validación se detuvo con `docker compose down -v`; sus volúmenes
  temporales fueron eliminados.
- Flyway crea `hotel_search`, registra la versión 1 como aplicada (la única
  migración en ese momento; `V2` se agregó en la Fase 10) y valida el
  esquema sin nuevas migraciones después de reiniciar el stack.
- El flujo end-to-end devuelve 202 en dos POST con búsquedas equivalentes,
  genera `searchId` distintos y responde `searchCount = 1` en los GET.
- Tras `docker compose down` y un nuevo arranque, la búsqueda y su conteo
  siguen disponibles, lo que confirma la persistencia del volumen Oracle.
- Los topics `hotel_availability_searches` y `hotel_search_dlt` quedan con
  una partición y replication factor 1.

## Estructura de paquetes actual

- `com.hotelsearch.domain`: modelos, value objects y reglas de negocio.
- `com.hotelsearch.application`: casos de uso (`SubmitHotelSearch`,
  `CountHotelSearch`, `PersistHotelSearch`) y excepciones específicas para
  búsqueda inexistente y Kafka no disponible.
- `com.hotelsearch.application.port`: `HotelSearchPublisher` y
  `HotelSearchRepository`.
- `com.hotelsearch.infrastructure.input.rest`: controller, DTOs, mapper HTTP,
  filtros de body y correlación, y manejo de Problem Details.
- `com.hotelsearch.infrastructure.input.kafka`: listener, mapper del evento
  y excepción de evento inválido.
- `com.hotelsearch.infrastructure.output.kafka`: publisher Kafka.
- `com.hotelsearch.infrastructure.output.persistence`: repositorio JDBC.
- `com.hotelsearch.infrastructure.config`: configuración de Spring, Kafka,
  JDBC, topics y OpenAPI.
- `com.hotelsearch.infrastructure.observability`: nombres compartidos de
  correlación y scopes delimitados para el MDC.

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
- PMD corre en `check` sobre el código de producción y los tests, cada uno
  con su ruleset, y el build falla con más de 10 hallazgos en una tarea.
- Sin idempotencia HTTP, sin Outbox, sin XA y sin consumidor automático del
  DLT.
- Micrometer Tracing se usa solo para logs y correlación; no hay exportador
  ni backend de tracing.

## Notas para la continuación

- La documentación de soporte del proyecto (`AGENTS.md`,
  `IMPLEMENTATION-PLAN.md`, `REQUIREMENTS.md`, `TECHNICAL-DECISIONS.md` y
  `TESTING_RULES.md`) está versionada en Git junto con `PROGRESS.md` y
  `README.md`.
- Al cerrar el contexto de la prueba de integración del consumer, el cliente
  Kafka deja logs `WARN` de conexión a `localhost:9092` porque Testcontainers
  ya detuvo el broker. Es ruido de apagado, no un fallo.
- El escenario de fallo transitorio simula la caída de Oracle pausando el
  contenedor y evictando conexiones del pool, por lo que requiere acceso al
  socket de Docker.
- La Fase 9 y la documentación de soporte quedan completadas y registradas
  en Git.
- El plan de correcciones de `CORRECTIONS-PLAN.md` (Fases 10 a 15) está
  cerrado. No hay fases pendientes; cualquier cambio nuevo de diseño se
  contrasta primero con `REQUIREMENTS.md` y `TECHNICAL-DECISIONS.md`.
