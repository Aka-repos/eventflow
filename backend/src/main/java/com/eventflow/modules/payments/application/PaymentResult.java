package com.eventflow.modules.payments.application;

import com.eventflow.shared.domain.Money;

import java.util.UUID;

/** Resultado público de la fachada (doc 10 §4): ningún módulo importa payments.domain. */
public record PaymentResult(UUID id, String provider, String status, Money amount, String failureReason) {

    public boolean approved() {
        return "APPROVED".equals(status);
    }

    public boolean declined() {
        return "DECLINED".equals(status);
    }
}
