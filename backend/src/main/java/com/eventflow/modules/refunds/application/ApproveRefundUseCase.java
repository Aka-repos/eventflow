package com.eventflow.modules.refunds.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.ledger.application.LedgerFacade;
import com.eventflow.modules.payments.application.PaymentsFacade;
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
 * POST /refund-requests/{id}/approve (S3): el organizador dueño del evento aprueba. En una TX:
 * Refund→APPROVED, Ticket→REFUNDED, QR→INVALIDATED, cupo devuelto al inventario, pago→REFUNDED,
 * asiento ORGANIZER→BUYER (D1), y outbox RefundApproved + TicketRefunded + TicketReleased(REFUND).
 */
@Service
public class ApproveRefundUseCase {

    private final RefundRequestRepository refundRepository;
    private final TicketingFacade ticketing;
    private final PaymentsFacade payments;
    private final LedgerFacade ledger;
    private final CatalogFacade catalog;
    private final OutboxPublisher outbox;
    private final Clock clock;

    public ApproveRefundUseCase(RefundRequestRepository refundRepository, TicketingFacade ticketing,
                                PaymentsFacade payments, LedgerFacade ledger, CatalogFacade catalog,
                                OutboxPublisher outbox, Clock clock) {
        this.refundRepository = refundRepository;
        this.ticketing = ticketing;
        this.payments = payments;
        this.ledger = ledger;
        this.catalog = catalog;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public RefundRequest execute(UUID organizerId, UUID refundId) {
        RefundRequest refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(RefundNotFoundException::new);
        // autorización ANTES de mutar: el organizador debe ser dueño del evento (404 anti-enumeración)
        UUID eventId = ticketing.eventIdOfTicket(refund.getTicketId());
        if (!catalog.isEventOrganizer(eventId, organizerId)) {
            throw new RefundNotFoundException();
        }
        // el expediente manda: si ya no está pendiente, aborta ANTES de tocar el boleto (idempotencia)
        refund.approve(organizerId, clock.instant());
        TicketingFacade.RefundSubject subject = ticketing.approveRefund(refund.getTicketId(), organizerId);
        payments.refund(refund.getPaymentId());
        ledger.recordRefund(organizerId, subject.ownerId(), refund.getAmount(), refund.getId(),
                subject.eventId(), Map.of("ticketId", refund.getTicketId().toString()));
        RefundRequest saved = refundRepository.save(refund);

        // eventos: expediente resuelto + boleto reembolsado + liberación (la consume waitlist M7)
        outbox.publish("Refund", refund.getId(), RefundEvents.REFUND_APPROVED, RefundEvents.VERSION,
                organizerId, Map.of("refundId", refund.getId().toString(),
                        "ticketId", refund.getTicketId().toString(), "resolvedBy", organizerId.toString(),
                        "amount", refund.getAmount().amount().toPlainString()));
        outbox.publish("Ticket", refund.getTicketId(), RefundEvents.TICKET_REFUNDED, RefundEvents.VERSION,
                organizerId, Map.of("ticketId", refund.getTicketId().toString(),
                        "refundId", refund.getId().toString(),
                        "amount", refund.getAmount().amount().toPlainString()));
        outbox.publish("Ticket", refund.getTicketId(), RefundEvents.TICKET_RELEASED, RefundEvents.VERSION,
                organizerId, Map.of("ticketId", refund.getTicketId().toString(),
                        "eventId", subject.eventId().toString(),
                        "ticketTypeId", subject.ticketTypeId().toString(), "cause", "REFUND"));
        return saved;
    }
}
