package com.hotelsearch.infrastructure.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Configuración de la capa JDBC.
 *
 * <p>El DataSource lo provee Spring Boot (HikariCP) a partir de las
 * propiedades de spring.datasource. Este bean hace explícito el
 * JdbcTemplate que consumen los adaptadores de persistencia.</p>
 */
@Configuration
public class DataSourceConfig {

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
