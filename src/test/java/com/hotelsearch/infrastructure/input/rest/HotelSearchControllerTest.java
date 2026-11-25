package com.hotelsearch.infrastructure.input.rest;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelsearch.application.CountHotelSearch;
import com.hotelsearch.application.HotelSearchUnavailableException;
import com.hotelsearch.application.SearchNotFoundException;
import com.hotelsearch.application.SubmitHotelSearch;
import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests del controller HTTP y de la traducción de errores a Problem Details.
 */
@WebMvcTest(HotelSearchController.class)
@Import({HotelSearchRestMapper.class, RestExceptionHandler.class})
class HotelSearchControllerTest {

    private static final String SEARCH_ID = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String VALID_BODY = """
        {
          "hotelId": "1234aBc",
          "checkIn": "09/11/2026",
          "checkOut": "29/11/2026",
          "ages": [29, 28, 4, 3]
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitHotelSearch submitHotelSearch;

    @MockitoBean
    private CountHotelSearch countHotelSearch;

    /**
     * Verifica que un request válido se procesa y responde 202 con el
     * searchId generado.
     *
     * Condiciones:
     * - El caso de uso devuelve un identificador UUIDv4
     *
     * Se espera:
     * - Status 202
     * - Body JSON con el searchId
     */
    @Test
    void submit_ValidRequest_ReturnsAccepted() throws Exception {
        // given
        SearchId searchId = SearchId.of(SEARCH_ID);
        when(submitHotelSearch.execute(any())).thenReturn(
            new SubmitHotelSearch.Result(searchId, validSearch(searchId)));

        // when
        mockMvc.perform(post("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
            .andExpect(status().isAccepted())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.searchId").value(SEARCH_ID));

        // then
        verify(submitHotelSearch).execute(argThat(command -> "1234aBc".equals(command.hotelId())));
    }

    /**
     * Verifica que un campo obligatorio ausente se rechaza con 400 y un
     * detalle de validación útil.
     *
     * Condiciones:
     * - El body no contiene checkOut
     *
     * Se espera:
     * - Status 400 con Problem Details
     * - El error indique el campo checkOut
     */
    @Test
    void submit_MissingRequiredField_ReturnsBadRequest() throws Exception {
        // given
        String body = """
            {
              "hotelId": "550e8400-e29b-41d4-a716-446655440000",
              "checkIn": "09/11/2026",
              "ages": [29]
            }
            """;

        // when
        mockMvc.perform(post("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors.checkOut[0]").value("checkOut es obligatorio"));

        // then
        verifyNoInteractions(submitHotelSearch);
    }

    /**
     * Verifica que una propiedad JSON desconocida se rechaza con 400.
     *
     * Condiciones:
     * - El body incluye una propiedad no declarada
     *
     * Se espera:
     * - Status 400
     * - El detalle identifique la propiedad desconocida
     */
    @Test
    void submit_UnknownJsonProperty_ReturnsBadRequest() throws Exception {
        // given
        String body = """
            {
              "hotelId": "550e8400-e29b-41d4-a716-446655440000",
              "checkIn": "09/11/2026",
              "checkOut": "29/11/2026",
              "ages": [29],
              "unexpected": true
            }
            """;

        // when
        mockMvc.perform(post("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail", containsString("Propiedad JSON desconocida: unexpected")));

        // then
        verifyNoInteractions(submitHotelSearch);
    }

    /**
     * Verifica que un JSON mal formado se rechaza con 400.
     *
     * Condiciones:
     * - El body termina antes de cerrar el objeto JSON
     *
     * Se espera:
     * - Status 400 con un mensaje sobre el JSON inválido
     */
    @Test
    void submit_MalformedJson_ReturnsBadRequest() throws Exception {
        // given
        String body = "{\"hotelId\":";

        // when
        mockMvc.perform(post("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail", containsString("JSON válido")));

        // then
        verifyNoInteractions(submitHotelSearch);
    }

    /**
     * Verifica que una fecha con formato o calendario inválido se rechace con
     * 400 antes de publicar el evento.
     *
     * Condiciones:
     * - El checkIn corresponde al 31 de febrero
     *
     * Se espera:
     * - Status 400 con el nombre del campo en el detalle
     */
    @Test
    void submit_InvalidDate_ReturnsBadRequest() throws Exception {
        // given
        String body = """
            {
              "hotelId": "550e8400-e29b-41d4-a716-446655440000",
              "checkIn": "31/02/2026",
              "checkOut": "29/11/2026",
              "ages": [29]
            }
            """;

        // when
        mockMvc.perform(post("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail", containsString("checkIn")));

        // then
        verifyNoInteractions(submitHotelSearch);
    }

    /**
     * Verifica que un hotelId con espacios en blanco o demasiado largo se
     * rechace con 400 antes de invocar el caso de uso.
     *
     * Condiciones:
     * - El hotelId tiene un espacio en medio, al inicio, un tab, un espacio
     *   Unicode, está vacío o tiene 65 caracteres
     *
     * Se espera:
     * - Status 400 con el error asociado al campo hotelId
     */
    @ParameterizedTest
    @MethodSource("invalidHotelIds")
    void submit_InvalidHotelId_ReturnsBadRequest(String jsonHotelId, String expectedError) throws Exception {
        // given
        String body = """
            {
              "hotelId": "%s",
              "checkIn": "09/11/2026",
              "checkOut": "29/11/2026",
              "ages": [29]
            }
            """.formatted(jsonHotelId);

        // when
        mockMvc.perform(post("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errors.hotelId[0]").value(expectedError));

        // then
        verifyNoInteractions(submitHotelSearch);
    }

    /**
     * Verifica que una edad negativa se rechace con 400.
     *
     * Condiciones:
     * - La lista ages contiene un valor menor que cero
     *
     * Se espera:
     * - Status 400 con el error asociado al elemento de ages
     */
    @Test
    void submit_NegativeAge_ReturnsBadRequest() throws Exception {
        // given
        String body = """
            {
              "hotelId": "550e8400-e29b-41d4-a716-446655440000",
              "checkIn": "09/11/2026",
              "checkOut": "29/11/2026",
              "ages": [29, -1]
            }
            """;

        // when
        mockMvc.perform(post("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errors['ages[1]'][0]")
                .value("cada edad debe ser mayor o igual que 0"));

        // then
        verifyNoInteractions(submitHotelSearch);
    }

    /**
     * Verifica que un body superior a 16 KB se rechace con 400.
     *
     * Condiciones:
     * - El body contiene más de 16384 bytes
     *
     * Se espera:
     * - Status 400 con el límite indicado en el detalle
     */
    @Test
    void submit_BodyLargerThan16Kb_ReturnsBadRequest() throws Exception {
        // given
        String body = "{\"hotelId\":\"" + "x".repeat(17_000) + "\"}";

        // when
        mockMvc.perform(post("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail", containsString("16384 bytes")));

        // then
        verifyNoInteractions(submitHotelSearch);
    }

    /**
     * Verifica que la indisponibilidad de Kafka se traduzca a 503.
     *
     * Condiciones:
     * - El caso de uso informa que Kafka no está disponible
     *
     * Se espera:
     * - Status 503 con Problem Details
     */
    @Test
    void submit_KafkaUnavailable_ReturnsServiceUnavailable() throws Exception {
        // given
        when(submitHotelSearch.execute(any()))
            .thenThrow(new HotelSearchUnavailableException("Kafka no está disponible"));

        // when
        mockMvc.perform(post("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(503));

        // then
        verify(submitHotelSearch).execute(any());
    }

    /**
     * Verifica que GET /count devuelva la búsqueda y el conteo de
     * equivalentes.
     *
     * Condiciones:
     * - El caso de uso devuelve una búsqueda con un equivalente
     *
     * Se espera:
     * - Status 200 con todos los campos de la respuesta
     */
    @Test
    void count_ExistingSearch_ReturnsSearchAndCount() throws Exception {
        // given
        SearchId searchId = SearchId.of(SEARCH_ID);
        when(countHotelSearch.execute(any())).thenReturn(
            new CountHotelSearch.Result(validSearch(searchId), 1));

        // when
        mockMvc.perform(get("/count").param("searchId", SEARCH_ID))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.searchId").value(SEARCH_ID))
            .andExpect(jsonPath("$.hotelId").value("550e8400-e29b-41d4-a716-446655440000"))
            .andExpect(jsonPath("$.checkIn").value("09/11/2026"))
            .andExpect(jsonPath("$.checkOut").value("29/11/2026"))
            .andExpect(jsonPath("$.ages[0]").value(29))
            .andExpect(jsonPath("$.ages[1]").value(28))
            .andExpect(jsonPath("$.searchCount").value(1));

        // then
        verify(countHotelSearch).execute(any());
    }

    /**
     * Verifica que un searchId válido pero inexistente se traduzca a 404.
     *
     * Condiciones:
     * - El caso de uso informa que la búsqueda no existe
     *
     * Se espera:
     * - Status 404 con Problem Details
     */
    @Test
    void count_SearchNotFound_ReturnsNotFound() throws Exception {
        // given
        SearchId searchId = SearchId.of(SEARCH_ID);
        when(countHotelSearch.execute(any())).thenThrow(new SearchNotFoundException(searchId));

        // when
        mockMvc.perform(get("/count").param("searchId", SEARCH_ID))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404));

        // then
        verify(countHotelSearch).execute(any());
    }

    /**
     * Verifica que un searchId mal formado se traduzca a 400.
     *
     * Condiciones:
     * - El caso de uso rechaza el formato del identificador
     *
     * Se espera:
     * - Status 400 con Problem Details
     */
    @Test
    void count_InvalidSearchId_ReturnsBadRequest() throws Exception {
        // given
        when(countHotelSearch.execute(any()))
            .thenThrow(new IllegalArgumentException("searchId debe ser un UUID válido"));

        // when
        mockMvc.perform(get("/count").param("searchId", "no-es-un-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400));

        // then
        verify(countHotelSearch).execute(any());
    }

    /**
     * Verifica que la ausencia del parámetro searchId se traduzca a 400.
     *
     * Condiciones:
     * - La request GET no incluye query parameters
     *
     * Se espera:
     * - Status 400 indicando el parámetro obligatorio
     */
    @Test
    void count_MissingSearchId_ReturnsBadRequest() throws Exception {
        // when
        mockMvc.perform(get("/count"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail", containsString("searchId")));

        // then
        verifyNoInteractions(countHotelSearch);
    }

    // <editor-fold desc="Helpers">
    private static Stream<Arguments> invalidHotelIds() {
        String whitespaceError = "hotelId no permite espacios en blanco";
        return Stream.of(
            Arguments.of("1234 aBc", whitespaceError),
            Arguments.of(" 1234aBc", whitespaceError),
            Arguments.of("1234\\taBc", whitespaceError),
            Arguments.of("1234\u00A0aBc", whitespaceError),
            Arguments.of("1234\u2003aBc", whitespaceError),
            Arguments.of("", "hotelId es obligatorio"),
            Arguments.of("A".repeat(65), "hotelId no puede superar 64 caracteres"));
    }
    private HotelSearch validSearch(SearchId searchId) {
        return new HotelSearch(
            searchId,
            HotelId.of("550e8400-e29b-41d4-a716-446655440000"),
            CheckIn.of(LocalDate.of(2026, 11, 9)),
            CheckOut.of(LocalDate.of(2026, 11, 29)),
            Ages.of(List.of(29, 28, 4, 3)));
    }
    // </editor-fold>
}
