package com.hotelsearch.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de beans de la aplicación mediante Java Configuration.
 *
 * <p>Provee el {@link Clock} inyectable utilizado por las reglas temporales
 * del dominio (validación de fechas de check-in/check-out). El dominio no
 * depende de Spring; este bean se resuelve en la capa de aplicación.</p>
 */
@Configuration
public class AppConfig {

    /**
     * Reloj del sistema en UTC. Puede ser reemplazado por un reloj fijo en
     * pruebas para hacer determinista la validación de fechas.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
