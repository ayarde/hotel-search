package com.hotelsearch.infrastructure.output.persistence;

import com.hotelsearch.domain.model.HotelSearch;
import com.hotelsearch.domain.model.HotelSearchWithCount;
import com.hotelsearch.domain.port.HotelSearchRepository;
import com.hotelsearch.domain.valueobject.Ages;
import com.hotelsearch.domain.valueobject.CheckIn;
import com.hotelsearch.domain.valueobject.CheckOut;
import com.hotelsearch.domain.valueobject.HotelId;
import com.hotelsearch.domain.valueobject.SearchId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Implementación JDBC del puerto de persistencia sobre Oracle.
 *
 * <p>La inserción no hace SELECT previo: la PK search_id actúa como
 * defensa frente al redelivery at-least-once. La consulta de búsqueda y
 * conteo se resuelve con la sentencia única definida en
 * TECHNICAL-DECISIONS.md (§7 Oracle) y usa bind parameters.</p>
 */
public class JdbcHotelSearchRepository implements HotelSearchRepository {

    private static final String INSERT_SQL = """
            INSERT INTO hotel_search (search_id, hotel_id, check_in, check_out, ages)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String FIND_WITH_COUNT_SQL = """
            SELECT
                s.search_id,
                s.hotel_id,
                s.check_in,
                s.check_out,
                s.ages,
                s.created_at,
                (
                    SELECT COUNT(*)
                    FROM hotel_search other
                    WHERE other.hotel_id = s.hotel_id
                      AND other.check_in = s.check_in
                      AND other.check_out = s.check_out
                      AND other.ages = s.ages
                      AND other.search_id <> s.search_id
                ) AS search_count
            FROM hotel_search s
            WHERE s.search_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcHotelSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(HotelSearch search) {
        try {
            jdbcTemplate.update(INSERT_SQL,
                search.searchId().value().toString(),
                search.hotelId().value().toString(),
                search.checkIn().value(),
                search.checkOut().value(),
                serializeAges(search.ages()));
        } catch (DuplicateKeyException e) {
            // La PK search_id ya existe: duplicado por redelivery, se
            // descarta. Cualquier otro error SQL no entra aquí y se propaga.
        }
    }

    @Override
    public Optional<HotelSearchWithCount> findByIdWithCount(SearchId searchId) {
        List<HotelSearchWithCount> rows = jdbcTemplate.query(FIND_WITH_COUNT_SQL,
            (rs, rowNum) -> new HotelSearchWithCount(
                new HotelSearch(
                    SearchId.of(rs.getString("search_id")),
                    HotelId.of(rs.getString("hotel_id")),
                    new CheckIn(rs.getObject("check_in", java.time.LocalDate.class)),
                    new CheckOut(rs.getObject("check_out", java.time.LocalDate.class)),
                    parseAges(rs.getString("ages"))),
                rs.getInt("search_count")),
            searchId.value().toString());

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    /**
     * Serializa las edades como JSON compacto, por ejemplo [29,28,4,3],
     * preservando el orden de la lista. Ese texto es el que hace posible
     * la igualdad de ages en el predicado de la consulta de conteo.
     */
    private static String serializeAges(Ages ages) {
        StringJoiner json = new StringJoiner(",", "[", "]");
        for (Integer age : ages.values()) {
            json.add(age.toString());
        }
        return json.toString();
    }

    /**
     * Parsea el JSON compacto de edades almacenado en Oracle y reconstruye
     * el value object con el orden preservado.
     */
    private static Ages parseAges(String raw) {
        List<Integer> values = Arrays.stream(raw.substring(1, raw.length() - 1).split(",", -1))
            .map(String::trim)
            .map(Integer::valueOf)
            .toList();
        return Ages.of(values);
    }
}