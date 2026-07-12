package com.eventflow.modules.catalog.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class EventNotPublishableException extends DomainException {

    public EventNotPublishableException(String detail) {
        super(ErrorCode.EVENT_NOT_PUBLISHABLE, detail);
    }
}
