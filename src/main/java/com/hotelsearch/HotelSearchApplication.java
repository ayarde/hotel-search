package com.hotelsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicación Hotel Search.
 *
 * <p>Arranque local:</p>
 * <pre>{@code ./gradlew bootRun}</pre>
 *
 * <p>Nota: en Fase 0 no se configura ningún DataSource (la persistencia
 * Oracle se agrega en la Fase 2), por lo que el arranque levanta solo
 * la capa web de Spring Boot.</p>
 */
@SpringBootApplication
public class HotelSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotelSearchApplication.class, args);
    }
}
