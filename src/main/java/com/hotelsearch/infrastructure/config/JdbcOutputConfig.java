package com.hotelsearch.infrastructure.config;

import com.hotelsearch.application.port.HotelSearchRepository;
import com.hotelsearch.infrastructure.output.persistence.JdbcHotelSearchRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Configuración de adaptadores de salida JDBC.
 *
 * <p>Define los beans de infraestructura para la persistencia de búsquedas
 * en Oracle. El {@link JdbcHotelSearchRepository} implementa el puerto
 * {@link HotelSearchRepository} y se configura como bean singleton. Al ser
 * un bean de Spring queda proxyado, lo que hace efectivo el
 * {@code @Transactional} de su método {@code save}.</p>
 */
@Configuration
public class JdbcOutputConfig {

    @Bean
    public HotelSearchRepository hotelSearchRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcHotelSearchRepository(jdbcTemplate);
    }
}
