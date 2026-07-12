package com.eventflow.modules.ticketing.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.ticketing.application.result.TicketView;
import com.eventflow.modules.ticketing.domain.Ticket;
import com.eventflow.modules.ticketing.domain.TicketType;
import com.eventflow.modules.ticketing.domain.port.TicketHistoryRepository;
import com.eventflow.modules.ticketing.domain.port.TicketTypeRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Compone TicketResponse/TicketDetail: evento (catalog S²), tarifa, zona e historia. */
@Component
public class TicketViewAssembler {

    private final TicketTypeRepository ticketTypeRepository;
    private final TicketHistoryRepository historyRepository;
    private final CatalogFacade catalog;

    public TicketViewAssembler(TicketTypeRepository ticketTypeRepository,
                               TicketHistoryRepository historyRepository, CatalogFacade catalog) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.historyRepository = historyRepository;
        this.catalog = catalog;
    }

    public TicketView assemble(Ticket ticket, boolean includeHistory) {
        Optional<TicketType> tariff = ticketTypeRepository.findById(ticket.getTicketTypeId());
        String zoneName = tariff.map(TicketType::getZoneId)
                .flatMap(zoneId -> zoneId == null ? Optional.empty()
                        : catalog.zoneNameForEvent(zoneId, ticket.getEventId()))
                .orElse(null);
        return new TicketView(ticket,
                catalog.eventCard(ticket.getEventId()).orElse(null),
                tariff.map(TicketType::getName).orElse(""),
                zoneName,
                includeHistory ? historyRepository.findByTicketId(ticket.getId()) : List.of());
    }
}
