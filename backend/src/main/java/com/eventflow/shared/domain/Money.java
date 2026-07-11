package com.eventflow.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value Object de dinero (ADR-05). Inmutable; BigDecimal escala 2 + ISO-4217.
 * Regla de redondeo oficial (auditoría M1): porcentajes con HALF_UP a 2 decimales.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (currency == null || !currency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("currency must be ISO-4217 uppercase");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        BigDecimal result = amount.subtract(other.amount);
        if (result.signum() < 0) {
            throw new IllegalArgumentException("resulting amount must not be negative");
        }
        return new Money(result, currency);
    }

    /** fee = round_half_up(amount × pct / 100, 2) — regla M1. */
    public Money percentage(int pct) {
        if (pct < 0 || pct > 100) {
            throw new IllegalArgumentException("pct must be between 0 and 100");
        }
        BigDecimal result = amount.multiply(BigDecimal.valueOf(pct))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Money(result, currency);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}
