package com.eventflow.modules.catalog.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class ZoneInUseException extends DomainException {

    public ZoneInUseException() {
        super(ErrorCode.ZONE_IN_USE, "La zona tiene tarifas asociadas y no puede eliminarse");
    }
}
