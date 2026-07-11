package com.eventflow.shared.error;

/**
 * Base de todas las excepciones de dominio. Cada una mapea 1:1 a un ErrorCode del contrato
 * y el ControllerAdvice la traduce a RFC 9457 — los controllers jamás las capturan.
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    protected DomainException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
