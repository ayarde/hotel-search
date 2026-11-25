package com.hotelsearch.infrastructure.input.rest.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.hotelsearch.domain.valueobject.HotelId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests del contrato declarado para hotelId en el DTO HTTP.
 */
class HotelSearchRequestTest {

    /**
     * Verifica que la regex del DTO acepta exactamente los mismos caracteres
     * que el value object del dominio, para que ninguna capa deje pasar lo
     * que la otra rechaza.
     *
     * Condiciones:
     * - Se recorre cada carácter del plano multilingüe básico
     *
     * Se espera:
     * - Ningún carácter tenga distinto resultado en la regex y en HotelId
     */
    @Test
    void hotelIdPattern_EveryBmpCharacter_MatchesDomainRule() {
        // given
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(HotelSearchRequest.HOTEL_ID_PATTERN);
        List<String> discrepancies = new ArrayList<>();

        // when
        for (char character = 0; character < Character.MAX_VALUE; character++) {
            if (Character.isSurrogate(character)) {
                continue;
            }
            String value = "a" + character + "b";
            boolean acceptedByPattern = pattern.matcher(value).matches();
            if (acceptedByPattern != acceptedByDomain(value)) {
                discrepancies.add(String.format("U+%04X", (int) character));
            }
        }

        // then
        assertThat(discrepancies).as("La regex del DTO y HotelId deben coincidir").isEmpty();
    }

    /**
     * Verifica que OpenAPI publica las mismas reglas que se validan.
     *
     * Condiciones:
     * - Se leen las anotaciones del campo hotelId, donde Java propaga las
     *   anotaciones del componente del record
     *
     * Se espera:
     * - El pattern de OpenAPI sea el mismo que valida Bean Validation
     * - Los máximos de @Size y de OpenAPI sean HotelId.MAX_LENGTH
     */
    @Test
    void hotelIdAnnotations_OpenApiAndValidation_ShareTheSameRules() throws NoSuchFieldException {
        // given
        Field hotelId = HotelSearchRequest.class.getDeclaredField("hotelId");

        // when
        Schema schema = hotelId.getAnnotation(Schema.class);
        Pattern validation = hotelId.getAnnotation(Pattern.class);
        Size size = hotelId.getAnnotation(Size.class);

        // then
        assertAll(
            () -> assertThat(schema.pattern()).as("Pattern OpenAPI").isEqualTo(validation.regexp()),
            () -> assertThat(schema.maxLength()).as("maxLength OpenAPI").isEqualTo(HotelId.MAX_LENGTH),
            () -> assertThat(size.max()).as("@Size max").isEqualTo(HotelId.MAX_LENGTH)
        );
    }

    // <editor-fold desc="Helpers">
    private static boolean acceptedByDomain(String value) {
        try {
            HotelId.of(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    // </editor-fold>
}
