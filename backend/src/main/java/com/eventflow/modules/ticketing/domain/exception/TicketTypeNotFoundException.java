package com.eventflow.modules.ticketing.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class TicketTypeNotFoundException extends DomainException {

    public TicketTypeNotFoundException() {
        super(ErrorCode.NOT_FOUND, "La tarifa no existe");
    }
}
