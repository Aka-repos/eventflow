# EventFlow API — 02. Modelo Único de Errores (RFC 9457)

Todo error se responde con `Content-Type: application/problem+json` y el esquema **`Problem`** (extensión de RFC 9457). No existe ningún otro formato de error en la API.

## 1. Esquema `Problem`

| Campo | Tipo | Siempre | Descripción |
|---|---|---|---|
| `type` | string (URI) | sí | identificador estable del tipo: `https://api.eventflow.app/errors/<code>`; los clientes enrutan por `code`, `type` es documentación |
| `title` | string | sí | resumen humano corto, estable por `code` (inglés) |
| `status` | integer | sí | espejo del status HTTP |
| `detail` | string | sí | mensaje seguro y accionable para mostrar/loguear (es); sin datos sensibles |
| `instance` | string | sí | **path** del request (`/api/v1/orders/123/pay`) |
| `code` | string | sí | **ErrorCode** máquina, `snake_case` (catálogo §3) — la clave de enrutamiento del cliente |
| `timestamp` | string ISO-8601 UTC | sí | momento del error |
| `traceId` | string | sí | = `X-Correlation-ID`; enlaza con logs del servidor |
| `errors` | array `FieldError` | solo validación | detalle por campo |
| `retryAfterSeconds` | integer | solo 429/503 | espejo del header `Retry-After` |
| `conflictVersion` | integer | solo `version_conflict` | versión vigente del recurso para recargar |

`FieldError = { "field": "email", "message": "formato inválido", "code": "invalid_format", "rejectedValue"?: <no sensible> }`

### Ejemplo — validación (422)

```json
{
  "type": "https://api.eventflow.app/errors/validation_error",
  "title": "Validation failed",
  "status": 422,
  "detail": "La solicitud contiene 2 campos inválidos",
  "instance": "/api/v1/auth/register",
  "code": "validation_error",
  "timestamp": "2026-07-09T14:03:22Z",
  "traceId": "0b6c9d1e-4a1f-4b0e-9c2d-7f8a1b2c3d4e",
  "errors": [
    { "field": "email", "message": "debe ser un email válido", "code": "invalid_format" },
    { "field": "password", "message": "mínimo 8 caracteres", "code": "too_short" }
  ]
}
```

### Ejemplo — conflicto de negocio (409)

```json
{
  "type": "https://api.eventflow.app/errors/listing_not_available",
  "title": "Listing not available",
  "status": 409,
  "detail": "El boleto ya fue reservado por otro usuario",
  "instance": "/api/v1/exchange-listings/9f2c…/reservations",
  "code": "listing_not_available",
  "timestamp": "2026-07-09T14:05:10Z",
  "traceId": "77aa…"
}
```

## 2. Familias de error (taxonomía)

| Familia | HTTP | Naturaleza |
|---|---|---|
| ValidationError | 400 (sintaxis) / 422 (semántica) | el cliente puede corregir el request |
| SecurityError | 401 / 403 | autenticación / autorización / visibilidad de QR |
| NotFoundError | 404 | inexistente o ajeno (anti-enumeración) |
| ConflictError | 409 | estado o concurrencia: reintentable tras releer |
| BusinessError | 402 / 422 | regla de negocio: no reintentable sin cambiar condiciones |
| RateLimitError | 429 | reintentar tras `Retry-After` |
| ServerError | 500 / 503 | opaco; reportar con `traceId` |

## 3. Catálogo `ErrorCode` (cerrado; ampliar = cambio aditivo)

### Genéricos
`malformed_request` 400 · `validation_error` 422 · `not_found` 404 · `internal_error` 500 · `service_unavailable` 503 · `rate_limited` 429 · `idempotency_key_required` 422 · `idempotency_key_reuse` 422 · `version_conflict` 409

### Seguridad
`unauthorized` 401 · `token_expired` 401 · `token_invalid` 401 · `refresh_token_reused` 401 (revoca familia; el cliente fuerza re-login) · `forbidden` 403 · `account_blocked` 403 · `email_already_registered` 409 · `invalid_credentials` 401

### Catálogo/eventos
`event_not_published` 409 · `event_sold_out` 409 (payload sugiere waitlist si `waitlistEnabled`) · `sales_window_closed` 422

Registro aditivo Módulo 2 (2026-07-11, per engineering/07 §2 "registradas en api/02 si son nuevas"):
`event_not_draft` 409 (publicar/eliminar exige DRAFT) · `event_not_publishable` 422 (publicar sin tarifas) · `category_in_use` 409 (DELETE categoría con eventos) · `category_name_taken` 409 (nombre único) · `zone_in_use` 409 (DELETE zona con tarifas) · `ticket_type_has_sales` 409 (editar/eliminar tarifa con vendidos)

### Órdenes/pagos
`order_expired` 409 · `order_not_pending` 409 · `payment_failed` 402 (con `detail` del motivo del proveedor, saneado) · `payment_in_progress` 409 · `currency_mismatch` 422

### Boletos/QR/check-in
`qr_not_yet_visible` 403 · `qr_invalid` 422 · `qr_expired` 422 · `ticket_blocked` 409 · `already_used` 409 · `checkin_wrong_event` 422 · `staff_not_assigned` 403

### Cancelación inteligente / reembolsos (ADR-19)
`refund_window_closed` 422 · `refund_not_allowed_exchange_acquired` 422 · `refund_already_requested` 409 · `refund_not_pending` 409

### Exchange
`exchange_disabled` 422 · `listing_deadline_passed` 422 · `listing_not_available` 409 · `listing_not_cancellable` 409 · `reservation_expired` 409 · `cannot_buy_own_listing` 422 · `ticket_not_listable` 409 (estado no ACTIVE)

### Waitlist
`waitlist_disabled` 422 · `already_in_waitlist` 409 · `event_not_sold_out` 422 · `offer_expired` 409 · `offer_not_yours` 404

### Parking
`no_slots_available` 409 · `slot_not_reserved` 409 · `parking_closed` 422

## 4. Reglas de implementación (contrato, no código)

1. Un `code` ↔ un `type` URI ↔ un `title`: estables para siempre; `detail` puede variar.
2. El backend mapea excepciones de dominio → `code` en un único `@ControllerAdvice` (RFC 9457 nativo de Spring, `ProblemDetail`).
3. Android enruta por `code` (no por `detail` ni `title`) → `AppError` tipado (doc 09).
4. `500` jamás incluye causa interna; siempre incluye `traceId`.
5. Los errores de violación de índices únicos parciales de BD (carrera perdida) se traducen al `code` de conflicto correspondiente, nunca a `internal_error`.

## Observaciones

| # | Problema | Impacto | Solución | ¿Dominio o contrato? |
|---|---|---|---|---|
| O7 | RFC 9457 usa `instance`/`detail`; el requerimiento pedía `path`/`message` | nomenclatura divergente | `instance` cumple el rol de `path` y `detail` el de `message`; se mantienen los nombres RFC para compatibilidad con `ProblemDetail` de Spring y tooling estándar | solo contrato |
