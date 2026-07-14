package com.eventflow.modules.ticketing.domain;

import com.eventflow.shared.domain.Money;

import java.time.Instant;
import java.util.Map;

/**
 * Servicio de dominio: cancelación inteligente (ADR-19). Función pura del estado del boleto —
 * decide la ÚNICA opción de recuperación válida y su cálculo, sin efectos secundarios. Determinista:
 * el mismo boleto en el mismo instante siempre devuelve la misma opción (la UI solo muestra esa).
 */
public final class RecoveryPolicy {

    private RecoveryPolicy() {
    }

    public enum Option {
        REFUND, EXCHANGE, NONE
    }

    public enum Reason {
        REFUND_WINDOW_ACTIVE, REFUND_WINDOW_EXPIRED, ACQUIRED_VIA_EXCHANGE, EXCHANGE_DISABLED,
        TICKET_NOT_RECOVERABLE
    }

    /** Datos del boleto relevantes para la recuperación (el use case los toma del agregado). */
    public record RecoverySubject(AcquiredVia acquiredVia, TicketStatus status,
                                  Money originalPrice, Money acquisitionPrice,
                                  Map<String, Object> policySnapshot) {
    }

    public record Recovery(Option option, Reason reason, Money refundAmount, Instant refundDeadline,
                           Money exchangeOriginalPrice, int exchangeDepreciationPct,
                           Money exchangeListPrice, Instant exchangeListingDeadline) {

        static Recovery none(Reason reason) {
            return new Recovery(Option.NONE, reason, null, null, null, 0, null, null);
        }
    }

    public static Recovery evaluate(RecoverySubject s, Instant now) {
        if (s.status() != TicketStatus.ACTIVE) {
            return Recovery.none(Reason.TICKET_NOT_RECOVERABLE);
        }
        boolean exchangeEnabled = Boolean.TRUE.equals(s.policySnapshot().get("exchangeEnabled"));

        // ADR-19: un boleto adquirido en Exchange JAMÁS puede reembolsarse.
        if (s.acquiredVia() == AcquiredVia.EXCHANGE) {
            if (exchangeEnabled && withinListingDeadline(s, now)) {
                return exchange(s, Reason.ACQUIRED_VIA_EXCHANGE);
            }
            return Recovery.none(Reason.ACQUIRED_VIA_EXCHANGE);
        }

        // PRIMARY: reembolso si la ventana está activa; si expiró, exchange si está habilitado.
        if (refundWindowActive(s, now)) {
            Instant deadline = instant(s.policySnapshot().get("refundWindowEndsAt"));
            return new Recovery(Option.REFUND, Reason.REFUND_WINDOW_ACTIVE,
                    s.acquisitionPrice(), deadline, null, 0, null, null);
        }
        if (exchangeEnabled && withinListingDeadline(s, now)) {
            return exchange(s, Reason.REFUND_WINDOW_EXPIRED);
        }
        return Recovery.none(Reason.EXCHANGE_DISABLED);
    }

    private static Recovery exchange(RecoverySubject s, Reason reason) {
        int depreciation = intValue(s.policySnapshot().get("exchangeDepreciationPct"), 0);
        Money listPrice = s.originalPrice().subtract(s.originalPrice().percentage(depreciation));
        return new Recovery(Option.EXCHANGE, reason, null, null,
                s.originalPrice(), depreciation, listPrice,
                instant(s.policySnapshot().get("exchangeListingDeadline")));
    }

    private static boolean refundWindowActive(RecoverySubject s, Instant now) {
        Instant ends = instant(s.policySnapshot().get("refundWindowEndsAt"));
        return ends != null && now.isBefore(ends);
    }

    private static boolean withinListingDeadline(RecoverySubject s, Instant now) {
        Instant deadline = instant(s.policySnapshot().get("exchangeListingDeadline"));
        return deadline == null || now.isBefore(deadline);
    }

    private static Instant instant(Object value) {
        return value == null ? null : Instant.parse(value.toString());
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
