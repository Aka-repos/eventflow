package com.eventflow.modules.refunds.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.refunds.domain.RefundRequest;
import com.eventflow.modules.refunds.domain.event.RefundEvents;
import com.eventflow.modules.refunds.domain.exception.RefundNotFoundException;
import com.eventflow.modules.refunds.domain.port.RefundRequestRepository;
import com.eventflow.modules.ticketing.application.TicketingFacade;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * POST /refund-requests/{id}/reject (S3): Refund→REJECTED, Ticket→ACTIVE, QR desbloqueado.
 * No hay movimiento de dinero ni de inventario.
 */
@Service
public class RejectRefundUseCase {

    private final RefundRequestRepository refundRepository;
    private final TicketingFacade ticketing;
    private final CatalogFacade catalog;
    private final OutboxPublisher outbox;
    private final Clock clock;

    public RejectRefundUseCase(RefundRequestRepository refundRepository, TicketingFacade ticketing,
                               CatalogFacade catalog, OutboxPublisher outbox, Clock clock) {
        this.refundRepository = refundRepository;
        this.ticketing = ticketing;
        this.catalog = catalog;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public RefundRequest execute(UUID organizerId, UUID refundId, String reason) {
        RefundRequest refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(RefundNotFoundException::new);
        // autorización ANTES de mutar (404 anti-enumeración)
        UUID eventId = ticketing.eventIdOfTicket(refund.getTicketId());
        if (!catalog.isEventOrganizer(eventId, organizerId)) {
            throw new RefundNotFoundException();
        }
        refund.reject(organizerId, reason, clock.instant());
        ticketing.rejectRefund(refund.getTicketId(), organizerId);
        RefundRequest saved = refundRepository.save(refund);
        outbox.publish("Refund", refund.getId(), RefundEvents.REFUND_REJECTED, RefundEvents.VERSION,
                organizerId, Map.of("refundId", refund.getId().toString(),
                        "ticketId", refund.getTicketId().toString(), "resolvedBy", organizerId.toString()));
        return saved;
    }
}
