package com.hotelsearch.infrastructure.input.kafka;

import com.hotelsearch.application.PersistHotelSearch;
import com.hotelsearch.domain.model.HotelSearchEvent;
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
     * después de ese retorno.</p>
     *
     * @param evento evento deserializado desde Kafka
     */
    @KafkaListener(
        topics = "${app.kafka.topics.hotel-availability-searches}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(HotelSearchEvent evento) {
        persistHotelSearch.execute(mapper.toHotelSearch(evento));
    }
}
