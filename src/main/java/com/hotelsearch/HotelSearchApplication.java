package com.hotelsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicación Hotel Search.
 *
 * <p>Arranque local:</p>
 * <pre>{@code ./gradlew bootRun}</pre>
 *
 * <p>La aplicación inicializa la conexión con Oracle, el producer y el
 * consumer Kafka, y aplica las migraciones de Flyway al arrancar.</p>
 */
@SpringBootApplication
public class HotelSearchApplication {

    /**
     * Arranca la aplicación Spring Boot.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(HotelSearchApplication.class, args);
    }
}
