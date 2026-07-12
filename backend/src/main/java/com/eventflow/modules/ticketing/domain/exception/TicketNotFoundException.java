package com.eventflow.modules.ticketing.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class TicketNotFoundException extends DomainException {

    public TicketNotFoundException() {
        super(ErrorCode.NOT_FOUND, "El boleto no existe");
    }
}
