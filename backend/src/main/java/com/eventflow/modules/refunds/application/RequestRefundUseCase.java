package com.eventflow.modules.refunds.application;

import com.eventflow.modules.ordering.application.OrderingFacade;
import com.eventflow.modules.refunds.domain.RefundRequest;
import com.eventflow.modules.refunds.domain.event.RefundEvents;
import com.eventflow.modules.refunds.domain.exception.RefundAlreadyRequestedException;
import com.eventflow.modules.refunds.domain.port.ListingsReadPort;
import com.eventflow.modules.refunds.domain.port.RefundRequestRepository;
import com.eventflow.modules.ticketing.application.TicketingFacade;
import com.eventflow.shared.error.SemanticValidationException;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * POST /tickets/{id}/refund-requests (S3): valida ADR-19 + ventana + sin listing activo (doble
 * candado, design/00 §7), marca el boleto REFUND_PENDING y bloquea el QR, y abre el expediente
 * congelando amount = acquisition_price y el payment_id de adquisición (C2). Todo en una TX.
 */
@Service
public class RequestRefundUseCase {

    private final TicketingFacade ticketing;
    private final OrderingFacade ordering;
    private final RefundRequestRepository refundRepository;
    private final ListingsReadPort listings;
    private final OutboxPublisher outbox;

    public RequestRefundUseCase(TicketingFacade ticketing, OrderingFacade ordering,
                                RefundRequestRepository refundRepository, ListingsReadPort listings,
                                OutboxPublisher outbox) {
        this.ticketing = ticketing;
        this.ordering = ordering;
        this.refundRepository = refundRepository;
        this.listings = listings;
        this.outbox = outbox;
    }

    @Transactional
    public RefundRequest execute(UUID requesterId, UUID ticketId, String reason) {
        if (refundRepository.existsActiveForTicket(ticketId)) {
            throw new RefundAlreadyRequestedException();
        }
        // doble candado refund↔listing (MEJORA autorizada; hoy siempre false, M6 lo implementa)
        if (listings.hasActiveListing(ticketId)) {
            throw new RefundAlreadyRequestedException();
        }
        // marca REFUND_PENDING + bloquea QR + valida ADR-19/ventana (lanza si no procede)
        TicketingFacade.RefundSubject subject = ticketing.beginRefund(ticketId, requesterId);
        // localiza el pago de adquisición del propietario actual (C2)
        UUID paymentId = ordering.acquisitionPayment(subject.acquisitionOrderItemId())
                .map(p -> p.id())
                .orElseThrow(() -> new SemanticValidationException("ticket",
                        "No se encontró el pago de adquisición del boleto"));
        RefundRequest refund = refundRepository.save(RefundRequest.open(
                ticketId, requesterId, paymentId, subject.acquisitionPrice(), reason));
        outbox.publish("Refund", refund.getId(), RefundEvents.REFUND_REQUESTED, RefundEvents.VERSION,
                requesterId, Map.of(
                        "refundId", refund.getId().toString(),
                        "ticketId", ticketId.toString(),
                        "requesterId", requesterId.toString(),
                        "amount", refund.getAmount().amount().toPlainString()));
        return refund;
    }
}
