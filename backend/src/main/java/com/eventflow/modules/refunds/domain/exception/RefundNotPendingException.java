package com.eventflow.modules.refunds.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class RefundNotPendingException extends DomainException {

    public RefundNotPendingException(String currentStatus) {
        super(ErrorCode.REFUND_NOT_PENDING, "El reembolso no está pendiente (estado: " + currentStatus + ")");
    }
}
