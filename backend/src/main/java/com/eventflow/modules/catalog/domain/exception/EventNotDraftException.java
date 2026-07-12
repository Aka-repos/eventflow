package com.eventflow.modules.catalog.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class EventNotDraftException extends DomainException {

    public EventNotDraftException(String detail) {
        super(ErrorCode.EVENT_NOT_DRAFT, detail);
    }
}
