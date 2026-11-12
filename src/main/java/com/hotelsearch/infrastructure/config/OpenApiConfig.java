package com.hotelsearch.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de la descripción OpenAPI de la API de búsquedas.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Describe la API y su modelo de consistencia eventual.
     *
     * @return documento OpenAPI base
     */
    @Bean
    public OpenAPI hotelSearchOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Hotel Search API")
            .version("v1")
            .description("API para registrar búsquedas de hotel y consultar sus equivalentes. "
                + "POST devuelve 202 cuando Kafka acepta el evento; la persistencia en Oracle es "
                + "asíncrona y GET puede responder 404 hasta que el consumer complete el commit."));
    }
}
