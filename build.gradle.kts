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

// Análisis estático; `check` depende de `pmdMain`, `pmdTest` y `pmdIntTest`.
// Los source sets se fijan acá, después de crear intTest. El código de
// producción usa config/pmd/ruleset.xml y los tests, ruleset-test.xml;
// pmdIntTest solo analiza el código, no levanta containers, así que corre
// siempre, sin -PintegrationTest=true. Cada tarea falla si PMD informa más de
// 10 hallazgos: ese es el máximo que fija CORRECTIONS-PLAN.md, no un
// objetivo. Hoy hay 0 hallazgos, y los reportes (build/reports/pmd/) listan
// cualquier hallazgo nuevo aunque el build siga pasando, así que conviene
// revisarlos.
pmd {
    toolVersion = libs.versions.pmd.get()
    sourceSets = listOf(project.sourceSets.main.get(), project.sourceSets.test.get(), intTest)
    ruleSetFiles = files("config/pmd/ruleset.xml")
    ruleSets = emptyList()
    maxFailures = 10
    isConsoleOutput = true
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

// Las reglas TestHelpersAtEnd y PinnedContainerImage son XPath. Si una
// actualización de PMD cambia el nombre de un nodo o de un atributo, o alguien
// edita la consulta, la regla puede dejar de marcar nada y pmdTest seguiría
// en verde. Esta verificación analiza los fixtures de config/pmd/fixtures
// (no se compilan ni pertenecen a ningún source set) con ruleset-test.xml y
// compara las líneas marcadas contra las esperadas. El classpath de intTest
// permite que PMD resuelva DockerImageName como tipo.
val pmdFixtures =
    tasks.register<Pmd>("pmdFixtures") {
        description = "Analiza los fixtures de config/pmd/fixtures con ruleset-test.xml."
        group = "verification"
        source = fileTree("config/pmd/fixtures")
        ruleSetFiles = files("config/pmd/ruleset-test.xml")
        ruleSets = emptyList()
        classpath = intTest.compileClasspath
        ignoreFailures = true
        isConsoleOutput = false
        reports {
            xml.required.set(true)
            html.required.set(false)
            xml.outputLocation.set(layout.buildDirectory.file("reports/pmd/fixtures.xml"))
        }
    }

val verifyPmdTestRules =
    tasks.register("verifyPmdTestRules") {
        description = "Verifica que las reglas XPath de ruleset-test.xml marquen los fixtures esperados."
        group = "verification"
        dependsOn(pmdFixtures)
        val report = layout.buildDirectory.file("reports/pmd/fixtures.xml")
        // Archivo:línea:regla. Los helpers sin marcar y las imágenes con tag
        // no figuran a propósito.
        val expected =
            setOf(
                "TestHelpersAtEndFixture.java:10:TestHelpersAtEnd",
                "PinnedContainerImageFixture.java:7:PinnedContainerImage",
                "PinnedContainerImageFixture.java:8:PinnedContainerImage",
                "PinnedContainerImageFixture.java:9:PinnedContainerImage",
            )
        val checkedRules = setOf("TestHelpersAtEnd", "PinnedContainerImage")
        doLast {
            val document =
                javax.xml.parsers.DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(report.get().asFile)
            val files = document.getElementsByTagName("file")
            val actual = mutableSetOf<String>()
            for (i in 0 until files.length) {
                val file = files.item(i) as org.w3c.dom.Element
                val fileName = file.getAttribute("name").substringAfterLast('/')
                val violations = file.getElementsByTagName("violation")
                for (j in 0 until violations.length) {
                    val violation = violations.item(j) as org.w3c.dom.Element
                    val rule = violation.getAttribute("rule")
                    if (rule in checkedRules) {
                        actual.add("$fileName:${violation.getAttribute("beginline")}:$rule")
                    }
                }
            }
            val missing = (expected - actual).sorted()
            val unexpected = (actual - expected).sorted()
            if (missing.isNotEmpty() || unexpected.isNotEmpty()) {
                throw GradleException(
                    "Las reglas de ruleset-test.xml no marcan lo esperado en config/pmd/fixtures.\n" +
                        "Sin marcar (esperadas): $missing\n" +
                        "Marcadas de más: $unexpected",
                )
            }
        }
    }

tasks.named("check") {
    dependsOn(integrationTest, verifyCvePinnedVersions, verifyPmdTestRules)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
