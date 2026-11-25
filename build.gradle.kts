plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    id("java")
    id("jacoco")
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

configurations {
    named("intTestImplementation") {
        extendsFrom(configurations.implementation.get(), configurations.testImplementation.get())
    }
    named("intTestRuntimeOnly") {
        extendsFrom(configurations.runtimeOnly.get(), configurations.testRuntimeOnly.get())
    }
}

// Forzar versiones seguras para CVEs en todas las configuraciones
configurations.configureEach {
    resolutionStrategy {
        // CVE-2026-110879: Jackson 3.2.4
        force("tools.jackson.core:jackson-databind:3.2.4")
        force("tools.jackson.core:jackson-core:3.2.4")
        force("com.fasterxml.jackson.core:jackson-annotations:2.22")
        // CVE-2026-113602: json-smart 2.6.1
        force("net.minidev:json-smart:2.6.1")
        // CVE-2026-119078: commons-codec 1.23.0
        force("commons-codec:commons-codec:1.23.0")
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
    // CVE-2026-110879: forzar Jackson 3.2.4 (versiones estrictas)
    implementation(enforcedPlatform("tools.jackson:jackson-bom:3.2.4"))
    // Dependencias explícitas para garantizar las versiones
    implementation("tools.jackson.core:jackson-databind:3.2.4")
    implementation("tools.jackson.core:jackson-core:3.2.4")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.22")
    // CVE-2026-119078: commons-codec 1.23.0
    implementation("commons-codec:commons-codec:1.23.0")
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)
    // CVE-2026-113602: json-smart ≤2.6.0 vulnerable; forzar versión segura
    testImplementation("net.minidev:json-smart:2.6.1")
    // CVE-2026-110879: alinear Jackson con BOM 3.2.4 (estricto)
    testImplementation(enforcedPlatform("tools.jackson:jackson-bom:3.2.4"))
    testImplementation("tools.jackson.core:jackson-databind:3.2.4")

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

tasks.named("check") {
    dependsOn(integrationTest)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
