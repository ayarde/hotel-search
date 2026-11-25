package com.hotelsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Reglas de dependencia entre capas verificadas sobre las fuentes.
 *
 * <p>La arquitectura hexagonal exige que domain y application no conozcan
 * el framework. Recorrer las fuentes evita sumar una librería de reglas de
 * arquitectura para una sola verificación.</p>
 */
class LayerDependencyRulesTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java", "com", "hotelsearch");
    private static final String SPRING_PACKAGE = "org.springframework";

    /**
     * Verifica que ninguna clase de la capa de aplicación referencia Spring.
     *
     * Condiciones:
     * - Fuentes de com.hotelsearch.application, incluidos los puertos
     *
     * Se espera:
     * - Ningún archivo contenga referencias a org.springframework
     */
    @Test
    void sources_ApplicationLayer_DoNotReferenceSpring() {
        // given
        Path applicationSources = MAIN_SOURCES.resolve("application");

        // when
        List<Path> offenders = filesReferencingSpring(applicationSources);

        // then
        assertThat(offenders)
            .as("La capa de aplicación no debe depender de Spring")
            .isEmpty();
    }

    /**
     * Verifica que ninguna clase del dominio referencia Spring.
     *
     * Condiciones:
     * - Fuentes de com.hotelsearch.domain
     *
     * Se espera:
     * - Ningún archivo contenga referencias a org.springframework
     */
    @Test
    void sources_DomainLayer_DoNotReferenceSpring() {
        // given
        Path domainSources = MAIN_SOURCES.resolve("domain");

        // when
        List<Path> offenders = filesReferencingSpring(domainSources);

        // then
        assertThat(offenders)
            .as("El dominio no debe depender de Spring")
            .isEmpty();
    }

    // <editor-fold desc="Helpers">
    private static List<Path> filesReferencingSpring(Path root) {
        assertThat(root).as("El directorio de fuentes debe existir: " + root).isDirectory();
        try (Stream<Path> files = Files.walk(root)) {
            return files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(LayerDependencyRulesTest::referencesSpring)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean referencesSpring(Path source) {
        try {
            return Files.readString(source).contains(SPRING_PACKAGE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    // </editor-fold>
}
