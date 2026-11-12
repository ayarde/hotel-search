package com.hotelsearch.infrastructure.config;

import com.hotelsearch.application.PersistHotelSearch;
import com.hotelsearch.application.port.HotelSearchRepository;
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

    /**
     * Servicio de aplicación que persiste una búsqueda en Oracle. La
     * anotación transaccional se aplica sobre el bean de Spring para que
     * el commit de la base termine antes de que el listener retorne.
     */
    @Bean
    public PersistHotelSearch persistHotelSearch(HotelSearchRepository repository) {
        return new PersistHotelSearch(repository);
    }
}
