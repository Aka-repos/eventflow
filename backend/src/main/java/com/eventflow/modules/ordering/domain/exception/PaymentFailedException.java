package com.eventflow.modules.ordering.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

/** 402: el proveedor rechazó el cobro; la orden queda FAILED y el inventario liberado (S2). */
public class PaymentFailedException extends DomainException {

    public PaymentFailedException(String reason) {
        super(ErrorCode.PAYMENT_FAILED, reason);
    }
}
