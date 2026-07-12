package com.eventflow.modules.catalog.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

/** 404 también para eventos ajenos (anti-enumeración, api/02 §2). */
public class EventNotFoundException extends DomainException {

    public EventNotFoundException() {
        super(ErrorCode.NOT_FOUND, "El evento no existe");
    }
}
