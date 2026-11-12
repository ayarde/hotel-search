package com.hotelsearch.config;

import com.hotelsearch.domain.port.HotelSearchRepository;
import com.hotelsearch.infrastructure.output.persistence.JdbcHotelSearchRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Configuración de adaptadores de salida JDBC.
 *
 * <p>Define los beans de infraestructura para la persistencia de búsquedas
 * en Oracle. El {@link JdbcHotelSearchRepository} implementa el puerto
 * {@link HotelSearchRepository} y se configura como bean singleton.</p>
 */
@Configuration
public class JdbcOutputConfig {

    @Bean
    public HotelSearchRepository hotelSearchRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcHotelSearchRepository(jdbcTemplate);
    }
}