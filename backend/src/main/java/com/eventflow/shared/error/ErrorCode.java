package com.eventflow.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Catálogo cerrado de códigos de error del contrato (docs/api/02-api-error-model.md).
 * Ampliarlo es cambio aditivo del contrato: registrar primero en el documento.
 */
public enum ErrorCode {
    // Genéricos
    MALFORMED_REQUEST("malformed_request", HttpStatus.BAD_REQUEST, "Malformed request"),
    VALIDATION_ERROR("validation_error", HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed"),
    NOT_FOUND("not_found", HttpStatus.NOT_FOUND, "Resource not found"),
    VERSION_CONFLICT("version_conflict", HttpStatus.CONFLICT, "Version conflict"),
    RATE_LIMITED("rate_limited", HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded"),
    INTERNAL_ERROR("internal_error", HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),

    // Seguridad
    UNAUTHORIZED("unauthorized", HttpStatus.UNAUTHORIZED, "Authentication required"),
    TOKEN_EXPIRED("token_expired", HttpStatus.UNAUTHORIZED, "Access token expired"),
    TOKEN_INVALID("token_invalid", HttpStatus.UNAUTHORIZED, "Token invalid"),
    REFRESH_TOKEN_REUSED("refresh_token_reused", HttpStatus.UNAUTHORIZED, "Refresh token reuse detected"),
    INVALID_CREDENTIALS("invalid_credentials", HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    FORBIDDEN("forbidden", HttpStatus.FORBIDDEN, "Forbidden"),
    ACCOUNT_BLOCKED("account_blocked", HttpStatus.FORBIDDEN, "Account blocked"),
    EMAIL_ALREADY_REGISTERED("email_already_registered", HttpStatus.CONFLICT, "Email already registered");

    private final String code;
    private final HttpStatus status;
    private final String title;

    ErrorCode(String code, HttpStatus status, String title) {
        this.code = code;
        this.status = status;
        this.title = title;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
