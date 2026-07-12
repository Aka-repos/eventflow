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
    EMAIL_ALREADY_REGISTERED("email_already_registered", HttpStatus.CONFLICT, "Email already registered"),

    // Catálogo/eventos (los nuevos están registrados aditivamente en docs/api/02 §3)
    EVENT_NOT_PUBLISHED("event_not_published", HttpStatus.CONFLICT, "Event not published"),
    EVENT_NOT_DRAFT("event_not_draft", HttpStatus.CONFLICT, "Event not in draft"),
    EVENT_NOT_PUBLISHABLE("event_not_publishable", HttpStatus.UNPROCESSABLE_ENTITY, "Event not publishable"),
    CATEGORY_IN_USE("category_in_use", HttpStatus.CONFLICT, "Category in use"),
    CATEGORY_NAME_TAKEN("category_name_taken", HttpStatus.CONFLICT, "Category name already exists"),
    ZONE_IN_USE("zone_in_use", HttpStatus.CONFLICT, "Zone has tariffs"),
    TICKET_TYPE_HAS_SALES("ticket_type_has_sales", HttpStatus.CONFLICT, "Ticket type has sales"),
    EVENT_SOLD_OUT("event_sold_out", HttpStatus.CONFLICT, "Event sold out"),
    SALES_WINDOW_CLOSED("sales_window_closed", HttpStatus.UNPROCESSABLE_ENTITY, "Sales window closed"),

    // Órdenes/pagos (api/02 §3, catálogo congelado)
    ORDER_EXPIRED("order_expired", HttpStatus.CONFLICT, "Order expired"),
    ORDER_NOT_PENDING("order_not_pending", HttpStatus.CONFLICT, "Order not pending"),
    PAYMENT_FAILED("payment_failed", HttpStatus.PAYMENT_REQUIRED, "Payment failed"),
    PAYMENT_IN_PROGRESS("payment_in_progress", HttpStatus.CONFLICT, "Payment in progress"),
    CURRENCY_MISMATCH("currency_mismatch", HttpStatus.UNPROCESSABLE_ENTITY, "Currency mismatch"),
    IDEMPOTENCY_KEY_REQUIRED("idempotency_key_required", HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key required"),
    IDEMPOTENCY_KEY_REUSE("idempotency_key_reuse", HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reuse");

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
