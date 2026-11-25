# Hotel Search — Plan de correcciones post-revisión

## Contexto

Este plan surge de la revisión externa del challenge. Agrupa cinco
puntos a corregir y dos sugerencias. Sigue la misma forma de trabajo que
`IMPLEMENTATION-PLAN.md`. Antes de cada fase se presentan el alcance, los
criterios de aceptación, los cambios y las validaciones. Al cerrar una
fase se muestran el diff y los resultados reales. No se avanza a la
siguiente fase mientras haya un criterio obligatorio en falla.

Todos los tests nuevos o modificados cumplen `TESTING_RULES.md`.

## Decisiones previas al plan

El contrato de `hotelId` se resolvió antes de empezar el plan, porque
contradecía decisiones cerradas. `REQUIREMENTS.md` y
`TECHNICAL-DECISIONS.md` ya están actualizados:

- `hotelId` es texto obligatorio de 1 a 64 caracteres, por ejemplo
  `1234aBc`. No se exige formato UUID.
- No admite espacios en blanco en ninguna posición
  (`Character.isWhitespace`). No se aplica trim silencioso ni cambio de
  mayúsculas, y la comparación es exacta.
- La columna `hotel_id` pasa a `VARCHAR2(64 CHAR)` con una migración
  nueva. Las migraciones ya aplicadas, como `V1`, no se editan.

También quedó definido cómo se miden los code smells: con el plugin
`pmd` de Gradle, sin levantar un servidor. El evaluador no informó qué
herramienta usó, así que los 67 hallazgos que reportó pueden no
coincidir uno a uno con los de PMD. Si la informa, se ajusta el ruleset.

------------------------------------------------------------------------

## Fase 10 — Contrato de `hotelId` como texto

Corresponde al punto 1 del feedback y es la corrección de mayor
prioridad.

### Alcance

- Alinear el código con el contrato definido en `REQUIREMENTS.md` §3.
- Actualizar `README.md` y los ejemplos de request y evento con
  `1234aBc`. `README.md` describe el comportamiento real de la API, así
  que se actualiza junto con el código.
- `HotelId` pasa a ser `record HotelId(String value)`. Valida que el
  valor no sea nulo ni vacío, que no tenga espacios en blanco y que no
  supere los 64 caracteres. Se eliminan el patrón UUID y la
  conversión con `UUID.fromString`.
- Ajustar los usos de `HotelId.of(UUID)` y de `hotelId().value()` como
  UUID en `SubmitHotelSearch`, `HotelSearch`, `HotelSearchEvent`,
  `HotelSearchEventMapper`, `HotelSearchRestMapper`, los DTOs REST y
  `JdbcHotelSearchRepository`.
- Actualizar la anotación OpenAPI del DTO: quitar `format: uuid` y poner
  el ejemplo `1234aBc`.
- Crear la migración `V2__hotel_id_free_text.sql` con
  `ALTER TABLE hotel_search MODIFY (hotel_id VARCHAR2(64 CHAR))` y
  actualizar su comentario.
- Se mantiene la comparación exacta de `hotelId` en el count, sensible a
  mayúsculas: `1234aBc` y `1234abc` son hoteles distintos.

Fuera de alcance: cambiar `searchId`, que sigue siendo un UUIDv4
generado por el servidor.

### Criterios de aceptación

- `POST /search` con `"hotelId": "1234aBc"` y el resto de los datos
  válidos responde 202 con un `searchId`.
- `GET /count?searchId=...` de esa búsqueda devuelve `hotelId`
  `1234aBc` sin alteraciones y el conteo esperado.
- Responden 400 con un mensaje claro: `hotelId` nulo, vacío, con
  espacios (por ejemplo `"1234 aBc"`, `" 1234aBc"` o un tab) y con más
  caracteres que el máximo.
- Se sigue aceptando un `hotelId` con formato UUID, porque también es
  texto válido.
- Flyway aplica `V2` sobre una base que ya tiene `V1`, sin errores de
  checksum.
- Se puede ejecutar la prueba de carga documentada en `README.md`.

### Validación

- Unit tests de `HotelIdTest` para el valor válido, los límites (1
  carácter, el máximo y máximo + 1), los espacios y los nulos.
- Actualizar los tests de mappers, casos de uso y controller que usan
  UUID como `hotelId`.
- Integration tests: `JdbcHotelSearchRepositoryIT` persiste y cuenta un
  `hotelId` alfanumérico de longitud máxima, y `HotelSearchRestIT` cubre
  el flujo completo con `1234aBc`.
- `./gradlew build -PintegrationTest=true`.
- `docker compose up --build` y una prueba manual de POST y luego GET
  con `1234aBc`.

Riesgo: cambia un contrato consumido (formato HTTP, evento Kafka y
esquema). El evento serializa `hotelId` como el objeto
`{"value": "..."}` del record y esa forma no cambia, así que los
mensajes en vuelo con un UUID siguen deserializándose.

------------------------------------------------------------------------

## Fase 11 — Capa de aplicación libre de Spring

Corresponde al punto 2 del feedback.

### Alcance

- Quitar `@Transactional` y el import de Spring de
  `PersistHotelSearch`.
- Llevar la demarcación transaccional al adaptador
  `JdbcHotelSearchRepository.save`, con `@Transactional` sobre el bean
  que crea `JdbcOutputConfig`, o con un `TransactionTemplate` inyectado
  si el adaptador no queda proxyado. Se elige una sola de las dos
  opciones y se justifica en el Javadoc.
- Actualizar el Javadoc de `AppConfig.persistHotelSearch`, que hoy
  menciona la anotación transaccional.
- Agregar una regla verificable: ninguna clase de
  `com.hotelsearch.application` ni de `com.hotelsearch.domain` importa
  `org.springframework`. Se implementa como unit test que recorre las
  fuentes o como regla PMD (ver Fase 13), sin sumar dependencias
  nuevas si no hace falta.

### Criterios de aceptación

- `application` y `domain` no tienen imports de Spring.
- El commit de Oracle sigue ocurriendo antes de que el listener retorne
  y se confirme el offset.
- Un duplicado por PK sigue distinguiéndose de otros errores SQL.

### Validación

- `grep -rn "org.springframework" src/main/java/com/hotelsearch/application src/main/java/com/hotelsearch/domain`
  no devuelve resultados.
- `PersistHotelSearchTest` y `JdbcHotelSearchRepositoryTest` siguen en
  verde.
- `HotelSearchKafkaConsumerIT` (duplicado, commit de offset y DLT) en
  verde con `-PintegrationTest=true`.

------------------------------------------------------------------------

## Fase 12 — Cobertura real sin exclusiones artificiales

Corresponde al punto 3 del feedback.

### Alcance

- Quitar de `coverageExcludes` en `build.gradle.kts` la exclusión
  `**/infrastructure/output/kafka/**`.
- Revisar `**/config/**` y `**/*Configuration.class`. Solo se excluye la
  configuración pura, es decir, las declaraciones de beans sin lógica
  (`DataSourceConfig`, `JdbcOutputConfig`, `KafkaOutputConfig`,
  `OpenApiConfig`, `AppConfig`) y `HotelSearchApplication`.
  `KafkaConsumerConfig` (153 líneas, con el error handler y la
  clasificación de errores) y `KafkaProducerConfig` (101 líneas) tienen
  lógica y quedan medidos.
- Completar los tests de `KafkaHotelSearchPublisher`: ACK exitoso,
  `ExecutionException` con `TimeoutException`, `ExecutionException`
  con otra causa o sin causa, `InterruptedException` (que restablece el
  flag de interrupción) y `KafkaException` síncrona.
- Agregar los tests que falten en `KafkaConsumerConfig` y
  `KafkaProducerConfig` para las ramas que no se cubren hoy.
- Pasar las exclusiones a una lista explícita de clases en lugar de
  globs amplios, con un comentario que explique por qué se excluye cada
  una.

### Criterios de aceptación

- JaCoCo con al menos 80% de líneas y branches, medido con las
  exclusiones reducidas.
- El reporte informa el número real y la cobertura sin ninguna
  exclusión, para dar transparencia.
- `jacocoTestCoverageVerification` falla si la cobertura baja del
  umbral.

### Validación

- `./gradlew clean build` y revisar
  `build/reports/jacoco/test/html/index.html`.
- Registrar en `PROGRESS.md` la cobertura de líneas y branches antes y
  después del cambio.

------------------------------------------------------------------------

## Fase 13 — Análisis estático y code smells

Corresponde al punto 4 del feedback.

### Alcance

- Agregar el plugin `pmd` de Gradle con la versión fijada en
  `gradle/libs.versions.toml` y un ruleset en
  `config/pmd/ruleset.xml`.
- Ejecutar PMD sobre `main`, registrar la cantidad inicial de hallazgos
  y corregirlos por categoría, en commits chicos.
- Configurar `maxFailures = 10` para que el build falle si se supera el
  límite pedido.
- Suprimir una regla solo con una justificación escrita en el ruleset o
  con un `@SuppressWarnings("PMD.Regla")` comentado. Nunca se desactiva
  una categoría entera para bajar el número.

### Criterios de aceptación

- `./gradlew pmdMain` informa 10 hallazgos o menos.
- `./gradlew build` ejecuta PMD como parte de `check`.
- Ninguna corrección cambia el comportamiento observable: la suite
  completa sigue en verde.

### Validación

- `./gradlew pmdMain` y revisar `build/reports/pmd/main.html`.
- `./gradlew build -PintegrationTest=true`.
- Registrar en `PROGRESS.md` la cantidad de hallazgos antes y después.

------------------------------------------------------------------------

## Fase 14 — Agrupar aserciones con `assertAll`

Corresponde al punto 5 del feedback.

### Alcance

Agrupar con `assertAll` las verificaciones independientes de los tests
que encadenan aserciones sueltas. Según un relevamiento inicial, los
casos más grandes son:

- `OperationalConfigurationTest.context_OperationalProperties_ConfigureVirtualThreadsHikariAndTimeouts`.
- `SubmitHotelSearchTest.submit_ValidRequest_ReturnsSearchIdAndPublishesEvent`.
- `KafkaHotelSearchPublisherIT.logging_CorrelationScope_WritesJsonWithCorrelationFields`.
- `HotelSearchEventMapperTest.toHotelSearch_ValidEvent_ReturnsImmutableDomainSearch`.
- Los tests con 4 a 6 aserciones en `KafkaHotelSearchPublisherTest`,
  `HotelSearchRestMapperTest`, `HotelSearchTest`,
  `JdbcHotelSearchRepositoryIT`, `KafkaHotelSearchPublisherIT`,
  `HotelSearchKafkaConsumerIT`, `KafkaConsumerConfigTest`,
  `SearchIdTest`, `HotelSearchRestIT` y `RestExceptionHandlerTest`.

Las aserciones que dependen de una anterior (por ejemplo, un
`assertNotNull` antes de desreferenciar) quedan fuera del grupo, delante
de `assertAll`. Las verificaciones de Mockito (`verify`) se mantienen en
el bloque `then` según `TESTING_RULES.md`.

### Criterios de aceptación

- Ningún test tiene más de 2 aserciones independientes fuera de un
  `assertAll`.
- Los tests siguen la estructura given/when/then y el naming de
  `TESTING_RULES.md`.
- La cobertura no baja.

### Validación

- Repetir el relevamiento de aserciones por método y adjuntar el
  resultado.
- `./gradlew build -PintegrationTest=true`.

------------------------------------------------------------------------

## Fase 15 — Sugerencias: constraints de versiones y `validSearchId`

### Alcance

Versiones fijadas por CVE:

- Reemplazar las declaraciones `implementation` usadas solo para fijar
  versiones (`jackson-annotations`, `commons-codec` y las de
  `jackson-databind` y `jackson-core` duplicadas con el BOM) por un
  bloque `dependencies { constraints { ... } }` con `because("CVE-...")`.
- Pasar `json-smart` de `testImplementation` a constraint.
- Revisar si `resolutionStrategy.force` sigue siendo necesario con los
  constraints y el `enforcedPlatform`. Si queda redundante, quitarlo.
- Pasar las versiones al catálogo `gradle/libs.versions.toml`.

`RequestCorrelationFilter.validSearchId`:

- Devolver `Optional<String>` en lugar de `null`.
- Registrar en `DEBUG` el valor descartado, truncado y sanitizado para
  no habilitar log injection, sin la traza completa.

### Criterios de aceptación

- `./gradlew dependencies --configuration runtimeClasspath` y
  `testRuntimeClasspath` resuelven las mismas versiones seguras que hoy
  (Jackson 3.2.4, jackson-annotations 2.22, commons-codec 1.23.0 y
  json-smart 2.6.1).
- `jackson-annotations` y `commons-codec` no aparecen como dependencias
  directas.
- `validSearchId` no devuelve `null`. Un `searchId` inválido no entra al
  MDC y queda registrado en DEBUG.

### Validación

- Comparar la salida de `./gradlew dependencies` antes y después del
  cambio.
- `RequestCorrelationFilterTest`: casos de `searchId` válido, ausente,
  vacío e inválido, verificando el MDC y el log.
- `./gradlew build -PintegrationTest=true`.

------------------------------------------------------------------------

## Orden y commits

1. Fase 10: contrato de `hotelId`. Va primero porque desbloquea el uso
   real de la API y la prueba de carga.
2. Fase 11: capa de aplicación libre de Spring.
3. Fase 12: cobertura real.
4. Fase 13: análisis estático. Va después de la Fase 12 para que los
   tests nuevos también pasen por PMD.
5. Fase 14: `assertAll`. Va después de las Fases 10 a 13 para no
   reescribir dos veces los mismos tests.
6. Fase 15: sugerencias.

Cada fase cierra con su propio commit, o con varios si supera las 400
líneas cambiadas. Se usa Conventional Commits y se actualiza
`PROGRESS.md` al final de cada fase. La Fase 10 va en un commit aislado
porque cambia un contrato y el esquema.

## Definition of Done del plan

- Las seis fases cumplen sus criterios de aceptación.
- `./gradlew build -PintegrationTest=true` pasa.
- JaCoCo supera el 80% de líneas y branches con exclusiones reducidas a
  la configuración pura.
- PMD informa 10 hallazgos o menos.
- `docker compose up --build` levanta el stack y el flujo POST y luego
  GET funciona con `hotelId` `1234aBc`.
- `REQUIREMENTS.md`, `TECHNICAL-DECISIONS.md`, `README.md` y
  `PROGRESS.md` reflejan el estado final.
