package com.eventflow.modules.ticketing.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

import java.time.Instant;

/** 403: el QR aún no entra en su ventana de visibilidad (ADR-03). */
public class QrNotYetVisibleException extends DomainException {

    public QrNotYetVisibleException(Instant availableAt) {
        super(ErrorCode.QR_NOT_YET_VISIBLE, "El QR estará disponible a partir de " + availableAt);
    }
}
