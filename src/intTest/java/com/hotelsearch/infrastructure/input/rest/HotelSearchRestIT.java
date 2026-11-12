package com.hotelsearch.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.hotelsearch.infrastructure.input.rest.dto.HotelSearchCountResponse;
import com.hotelsearch.infrastructure.input.rest.dto.HotelSearchRequest;
import com.hotelsearch.infrastructure.input.rest.dto.SubmitHotelSearchResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.oracle.OracleContainer;

/**
 * Integration test del contrato REST completo con Kafka y Oracle reales.
 *
 * <p>Verifica el flujo POST → Kafka → consumer → Oracle → GET, la
 * consistencia eventual, la equivalencia sensible al orden de las edades y
 * la publicación de la descripción OpenAPI.</p>
 *
 * Condiciones del entorno:
 * - Kafka 4.4.0 pinneado con un broker real
 * - Oracle Free 23.26.3 pinneado con la imagen oficial de Testcontainers
 * - La aplicación corre en un servidor HTTP real sobre un puerto aleatorio
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HotelSearchRestIT {

    @Container
    private static final KafkaContainer kafka =
        new KafkaContainer("apache/kafka:4.4.0")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    @Container
    @ServiceConnection
    private static final OracleContainer oracle =
        new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    private static final DateTimeFormatter HTTP_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
    private static final String HOTEL_ID = "550e8400-e29b-41d4-a716-446655440000";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RestClient restClient;
    private LocalDate checkIn;
    private LocalDate checkOut;

    @BeforeEach
    void setUp() {
        restClient = RestClient.create("http://localhost:" + port);
        checkIn = LocalDate.now(ZoneOffset.UTC).plusDays(7);
        checkOut = checkIn.plusDays(3);
        jdbcTemplate.update("DELETE FROM hotel_search");
    }

    /**
     * Verifica el flujo REST completo y la equivalencia de búsquedas.
     *
     * Condiciones:
     * - Se registran dos búsquedas con las mismas edades en el mismo orden
     * - Se registra una tercera búsqueda con las edades en otro orden
     *
     * Se espera:
     * - Cada POST responda 202 después del ACK de Kafka
     * - Las dos primeras búsquedas se cuenten como equivalentes entre sí
     * - La tercera búsqueda no sea equivalente a las anteriores
     * - OpenAPI documente ambos endpoints y la consistencia eventual
     */
    @Test
    void api_PostsSearches_ReturnsExpectedCountsAndPublishesOpenApi() {
        // given
        SubmitHotelSearchResponse first = post(List.of(29, 28, 4, 3));
        SubmitHotelSearchResponse second = post(List.of(29, 28, 4, 3));

        // when
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
            assertThat(persistedRows()).isEqualTo(2));
        HotelSearchCountResponse firstCount = count(first.searchId());
        HotelSearchCountResponse secondCount = count(second.searchId());

        SubmitHotelSearchResponse differentOrder = post(List.of(28, 29, 4, 3));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
            assertThat(persistedRows()).isEqualTo(3));
        HotelSearchCountResponse differentOrderCount = count(differentOrder.searchId());
        String openApi = restClient.get()
            .uri("/v3/api-docs")
            .retrieve()
            .requiredBody(String.class);

        // then
        assertAll(
            () -> assertThat(firstCount.searchCount()).isEqualTo(1),
            () -> assertThat(secondCount.searchCount()).isEqualTo(1),
            () -> assertThat(differentOrderCount.searchCount()).isEqualTo(0),
            () -> assertThat(firstCount.ages()).containsExactly(29, 28, 4, 3),
            () -> assertThat(openApi).contains("\"/search\""),
            () -> assertThat(openApi).contains("\"/count\""),
            () -> assertThat(openApi).contains("HotelSearchRequest"),
            () -> assertThat(openApi).contains("HotelSearchCountResponse"),
            () -> assertThat(openApi).contains("202"),
            () -> assertThat(openApi).contains("503"),
            () -> assertThat(openApi).contains("asíncrona")
        );
    }

    /**
     * Verifica que un searchId mal formado se rechace con 400 usando el caso
     * de uso real.
     *
     * Condiciones:
     * - El query parameter no contiene un UUID
     *
     * Se espera:
     * - El servidor responda 400
     */
    @Test
    void api_InvalidSearchId_ReturnsBadRequest() {
        // given
        String invalidSearchId = "no-es-un-uuid";

        // when / then
        assertThatThrownBy(() -> count(invalidSearchId))
            .isInstanceOf(RestClientResponseException.class)
            .satisfies(exception -> assertThat(
                ((RestClientResponseException) exception).getStatusCode().value())
                .isEqualTo(400));
    }

    // <editor-fold desc="Helpers">
    private SubmitHotelSearchResponse post(List<Integer> ages) {
        HotelSearchRequest request = new HotelSearchRequest(
            HOTEL_ID,
            HTTP_DATE_FORMATTER.format(checkIn),
            HTTP_DATE_FORMATTER.format(checkOut),
            ages);
        ResponseEntity<SubmitHotelSearchResponse> response = restClient.post()
            .uri("/search")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toEntity(SubmitHotelSearchResponse.class);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private HotelSearchCountResponse count(String searchId) {
        HotelSearchCountResponse response = restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/count").queryParam("searchId", searchId).build())
            .retrieve()
            .requiredBody(HotelSearchCountResponse.class);
        assertThat(response).isNotNull();
        return response;
    }

    private int persistedRows() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM hotel_search", Integer.class);
        return count == null ? 0 : count;
    }
    // </editor-fold>
}
