package com.eventflow.modules.ticketing.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

/** 422 (ADR-19): un boleto adquirido en el Exchange jamás puede reembolsarse. */
public class RefundNotAllowedExchangeException extends DomainException {

    public RefundNotAllowedExchangeException() {
        super(ErrorCode.REFUND_NOT_ALLOWED_EXCHANGE_ACQUIRED,
                "Este boleto fue adquirido en el Exchange y solo puede re-publicarse, no reembolsarse");
    }
}
