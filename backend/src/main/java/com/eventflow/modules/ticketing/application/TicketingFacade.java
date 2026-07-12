package com.eventflow.modules.ticketing.application;

import com.eventflow.modules.ticketing.domain.Ticket;
import com.eventflow.modules.ticketing.domain.TicketHistoryEntry;
import com.eventflow.modules.ticketing.domain.TicketType;
import com.eventflow.modules.ticketing.domain.exception.TicketTypeNotFoundException;
import com.eventflow.modules.ticketing.domain.port.TicketHistoryRepository;
import com.eventflow.modules.ticketing.domain.port.TicketRepository;
import com.eventflow.modules.ticketing.domain.port.TicketTypeRepository;
import com.eventflow.shared.domain.Money;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ÚNICA superficie de ticketing para otros módulos (doc 10: ordering→ticketing S).
 * Inventario bajo FOR UPDATE (S2); emisión de boletos con snapshot ADR-03 en la MISMA TX del pago.
 */
@Component
public class TicketingFacade {

    private final TicketTypeRepository ticketTypeRepository;
    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository historyRepository;
    private final OutboxPublisher outbox;

    public TicketingFacade(TicketTypeRepository ticketTypeRepository, TicketRepository ticketRepository,
                           TicketHistoryRepository historyRepository, OutboxPublisher outbox) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
        this.outbox = outbox;
    }

    /** Reserva cupo (sold += qty) bajo lock de fila; valida ventana de venta y stock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public TariffSnapshot reserve(UUID ticketTypeId, int quantity, Instant now) {
        TicketType tariff = ticketTypeRepository.findByIdForUpdate(ticketTypeId)
                .orElseThrow(TicketTypeNotFoundException::new);
        tariff.reserve(quantity, now);
        ticketTypeRepository.save(tariff);
        return new TariffSnapshot(tariff.getId(), tariff.getEventId(), tariff.getName(), tariff.getPrice());
    }

    /** Devuelve cupo (cancelación/expiración/pago rechazado). Idempotencia la da el llamador. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(UUID ticketTypeId, int quantity) {
        TicketType tariff = ticketTypeRepository.findByIdForUpdate(ticketTypeId)
                .orElseThrow(TicketTypeNotFoundException::new);
        tariff.release(quantity);
        ticketTypeRepository.save(tariff);
    }

    /** Emite boletos primarios: Ticket ACTIVE + historia ISSUED + TicketPurchased al outbox (api/08). */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> issuePrimaryTickets(IssueTicketsCommand cmd) {
        List<UUID> ticketIds = new ArrayList<>(cmd.quantity());
        for (int i = 0; i < cmd.quantity(); i++) {
            Ticket ticket = ticketRepository.save(Ticket.issuePrimary(cmd.ticketTypeId(), cmd.eventId(),
                    cmd.ownerId(), cmd.orderItemId(), cmd.unitPrice(), cmd.policySnapshot(), cmd.purchasedAt()));
            historyRepository.append(TicketHistoryEntry.issued(ticket.getId(), cmd.ownerId()));
            outbox.publish("Ticket", ticket.getId(), "TicketPurchased", 1, cmd.ownerId(), Map.of(
                    "ticketId", ticket.getId().toString(),
                    "ticketTypeId", cmd.ticketTypeId().toString(),
                    "eventId", cmd.eventId().toString(),
                    "ownerId", cmd.ownerId().toString(),
                    "acquisitionPrice", cmd.unitPrice().amount().toPlainString()));
            ticketIds.add(ticket.getId());
        }
        return List.copyOf(ticketIds);
    }

    /** Snapshot de lectura de la tarifa (sin lock) — para descripciones y agrupación por evento. */
    @Transactional(readOnly = true)
    public TariffSnapshot tariffSnapshot(UUID ticketTypeId) {
        TicketType tariff = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(TicketTypeNotFoundException::new);
        return new TariffSnapshot(tariff.getId(), tariff.getEventId(), tariff.getName(), tariff.getPrice());
    }

    /** Versión batch (H3): una sola consulta para componer páginas de OrderResponse. */
    @Transactional(readOnly = true)
    public Map<UUID, TariffSnapshot> tariffSnapshots(Collection<UUID> ticketTypeIds) {
        Map<UUID, TariffSnapshot> byId = new java.util.HashMap<>();
        for (TicketType tariff : ticketTypeRepository.findAllByIds(ticketTypeIds)) {
            byId.put(tariff.getId(), new TariffSnapshot(tariff.getId(), tariff.getEventId(),
                    tariff.getName(), tariff.getPrice()));
        }
        return byId;
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<UUID>> ticketIdsBySourceOrderItem(Collection<UUID> orderItemIds) {
        return ticketRepository.ticketIdsBySourceOrderItem(orderItemIds);
    }

    public record TariffSnapshot(UUID ticketTypeId, UUID eventId, String name, Money price) {
    }

    public record IssueTicketsCommand(UUID orderItemId, UUID ticketTypeId, UUID eventId, UUID ownerId,
                                      int quantity, Money unitPrice, Map<String, Object> policySnapshot,
                                      Instant purchasedAt) {
    }
}
