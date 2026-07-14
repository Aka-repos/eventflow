package com.eventflow.modules.ledger.application;

import com.eventflow.modules.ledger.domain.LedgerEntry;
import com.eventflow.modules.ledger.domain.port.LedgerAppender;
import com.eventflow.shared.domain.Money;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** ÚNICA superficie del ledger (doc 10: ordering/refunds/exchange → S). Solo escritura append-only. */
@Component
public class LedgerFacade {

    private final LedgerAppender writer;

    public LedgerFacade(LedgerAppender writer) {
        this.writer = writer;
    }

    /** Registra la venta primaria en la MISMA TX del caso de uso que la invoca. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void recordPrimarySale(UUID buyerId, UUID organizerId, Money amount, UUID orderId, UUID eventId,
                                  Map<String, Object> details) {
        writer.append(LedgerEntry.sale(buyerId, organizerId, amount, orderId, eventId, details));
    }

    /**
     * Reembolso (D1): asiento ORGANIZER → BUYER que revierte exactamente la venta primaria
     * (SALE BUYER → ORGANIZER), comisión 0. Referencia el refund, no la orden.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void recordRefund(UUID organizerId, UUID buyerId, Money amount, UUID refundId, UUID eventId,
                             Map<String, Object> details) {
        writer.append(LedgerEntry.refund(organizerId, buyerId, amount, refundId, eventId, details));
    }
}
