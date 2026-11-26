plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    id("java")
    id("jacoco")
    id("pmd")
}

group = "com.hotelsearch"
version = "0.0.1"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

jacoco {
    toolVersion = "0.8.14"
    reportsDirectory = layout.buildDirectory.dir("reports/jacoco")
}

// Análisis estático; `check` depende de `pmdMain`, `pmdTest` y `pmdIntTest`
// (los source sets se fijan después de crear intTest). Cada tarea falla si PMD
// informa más de 10 hallazgos: ese es el máximo que fija CORRECTIONS-PLAN.md,
// no un objetivo. Hoy hay 0 hallazgos, y los reportes (build/reports/pmd/)
// listan cualquier hallazgo nuevo aunque el build siga pasando, así que
// conviene revisarlos.
pmd {
    toolVersion = libs.versions.pmd.get()
    ruleSetFiles = files("config/pmd/ruleset.xml")
    ruleSets = emptyList()
    maxFailures = 10
    isConsoleOutput = true
}

// Exclusiones de cobertura: solo clases de configuración pura, es decir,
// declaraciones de beans sin ramas ni reglas propias. Lo que tiene lógica
// (KafkaConsumerConfig, KafkaProducerConfig, adaptadores) queda medido.
val coverageExcludes =
    listOf(
        // Punto de entrada: solo invoca SpringApplication.run.
        "com/hotelsearch/HotelSearchApplication.class",
        // Instancia los casos de uso con sus puertos; no tiene lógica.
        "com/hotelsearch/infrastructure/config/AppConfig.class",
        // Declara el JdbcClient sobre el DataSource de Spring Boot.
        "com/hotelsearch/infrastructure/config/DataSourceConfig.class",
        // Declara el adaptador JDBC del repositorio.
        "com/hotelsearch/infrastructure/config/JdbcOutputConfig.class",
        // Declara el adaptador Kafka del publisher.
        "com/hotelsearch/infrastructure/config/KafkaOutputConfig.class",
        // Metadatos estáticos de OpenAPI.
        "com/hotelsearch/infrastructure/config/OpenApiConfig.class",
    )

val filteredClasses = {
    layout.files(
        sourceSets.main.get().output.classesDirs.files.map { dir ->
            fileTree(dir) {
                exclude(*coverageExcludes.toTypedArray())
            }
        },
    )
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }
    classDirectories.setFrom(filteredClasses())
}

// Reporte informativo sin ninguna exclusión, para transparencia. No bloquea
// el build: el umbral se verifica sobre el reporte con exclusiones reducidas.
val jacocoUnfilteredReport =
    tasks.register<JacocoReport>("jacocoUnfilteredReport") {
        description = "Genera el reporte JaCoCo de unit tests sin exclusiones."
        group = "verification"
        dependsOn(tasks.named("test"))
        executionData(tasks.named<Test>("test").get())
        sourceSets(sourceSets.main.get())
        reports {
            html.required.set(true)
            xml.required.set(true)
            csv.required.set(false)
            html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/unfiltered/html"))
            xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/unfiltered/jacocoUnfilteredReport.xml"))
        }
    }

tasks.withType<JacocoCoverageVerification> {
    classDirectories.setFrom(filteredClasses())
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = BigDecimal("0.80")
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                minimum = BigDecimal("0.80")
            }
        }
    }
}

// Only run coverage verification on main test task (not integrationTest)
tasks.named<Test>("test") {
    finalizedBy(tasks.jacocoTestReport, jacocoUnfilteredReport, tasks.jacocoTestCoverageVerification)
}

repositories {
    mavenCentral()
}

// Source set de integration tests. Debe crearse antes del bloque dependencies
// para que existan las configuraciones intTest* cuando se declaran sus artefactos.
val intTest =
    sourceSets.create("intTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }

// Los tests usan su propio ruleset (config/pmd/ruleset-test.xml). pmdIntTest
// solo analiza el código: no levanta containers, así que corre siempre, sin
// -PintegrationTest=true.
pmd {
    sourceSets = listOf(project.sourceSets.main.get(), project.sourceSets.test.get(), intTest)
}

tasks.named<Pmd>("pmdTest") { ruleSetFiles = files("config/pmd/ruleset-test.xml") }
tasks.named<Pmd>("pmdIntTest") { ruleSetFiles = files("config/pmd/ruleset-test.xml") }

configurations {
    named("intTestImplementation") {
        extendsFrom(configurations.implementation.get(), configurations.testImplementation.get())
    }
    named("intTestRuntimeOnly") {
        extendsFrom(configurations.runtimeOnly.get(), configurations.testRuntimeOnly.get())
    }
}

// Los constraints del bloque dependencies documentan las versiones seguras y su
// CVE, pero no alcanzan solos: el plugin io.spring.dependency-management
// reescribe constraints, BOMs y resolutionStrategy.force con las versiones que
// gestiona Spring Boot (se resolvía jackson-databind 3.1.7 y json-smart
// 2.6.0). Por eso las mismas coordenadas del catálogo también se declaran en
// la gestión de dependencias del plugin, que es la que efectivamente manda.
val cvePinnedLibraries =
    listOf(libs.jackson.databind, libs.jackson.core, libs.jackson.annotations, libs.commons.codec, libs.json.smart)
        .map { library -> library.get() }
        .map { library -> "${library.module}:${library.versionConstraint.requiredVersion}" }

dependencyManagement {
    dependencies {
        cvePinnedLibraries.forEach { dependency(it) }
    }
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.spring.boot.micrometer.tracing.opentelemetry)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.flyway.database.oracle)
    runtimeOnly(libs.ojdbc17)
    // CVE-2026-110879: BOM de Jackson 3.2.4 para los módulos tools.jackson.
    // Las configuraciones de test lo heredan de implementation.
    implementation(enforcedPlatform(libs.jackson.bom))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)

    // Versiones mínimas seguras. Un constraint no agrega la librería al
    // classpath: solo fija la versión cuando otra dependencia la trae.
    constraints {
        implementation(libs.jackson.databind) {
            because("CVE-2026-110879: Jackson anterior a 3.2.4 es vulnerable")
        }
        implementation(libs.jackson.core) {
            because("CVE-2026-110879: Jackson anterior a 3.2.4 es vulnerable")
        }
        implementation(libs.jackson.annotations) {
            because("CVE-2026-110879: versión de jackson-annotations alineada con Jackson 3.2.4")
        }
        implementation(libs.commons.codec) {
            because("CVE-2026-119078: commons-codec anterior a 1.23.0 es vulnerable")
        }
        testImplementation(libs.json.smart) {
            because("CVE-2026-113602: json-smart 2.6.0 o anterior es vulnerable")
        }
    }

    "intTestImplementation"(libs.spring.boot.starter.test)
    "intTestImplementation"(libs.spring.boot.web.server.test)
    "intTestImplementation"(libs.spring.boot.testcontainers)
    "intTestImplementation"(libs.testcontainers.oracle.free)
    "intTestImplementation"(libs.testcontainers.junit.jupiter)
    "intTestImplementation"(libs.testcontainers.kafka)
}

val integrationTest =
    tasks.register<Test>("integrationTest") {
        description = "Ejecuta los integration tests con dependencias reales (Testcontainers)."
        group = "verification"
        testClassesDirs = intTest.output.classesDirs
        classpath = intTest.runtimeClasspath
        useJUnitPlatform()
        shouldRunAfter(tasks.named("test"))
        // Los integration tests no corren por defecto; se activan con -PintegrationTest=true.
        onlyIf { providers.gradleProperty("integrationTest").getOrElse("false") == "true" }
    }

// El pin depende de que el plugin de gestión de dependencias respete su propio
// bloque dependencyManagement. Si un cambio del plugin o de Spring Boot altera
// esa precedencia, una librería fijada podría volver en silencio a una versión
// vulnerable; esta tarea lo detecta en `check`.
val verifyCvePinnedVersions =
    tasks.register("verifyCvePinnedVersions") {
        description = "Verifica que las librerías fijadas por CVE resuelvan la versión del catálogo."
        group = "verification"
        val expectedVersions =
            cvePinnedLibraries.associate { coordinates ->
                coordinates.substringBeforeLast(':') to coordinates.substringAfterLast(':')
            }
        val resolutionResults =
            listOf("runtimeClasspath", "testRuntimeClasspath", "intTestRuntimeClasspath")
                .associateWith { name -> configurations.named(name).map { it.incoming.resolutionResult } }
        doLast {
            val mismatches =
                resolutionResults.flatMap { (configuration, result) ->
                    result.get().allComponents.mapNotNull { component ->
                        val module = component.moduleVersion ?: return@mapNotNull null
                        val expected = expectedVersions["${module.group}:${module.name}"]
                        if (expected == null || module.version == expected) {
                            null
                        } else {
                            "$configuration: ${module.group}:${module.name} resolvió ${module.version}, " +
                                "se esperaba $expected"
                        }
                    }
                }
            if (mismatches.isNotEmpty()) {
                throw GradleException(
                    "Versiones fijadas por CVE alteradas:\n" + mismatches.joinToString("\n"),
                )
            }
        }
    }

tasks.named("check") {
    dependsOn(integrationTest, verifyCvePinnedVersions)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
