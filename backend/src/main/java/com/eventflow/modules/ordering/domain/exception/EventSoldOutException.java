package com.eventflow.modules.ordering.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class EventSoldOutException extends DomainException {

    public EventSoldOutException(String detail) {
        super(ErrorCode.EVENT_SOLD_OUT, detail);
    }
}
