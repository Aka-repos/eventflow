package com.eventflow.modules.catalog.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class SponsorNotFoundException extends DomainException {

    public SponsorNotFoundException() {
        super(ErrorCode.NOT_FOUND, "El patrocinador no existe");
    }
}
