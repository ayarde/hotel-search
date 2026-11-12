package com.hotelsearch.infrastructure.input.rest;

import com.hotelsearch.application.CountHotelSearch;
import com.hotelsearch.application.SubmitHotelSearch;
import com.hotelsearch.infrastructure.input.rest.dto.HotelSearchCountResponse;
import com.hotelsearch.infrastructure.input.rest.dto.HotelSearchRequest;
import com.hotelsearch.infrastructure.input.rest.dto.SubmitHotelSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API HTTP para registrar y consultar búsquedas de hotel.
 */
@RestController
@Tag(name = "Búsquedas de hoteles", description = "Registro y consulta de búsquedas de hotel")
public class HotelSearchController {

    private final SubmitHotelSearch submitHotelSearch;
    private final CountHotelSearch countHotelSearch;
    private final HotelSearchRestMapper mapper;

    /**
     * Crea el controller con los casos de uso y el mapper HTTP.
     *
     * @param submitHotelSearch caso de uso de registro
     * @param countHotelSearch caso de uso de consulta y conteo
     * @param mapper mapper entre HTTP y aplicación
     */
    public HotelSearchController(
        SubmitHotelSearch submitHotelSearch,
        CountHotelSearch countHotelSearch,
        HotelSearchRestMapper mapper
    ) {
        this.submitHotelSearch = submitHotelSearch;
        this.countHotelSearch = countHotelSearch;
        this.mapper = mapper;
    }

    /**
     * Registra una búsqueda y responde solo después de que Kafka confirme
     * el mensaje. La persistencia en Oracle ocurre de forma asíncrona.
     *
     * @param request búsqueda validada
     * @return identificador de la búsqueda aceptada
     */
    @PostMapping(
        path = "/search",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
        summary = "Registrar una búsqueda de hotel",
        description = "Publica la búsqueda en Kafka. La respuesta 202 confirma la aceptación por Kafka, "
            + "no la persistencia inmediata en Oracle; el GET puede responder 404 durante esa ventana."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Kafka aceptó el evento"),
        @ApiResponse(
            responseCode = "400",
            description = "La solicitud no cumple el contrato HTTP",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)
            )
        ),
        @ApiResponse(
            responseCode = "503",
            description = "Kafka no está disponible",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)
            )
        )
    })
    public ResponseEntity<SubmitHotelSearchResponse> submit(
        @Valid @RequestBody HotelSearchRequest request
    ) {
        var result = submitHotelSearch.execute(mapper.toCommand(request));
        return ResponseEntity.accepted().body(mapper.toResponse(result));
    }

    /**
     * Consulta una búsqueda persistida y cuenta las búsquedas equivalentes.
     *
     * @param searchId identificador de la búsqueda
     * @return búsqueda completa y cantidad de equivalentes
     */
    @GetMapping(path = "/count", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Consultar una búsqueda y sus equivalentes",
        description = "Devuelve la búsqueda indicada y la cantidad de otras búsquedas con el mismo hotel, "
            + "fechas y lista de edades en el mismo orden."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Búsqueda consultada"),
        @ApiResponse(
            responseCode = "400",
            description = "El searchId no tiene un formato UUID válido",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "El searchId todavía no existe en Oracle",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)
            )
        )
    })
    public HotelSearchCountResponse count(@RequestParam String searchId) {
        var result = countHotelSearch.execute(new CountHotelSearch.Query(searchId));
        return mapper.toResponse(result);
    }
}
