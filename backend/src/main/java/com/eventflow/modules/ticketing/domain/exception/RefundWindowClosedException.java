package com.eventflow.modules.ticketing.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

/** 422: la ventana de reembolso del boleto expiró (o la política no permite reembolso). */
public class RefundWindowClosedException extends DomainException {

    public RefundWindowClosedException() {
        super(ErrorCode.REFUND_WINDOW_CLOSED, "La ventana de reembolso de este boleto no está activa");
    }
}
