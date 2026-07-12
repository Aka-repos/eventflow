package com.eventflow.modules.catalog.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class ZoneNotFoundException extends DomainException {

    public ZoneNotFoundException() {
        super(ErrorCode.NOT_FOUND, "La zona no existe");
    }
}
