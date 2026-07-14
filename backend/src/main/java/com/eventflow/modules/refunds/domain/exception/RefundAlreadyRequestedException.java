package com.eventflow.modules.refunds.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class RefundAlreadyRequestedException extends DomainException {

    public RefundAlreadyRequestedException() {
        super(ErrorCode.REFUND_ALREADY_REQUESTED,
                "Este boleto ya tiene una solicitud de reembolso activa o está publicado en el Exchange");
    }
}
