package com.hotelsearch.infrastructure.input.kafka;

import com.hotelsearch.application.PersistHotelSearch;
import com.hotelsearch.domain.model.HotelSearchEvent;
import com.hotelsearch.domain.valueobject.SearchId;
import com.hotelsearch.infrastructure.observability.CorrelationContext;
import com.hotelsearch.infrastructure.observability.MdcScope;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Listener Kafka de eventos de búsqueda de hotel.
 */
public class HotelSearchKafkaListener {

    private final HotelSearchEventMapper mapper;
    private final PersistHotelSearch persistHotelSearch;

    /**
     * Crea el listener con el mapper de entrada y el servicio transaccional
     * de persistencia.
     *
     * @param mapper mapper que valida el evento recibido
     * @param persistHotelSearch servicio de aplicación con frontera Oracle
     */
    public HotelSearchKafkaListener(
        HotelSearchEventMapper mapper,
        PersistHotelSearch persistHotelSearch
    ) {
        this.mapper = mapper;
        this.persistHotelSearch = persistHotelSearch;
    }

    /**
     * Consume un evento, lo valida y lo persiste en Oracle.
     *
     * <p>El servicio de persistencia completa su transacción antes de que
     * este método retorne. Spring Kafka confirma el offset únicamente
     * después de ese retorno. El scope de correlación se cierra incluso
     * cuando la validación o la persistencia propagan una excepción.</p>
     *
     * @param consumerRecord registro Kafka con el evento y sus headers de correlación
     */
    @KafkaListener(
        topics = "${app.kafka.topics.hotel-availability-searches}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, HotelSearchEvent> consumerRecord) {
        HotelSearchEvent event = consumerRecord.value();
        String searchId = resolveSearchId(consumerRecord, event);

        try (MdcScope _ = MdcScope.search(
            CorrelationContext.KAFKA_CONSUMER_COMPONENT, searchId)) {
            persistHotelSearch.execute(mapper.toHotelSearch(event));
        }
    }

    private String resolveSearchId(
        ConsumerRecord<String, HotelSearchEvent> consumerRecord,
        HotelSearchEvent event
    ) {
        String eventSearchId = event.searchId().toString();
        var header = consumerRecord.headers().lastHeader(CorrelationContext.SEARCH_ID);
        if (header == null || header.value() == null) {
            return eventSearchId;
        }

        String candidate = new String(header.value(), StandardCharsets.UTF_8);
        try {
            String validSearchId = SearchId.of(candidate).toString();
            return validSearchId.equals(eventSearchId) ? validSearchId : eventSearchId;
        } catch (IllegalArgumentException _) {
            // El evento conserva su searchId canónico. Un header inválido no
            // se utiliza para la correlación ni se propaga al MDC.
            return eventSearchId;
        }
    }
}
