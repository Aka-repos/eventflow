package com.eventflow.modules.ticketing.application;

import com.eventflow.modules.ticketing.domain.DynamicQr;
import com.eventflow.modules.ticketing.domain.Ticket;
import com.eventflow.modules.ticketing.domain.TicketHistoryEntry;
import com.eventflow.modules.ticketing.domain.TicketType;
import com.eventflow.modules.ticketing.domain.exception.TicketTypeNotFoundException;
import com.eventflow.modules.ticketing.domain.port.TicketHistoryRepository;
import com.eventflow.modules.ticketing.domain.port.TicketRepository;
import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.ticketing.domain.AcquiredVia;
import com.eventflow.modules.ticketing.domain.RecoveryPolicy;
import com.eventflow.modules.ticketing.domain.exception.TicketNotFoundException;
import com.eventflow.modules.ticketing.domain.port.DynamicQrRepository;
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
    private final DynamicQrRepository qrRepository;
    private final CatalogFacade catalog;
    private final QrIssuer qrIssuer;
    private final OutboxPublisher outbox;
    private final java.time.Clock clock;

    public TicketingFacade(TicketTypeRepository ticketTypeRepository, TicketRepository ticketRepository,
                           TicketHistoryRepository historyRepository, DynamicQrRepository qrRepository,
                           CatalogFacade catalog, QrIssuer qrIssuer, OutboxPublisher outbox,
                           java.time.Clock clock) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
        this.qrRepository = qrRepository;
        this.catalog = catalog;
        this.qrIssuer = qrIssuer;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Nombre de la tarifa (para CheckInResponse.ticketTypeName). */
    @Transactional(readOnly = true)
    public String ticketTypeName(java.util.UUID ticketTypeId) {
        return ticketTypeRepository.findById(ticketTypeId)
                .map(com.eventflow.modules.ticketing.domain.TicketType::getName).orElse(null);
    }

    /** Nombre de la zona de la tarifa, si tiene (para CheckInResponse.zoneName). */
    @Transactional(readOnly = true)
    public String zoneNameForTicketType(java.util.UUID ticketTypeId, java.util.UUID eventId) {
        return ticketTypeRepository.findById(ticketTypeId)
                .map(com.eventflow.modules.ticketing.domain.TicketType::getZoneId)
                .flatMap(zoneId -> zoneId == null ? java.util.Optional.empty()
                        : catalog.zoneNameForEvent(zoneId, eventId))
                .orElse(null);
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

    /**
     * Resolución del QR para check-in (checkin→ticketing S): bajo lock del QR, valida estado del
     * QR y del boleto, propiedad de evento y consuma atómicamente. Devuelve el veredicto SIN
     * decidir HTTP — el módulo checkin traduce a Problem/registro. Un solo boleto entra una vez
     * (el índice único de event_checkins lo respalda; aquí el consumo del QR ACTIVE serializa).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CheckInResolution resolveAndConsume(java.util.UUID qrId, java.util.UUID eventId) {
        DynamicQr qr = qrRepository.findByIdForUpdate(qrId).orElse(null);
        if (qr == null) {
            return CheckInResolution.denied("qr_invalid", null, null);
        }
        Ticket ticket = ticketRepository.findById(qr.getTicketId()).orElse(null);
        if (ticket == null) {
            return CheckInResolution.denied("qr_invalid", null, qr.getId());
        }
        if (!ticket.getEventId().equals(eventId)) {
            return CheckInResolution.denied("checkin_wrong_event", ticket, qr.getId());
        }
        // QR no ACTIVE: si ya se consumó ⇒ already_used; si no ⇒ ticket_blocked/qr_invalid
        if (qr.getStatus() == com.eventflow.modules.ticketing.domain.QrStatus.CONSUMED
                || ticket.getStatus() == com.eventflow.modules.ticketing.domain.TicketStatus.USED) {
            return CheckInResolution.denied("already_used", ticket, qr.getId());
        }
        if (!qr.isValidAt(clock.instant())) {
            return CheckInResolution.denied("qr_invalid", ticket, qr.getId());
        }
        if (!ticket.isCheckInEligible()) {
            return CheckInResolution.denied("ticket_blocked", ticket, qr.getId());
        }
        // veredicto GRANTED: consumir QR + marcar boleto USED en la misma TX del check-in
        qr.consume();
        qrRepository.save(qr);
        ticket.markUsed();
        ticketRepository.save(ticket);
        historyRepository.append(com.eventflow.modules.ticketing.domain.TicketHistoryEntry.of(
                ticket.getId(), com.eventflow.modules.ticketing.domain.TicketStatus.ACTIVE.name(),
                com.eventflow.modules.ticketing.domain.TicketStatus.USED.name(), "CHECKIN",
                ticket.getCurrentOwnerId()));
        return CheckInResolution.granted(ticket, qr.getId());
    }

    /** Evento del boleto — para autorizar al organizador ANTES de mutar (refunds→ticketing S). */
    @Transactional(readOnly = true)
    public java.util.UUID eventIdOfTicket(java.util.UUID ticketId) {
        return ticketRepository.findById(ticketId).map(Ticket::getEventId)
                .orElseThrow(TicketNotFoundException::new);
    }

    /**
     * Datos del boleto para evaluar recuperación (refunds→ticketing S). Incluye acquisition_price
     * (base del reembolso, C2/ADR-19) y el snapshot congelado.
     */
    @Transactional(readOnly = true)
    public TicketRecoveryView recoveryView(java.util.UUID ticketId, java.util.UUID ownerId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .filter(t -> t.isOwnedBy(ownerId))
                .orElseThrow(TicketNotFoundException::new);
        RecoveryPolicy.Recovery recovery = RecoveryPolicy.evaluate(
                new RecoveryPolicy.RecoverySubject(ticket.getAcquiredVia(), ticket.getStatus(),
                        ticket.getOriginalPrice(), ticket.getAcquisitionPrice(), ticket.getPolicySnapshot()),
                clock.instant());
        return new TicketRecoveryView(ticket.getId(), ticket.getEventId(), ticket.getAcquisitionOrderItemId(),
                ticket.getAcquiredVia().name(), ticket.getAcquisitionPrice(), recovery);
    }

    /**
     * Solicitud de reembolso (refunds→ticketing S): valida ADR-19 + ventana, marca el boleto
     * REFUND_PENDING y BLOQUEA el QR. Devuelve el snapshot necesario para crear el expediente.
     * Todo en la MISMA TX del caso de uso.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RefundSubject beginRefund(java.util.UUID ticketId, java.util.UUID ownerId) {
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .filter(t -> t.isOwnedBy(ownerId))
                .orElseThrow(TicketNotFoundException::new);
        // ADR-19: EXCHANGE jamás reembolsa
        if (ticket.getAcquiredVia() == AcquiredVia.EXCHANGE) {
            throw new com.eventflow.modules.ticketing.domain.exception.RefundNotAllowedExchangeException();
        }
        // ventana de reembolso activa (regla de dominio evaluada aquí, server-side)
        RecoveryPolicy.Recovery recovery = RecoveryPolicy.evaluate(
                new RecoveryPolicy.RecoverySubject(ticket.getAcquiredVia(), ticket.getStatus(),
                        ticket.getOriginalPrice(), ticket.getAcquisitionPrice(), ticket.getPolicySnapshot()),
                clock.instant());
        if (recovery.option() != RecoveryPolicy.Option.REFUND) {
            throw new com.eventflow.modules.ticketing.domain.exception.RefundWindowClosedException();
        }
        ticket.requestRefund();
        ticketRepository.save(ticket);
        qrIssuer.blockLive(ticket);
        historyRepository.append(com.eventflow.modules.ticketing.domain.TicketHistoryEntry.of(
                ticket.getId(), com.eventflow.modules.ticketing.domain.TicketStatus.ACTIVE.name(),
                com.eventflow.modules.ticketing.domain.TicketStatus.REFUND_PENDING.name(),
                "REFUND_REQUEST", ownerId));
        return new RefundSubject(ticket.getId(), ticket.getEventId(), ticket.getTicketTypeId(),
                ticket.getCurrentOwnerId(), ticket.getAcquisitionOrderItemId(), ticket.getAcquisitionPrice());
    }

    /**
     * Aprobación (refunds→ticketing S): REFUND_PENDING → REFUNDED, invalida el QR y DEVUELVE el
     * cupo al inventario (sold -= 1, bajo lock de la tarifa). Emite el boleto de vuelta al pool.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RefundSubject approveRefund(java.util.UUID ticketId, java.util.UUID resolverId) {
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId).orElseThrow(TicketNotFoundException::new);
        ticket.approveRefund();
        ticketRepository.save(ticket);
        qrIssuer.invalidateLive(ticket, "REFUND", resolverId);
        release(ticket.getTicketTypeId(), 1); // el cupo vuelve al inventario oficial
        historyRepository.append(com.eventflow.modules.ticketing.domain.TicketHistoryEntry.of(
                ticket.getId(), com.eventflow.modules.ticketing.domain.TicketStatus.REFUND_PENDING.name(),
                com.eventflow.modules.ticketing.domain.TicketStatus.REFUNDED.name(),
                "REFUND_APPROVED", resolverId));
        return new RefundSubject(ticket.getId(), ticket.getEventId(), ticket.getTicketTypeId(),
                ticket.getCurrentOwnerId(), ticket.getAcquisitionOrderItemId(), ticket.getAcquisitionPrice());
    }

    /** Rechazo (refunds→ticketing S): REFUND_PENDING → ACTIVE, desbloquea el QR. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void rejectRefund(java.util.UUID ticketId, java.util.UUID resolverId) {
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId).orElseThrow(TicketNotFoundException::new);
        ticket.rejectRefund();
        ticketRepository.save(ticket);
        qrIssuer.unblockLive(ticket);
        historyRepository.append(com.eventflow.modules.ticketing.domain.TicketHistoryEntry.of(
                ticket.getId(), com.eventflow.modules.ticketing.domain.TicketStatus.REFUND_PENDING.name(),
                com.eventflow.modules.ticketing.domain.TicketStatus.ACTIVE.name(),
                "REFUND_REJECTED", resolverId));
    }

    public record TicketRecoveryView(java.util.UUID ticketId, java.util.UUID eventId,
                                     java.util.UUID acquisitionOrderItemId, String acquiredVia,
                                     Money acquisitionPrice, RecoveryPolicy.Recovery recovery) {
    }

    public record RefundSubject(java.util.UUID ticketId, java.util.UUID eventId, java.util.UUID ticketTypeId,
                                java.util.UUID ownerId, java.util.UUID acquisitionOrderItemId,
                                Money acquisitionPrice) {
    }

    public record CheckInResolution(boolean granted, String denialCode, java.util.UUID ticketId,
                                    java.util.UUID eventId, java.util.UUID ownerId, java.util.UUID qrId,
                                    java.util.UUID ticketTypeId) {
        static CheckInResolution granted(Ticket t, java.util.UUID qrId) {
            return new CheckInResolution(true, null, t.getId(), t.getEventId(), t.getCurrentOwnerId(),
                    qrId, t.getTicketTypeId());
        }
        static CheckInResolution denied(String code, Ticket t, java.util.UUID qrId) {
            return new CheckInResolution(false, code, t == null ? null : t.getId(),
                    t == null ? null : t.getEventId(), t == null ? null : t.getCurrentOwnerId(),
                    qrId, t == null ? null : t.getTicketTypeId());
        }
    }

    public record TariffSnapshot(UUID ticketTypeId, UUID eventId, String name, Money price) {
    }

    public record IssueTicketsCommand(UUID orderItemId, UUID ticketTypeId, UUID eventId, UUID ownerId,
                                      int quantity, Money unitPrice, Map<String, Object> policySnapshot,
                                      Instant purchasedAt) {
    }
}
