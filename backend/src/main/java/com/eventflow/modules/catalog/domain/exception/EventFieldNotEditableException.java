package com.eventflow.modules.catalog.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

/** Campos no editables tras publicar (frozen: 409 event_not_published en PATCH). */
public class EventFieldNotEditableException extends DomainException {

    public EventFieldNotEditableException() {
        super(ErrorCode.EVENT_NOT_PUBLISHED, "El evento publicado solo admite campos seguros "
                + "(description, coverUrl, address, startsAt, endsAt)");
    }
}
