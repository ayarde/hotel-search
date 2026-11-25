package com.hotelsearch.infrastructure.config;

import com.hotelsearch.application.CountHotelSearch;
import com.hotelsearch.application.PersistHotelSearch;
import com.hotelsearch.application.SubmitHotelSearch;
import com.hotelsearch.application.port.HotelSearchPublisher;
import com.hotelsearch.application.port.HotelSearchRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de beans de la aplicación mediante Java Configuration.
 *
 * <p>Provee el {@link Clock} inyectable utilizado por las reglas temporales
 * del dominio y configura los casos de uso de la aplicación con sus puertos.
 * El dominio no depende de Spring; estos beans se resuelven en la capa de
 * aplicación.</p>
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
     * Caso de uso que registra una búsqueda y la publica en Kafka.
     *
     * @param publisher puerto de publicación Kafka
     * @param clock reloj inyectable para las reglas temporales
     * @return caso de uso configurado
     */
    @Bean
    public SubmitHotelSearch submitHotelSearch(HotelSearchPublisher publisher, Clock clock) {
        return new SubmitHotelSearch(publisher, clock);
    }

    /**
     * Caso de uso que consulta una búsqueda y cuenta sus equivalentes.
     *
     * @param repository repositorio de búsquedas
     * @return caso de uso configurado
     */
    @Bean
    public CountHotelSearch countHotelSearch(HotelSearchRepository repository) {
        return new CountHotelSearch(repository);
    }

    /**
     * Caso de uso que persiste en Oracle la búsqueda recibida por Kafka.
     * No es transaccional: la transacción la abre el adaptador JDBC en
     * {@code JdbcHotelSearchRepository.save}, de modo que el commit termina
     * antes de que el listener retorne.
     *
     * @param repository repositorio de búsquedas
     * @return caso de uso configurado
     */
    @Bean
    public PersistHotelSearch persistHotelSearch(HotelSearchRepository repository) {
        return new PersistHotelSearch(repository);
    }
}
