# EventFlow API — 04. Catálogo Completo de Endpoints

> Convenciones del doc 01 aplican a todos: base `/api/v1`, Bearer JWT salvo 🌐 (público), ⚡ = `Idempotency-Key` obligatorio, 🔒 = `If-Match` (optimistic lock). Errores: además de los listados, todo endpoint puede devolver `400, 401, 429, 500` (y ⚡ los de idempotencia). DTOs → doc 03; definición ejecutable → `05-openapi.yaml`.

## 1. Auth — rol: público

| Método/Ruta | Body → Response | Errores específicos |
|---|---|---|
| 🌐 POST `/auth/register` | `RegisterRequest` → 201 `AuthTokensResponse` | 409 `email_already_registered`, 422 |
| 🌐 POST `/auth/login` | `LoginRequest` → 200 `AuthTokensResponse` | 401 `invalid_credentials`, 403 `account_blocked` |
| 🌐 POST `/auth/refresh` | `RefreshRequest` → 200 `AuthTokensResponse` | 401 `token_invalid`, 401 `refresh_token_reused` |
| POST `/auth/logout` | `LogoutRequest` → 204 | — |

## 2. Catálogo — rol: público (🌐) / cualquiera autenticado

| Método/Ruta | Params | Response | Errores |
|---|---|---|---|
| 🌐 GET `/events` | `q, categoryId, dateFrom, dateTo, nearLat, nearLng, radiusKm, sort(startsAt,-startsAt), cursor, limit` | 200 `[EventSummary]` + `CursorMeta` | — |
| 🌐 GET `/events/{eventId}` | — | 200 `EventDetail` | 404 |
| 🌐 GET `/events/{eventId}/exchange-listings` | `cursor, limit` | 200 `[ListingResponse]` (solo `PUBLISHED`, O4) | 404 |
| 🌐 GET `/events/{eventId}/parkings` | — | 200 `[ParkingSummary]` | 404 |
| 🌐 GET `/categories` | — | 200 `[CategoryDto]` | — |

## 3. Órdenes y pagos — rol: ATTENDEE+ (dueño de la orden)

| Método/Ruta | Body → Response | Errores específicos |
|---|---|---|
| ⚡ POST `/orders` | `CreateOrderRequest` → 201 `OrderResponse(PENDING)` | 409 `event_sold_out`/`no_slots_available`/`listing_not_available`, 422 `sales_window_closed`/`exchange` reservas |
| ⚡ POST `/orders/{orderId}/pay` | `PayOrderRequest` → 200 `OrderResponse(PAID)` | 402 `payment_failed`, 409 `order_expired`/`order_not_pending`/`payment_in_progress`, 404 |
| POST `/orders/{orderId}/cancel` | — → 200 `OrderResponse(CANCELLED)` | 409 `order_not_pending`, 404 |
| GET `/orders` | `status?, cursor, limit` | 200 `[OrderResponse]` | — |
| GET `/orders/{orderId}` | — | 200 `OrderResponse` | 404 |

### Ejemplo detallado — ⚡ POST `/orders/{orderId}/pay`

```http
POST /api/v1/orders/ord-7f3a/pay HTTP/1.1
Authorization: Bearer eyJ…
Idempotency-Key: 3f8e4b2c-9d1a-4c5e-8b7f-2a1d3e4f5a6b
Content-Type: application/json

{ "method": "FAKE" }
```
```json
// 200
{ "data": { "id": "ord-7f3a", "status": "PAID", "total": { "amount": "87.00", "currency": "USD" },
  "expiresAt": "2026-07-09T15:00:00Z", "createdAt": "2026-07-09T14:45:00Z",
  "items": [ { "id": "oi-1", "type": "TICKET", "description": "VIP — Concierto X", "quantity": 1,
               "unitPrice": { "amount": "80.00", "currency": "USD" }, "ticketIds": ["tk-91b2"] },
             { "id": "oi-2", "type": "PARKING", "description": "Parking General", "quantity": 1,
               "unitPrice": { "amount": "7.00", "currency": "USD" } } ],
  "payment": { "id": "pay-11", "provider": "FAKE", "status": "APPROVED", "amount": { "amount": "87.00", "currency": "USD" } } } }
```
```json
// 402 payment_failed (Problem)
{ "type": "https://api.eventflow.app/errors/payment_failed", "title": "Payment failed", "status": 402,
  "detail": "La tarjeta fue rechazada por el emisor", "instance": "/api/v1/orders/ord-7f3a/pay",
  "code": "payment_failed", "timestamp": "2026-07-09T14:46:02Z", "traceId": "…" }
```

## 4. Boletos y cancelación inteligente — rol: ATTENDEE+ (propietario actual)

| Método/Ruta | Response | Errores específicos |
|---|---|---|
| GET `/tickets` | `status?, cursor, limit` → 200 `[TicketResponse]` | — |
| GET `/tickets/{ticketId}` | 200 `TicketDetail` | 404 |
| GET `/tickets/{ticketId}/qr` | 200 `QrResponse` | 403 `qr_not_yet_visible`, 409 `ticket_blocked`, 404 |
| GET `/tickets/{ticketId}/recovery-options` | 200 `RecoveryOptionsResponse` | 404 |
| ⚡ POST `/tickets/{ticketId}/refund-requests` | `CreateRefundRequest` → 201 `RefundResponse` | 422 `refund_window_closed`/`refund_not_allowed_exchange_acquired` (ADR-19), 409 `refund_already_requested`/`ticket_blocked`, 404 |
| ⚡ POST `/tickets/{ticketId}/exchange-listings` | — → 201 `MyListingResponse` | 422 `exchange_disabled`/`listing_deadline_passed`, 409 `ticket_not_listable`, 404 |

### Ejemplo — GET `/tickets/{id}/recovery-options` (boleto adquirido en Exchange, ADR-19)

```json
// 200
{ "data": { "ticketId": "tk-91b2", "option": "EXCHANGE", "reason": "ACQUIRED_VIA_EXCHANGE",
  "refund": null,
  "exchange": { "originalPrice": { "amount": "80.00", "currency": "USD" }, "depreciationPct": 10,
                "listPrice": { "amount": "72.00", "currency": "USD" },
                "listingDeadline": "2026-08-14T00:00:00Z" },
  "links": { "action": "/api/v1/tickets/tk-91b2/exchange-listings" } } }
```

## 5. Exchange — rol: ATTENDEE+

| Método/Ruta | Body → Response | Errores específicos |
|---|---|---|
| ⚡ POST `/exchange-listings/{listingId}/reservations` | — → 201 `CreateReservationResponse` | 409 `listing_not_available`, 422 `cannot_buy_own_listing`, 404 |
| DELETE `/exchange-listings/{listingId}` | (vendedor) → 204 | 409 `listing_not_cancellable`, 404 |
| GET `/me/exchange-listings` | `status?, cursor, limit` → 200 `[MyListingResponse]` | — |

La compra se completa con ⚡ POST `/orders` (`items[{type: EXCHANGE_TICKET, referenceId: <reservationId>}]`) + `/pay` — mismo pipeline que toda compra.

## 6. Waitlist — rol: ATTENDEE+

| Método/Ruta | Body → Response | Errores específicos |
|---|---|---|
| POST `/events/{eventId}/waitlist` | — → 201 `WaitlistStatusResponse` | 422 `waitlist_disabled`/`event_not_sold_out`, 409 `already_in_waitlist`, 404 |
| DELETE `/events/{eventId}/waitlist` | — → 204 | 404 |
| GET `/me/waitlist` | → 200 `[WaitlistStatusResponse]` | — |
| GET `/me/waitlist-offers` | → 200 `[WaitlistOfferResponse]` | — |
| ⚡ POST `/waitlist-offers/{offerId}/accept` | — → 201 `AcceptOfferResponse` (contiene la Orden a pagar) | 409 `offer_expired`, 404 `offer_not_yours` |
| POST `/waitlist-offers/{offerId}/decline` | — → 204 | 409 `offer_expired`, 404 |

## 7. Yo (perfil, favoritos, notificaciones, parking) — rol: cualquiera autenticado

| Método/Ruta | Body → Response |
|---|---|
| GET `/me` → 200 `UserProfile` · PUT `/me` `UpdateProfileRequest` → 200 `UserProfile` |
| POST `/me/devices` `RegisterDeviceRequest` → 204 |
| GET `/me/favorites` → 200 `[EventSummary]` · PUT `/me/favorites/{eventId}` → 204 · DELETE → 204 (404 si evento no existe) |
| GET `/me/notifications` `unreadOnly?, cursor, limit` → 200 `[NotificationResponse]` · PUT `/me/notifications/{id}/read` → 204 (O3) |
| GET `/me/parking-reservations` `status?, cursor, limit` → 200 `[ParkingReservationResponse]` |
| GET `/me/parking-reservations/{id}/qr` → 200 `QrResponse` (403 `qr_not_yet_visible`) |

## 8. Check-in — rol: ORGANIZER (su evento) / STAFF (evento asignado)

| Método/Ruta | Body → Response | Errores específicos |
|---|---|---|
| ⚡ POST `/organizer/events/{eventId}/check-ins` | `CheckInRequest` → 200 `CheckInResponse` | 422 `qr_invalid`/`qr_expired`/`checkin_wrong_event`, 409 `already_used`/`ticket_blocked`, 403 `staff_not_assigned` |
| ⚡ POST `/organizer/parkings/{parkingId}/check-ins` | `CheckInRequest` → 200 `CheckInResponse` | ídem + 409 `slot_not_reserved` |
| ⚡ POST `/organizer/parkings/{parkingId}/check-outs` | `CheckInRequest` → 200 `CheckInResponse` | 409 `slot_not_reserved` |

## 9. Organizador — rol: ORGANIZER (dueño del evento; ADMIN supervisa)

| Método/Ruta | Body → Response | Errores |
|---|---|---|
| POST `/organizer/events` | `CreateEventRequest` → 201 `EventDetail(DRAFT)` | 422 |
| GET `/organizer/events` | `status?, cursor, limit` → 200 `[EventSummary]` | — |
| PATCH 🔒 `/organizer/events/{eventId}` | `UpdateEventRequest` → 200 `EventDetail` | 409 `version_conflict`/`event_not_published` (campos no editables tras publicar), 404 |
| DELETE `/organizer/events/{eventId}` | → 204 (soft; solo DRAFT) | 409, 404 |
| POST `/organizer/events/{eventId}/publish` | → 200 `EventDetail(PUBLISHED)` | 422 (sin tarifas/política), 409 |
| GET/PUT 🔒 `/organizer/events/{eventId}/policy` | `EventPolicyRequest` → 200 `EventPolicyResponse` | 409 `version_conflict`, 404 |
| POST/PATCH 🔒/DELETE `/organizer/events/{eventId}/ticket-types[/{id}]` | `CreateTicketTypeRequest` → 201/200/204 | 409 (vendidos>0 restringe), 404 |
| POST/DELETE `/organizer/events/{eventId}/zones[/{id}]` | `CreateZoneRequest` → 201/204 | 409, 404 |
| POST/PATCH 🔒/DELETE `/organizer/events/{eventId}/parkings[/{id}]` | `CreateParkingRequest` → 201/200/204 | 409, 404 |
| GET `/organizer/events/{eventId}/refund-requests` | `status?, cursor, limit` → 200 `[RefundResponse]` | 404 |
| ⚡ POST `/refund-requests/{refundId}/approve` | — → 200 `RefundResponse(APPROVED)` | 409 `refund_not_pending`, 404 |
| ⚡ POST `/refund-requests/{refundId}/reject` | `RejectRefundRequest` → 200 `RefundResponse(REJECTED)` | 409 `refund_not_pending`, 404 |
| ⚡ POST `/organizer/tickets/{ticketId}/invalidate` | — → 200 `TicketResponse` | 409, 404 |
| ⚡ POST `/organizer/tickets/{ticketId}/reissue` | — → 200 `TicketResponse` (QR nuevo) | 409, 404 |
| POST/DELETE `/organizer/events/{eventId}/staff[/{userId}]` | `AssignStaffRequest` → 201/204 | 404 |
| GET `/organizer/events/{eventId}/stats` | → 200 `EventStatsResponse` | 404 |
| GET `/organizer/dashboard` | `from?, to?` → 200 `OrganizerDashboardResponse` | — |

## 10. Administrador — rol: ADMIN

| Método/Ruta | Body → Response |
|---|---|
| GET `/admin/users` `q?, role?, status?, cursor, limit` → 200 `[AdminUserResponse]` |
| GET `/admin/users/{userId}` → 200 `AdminUserResponse` · PATCH 🔒 → 200 (`AdminUpdateUserRequest`) · DELETE → 204 (soft) |
| POST/PATCH/DELETE `/admin/categories[/{id}]` `CategoryRequest` → 201/200/204 (409 en DELETE si en uso) |
| POST/PATCH/DELETE `/admin/sponsors[/{id}]` `SponsorRequest` → 201/200/204 |
| GET `/admin/config` → 200 `[GlobalConfigEntry]` · PUT 🔒 `/admin/config/{key}` → 200 `GlobalConfigEntry` (409 `version_conflict`) |
| GET `/admin/dashboard` `from?, to?` → 200 `AdminDashboardResponse` |
| GET `/admin/reports` `type, from, to, cursor?` → 200 (por tipo: ventas, reembolsos, exchange, waitlist) |

## Matriz de autorización (resumen)

| Prefijo | ATTENDEE | STAFF | ORGANIZER | ADMIN |
|---|---|---|---|---|
| `/auth`, catálogo 🌐 | ✔ | ✔ | ✔ | ✔ |
| `/orders`, `/tickets`, `/me/**`, exchange, waitlist | ✔ (recursos propios) | ✔ | ✔ | — |
| `/organizer/**` check-ins | — | ✔ evento asignado | ✔ evento propio | — |
| `/organizer/**` resto | — | — | ✔ evento propio | ✔ lectura |
| `/admin/**` | — | — | — | ✔ |

## Observaciones

| # | Problema | Impacto | Solución | ¿Dominio o contrato? |
|---|---|---|---|---|
| O8 | El diseño (doc 05 previo) no definía `GET /me/exchange-listings`, `GET /me/waitlist`, `decline` de ofertas ni QR de parking del asistente | Android no podría renderizar "mis publicaciones/mi posición/mi QR de parking" | endpoints añadidos; leen datos ya existentes en el modelo | solo contrato (aditivo) |
| O9 | `EventStatsResponse` requiere `occupancyPct` y series por mes; el dominio ya lo soporta vía ledger/proyecciones | ninguno | documentado como agregación de lectura | solo contrato |
| O10 | Cupo numérico público (`available: boolean` vs cantidad exacta) | exponer cantidades facilita scraping/especulación | el contrato publica disponibilidad booleana + `priceFrom`; cantidades exactas solo en `/organizer/**` | solo contrato |
