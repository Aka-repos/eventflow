# EventFlow API — 03. Catálogo de DTOs

> Los DTOs son el contrato serializado: **independientes del dominio** (jamás se exponen entidades JPA/Room). La definición ejecutable con validaciones exactas vive en [`05-openapi.yaml`](05-openapi.yaml) (`components.schemas`); este catálogo es su vista legible. Convenciones: camelCase, enums UPPER_SNAKE, fechas ISO-8601 UTC, `Money{amount: string, currency}`; campos no marcados `null` son obligatorios en el JSON.

## 0. Tipos compartidos

| DTO | Campos | Notas |
|---|---|---|
| `Money` | `amount: string(decimal, regex ^\d+\.\d{2}$)`, `currency: string(3, USD)` | nunca número JSON |
| `CursorMeta` | `nextCursor: string?`, `hasNext: boolean` | doc 07 |
| `Problem`, `FieldError` | doc 02 | errores |

## 1. Auth (`/auth`)

| DTO | Campos y validación |
|---|---|
| `RegisterRequest` | `email: string(email, ≤255)` · `password: string(8–72)` · `fullName: string(1–200)` · `phone: string?(E.164)` |
| `LoginRequest` | `email` · `password` |
| `RefreshRequest` | `refreshToken: string` |
| `AuthTokensResponse` | `accessToken: string(JWT)` · `accessTokenExpiresIn: int(seg)` · `refreshToken: string` · `user: UserProfile` |
| `LogoutRequest` | `refreshToken: string` |

```json
// POST /auth/login → 200
{ "data": { "accessToken": "eyJ…", "accessTokenExpiresIn": 900, "refreshToken": "d4f…",
  "user": { "id": "u-…", "email": "ana@mail.com", "fullName": "Ana P.", "roles": ["ATTENDEE"] } } }
```

## 2. Usuarios / perfil

| DTO | Campos |
|---|---|
| `UserProfile` | `id: uuid` · `email` · `fullName` · `phone?` · `roles: [RoleCode]` · `createdAt` |
| `UpdateProfileRequest` | `fullName: string(1–200)` · `phone: string?(E.164)` |
| `RegisterDeviceRequest` | `fcmToken: string` · `platform: enum(ANDROID,IOS,WEB)` |

Enum `RoleCode`: `ADMIN, ORGANIZER, STAFF, ATTENDEE`.

## 3. Catálogo de eventos

| DTO | Campos |
|---|---|
| `EventSummary` | `id` · `title` · `venueName` · `startsAt` · `endsAt` · `timezone: string(IANA)` · `status: EventStatus` · `coverUrl?` · `category: CategoryDto` · `priceFrom: Money?` · `isFavorite: boolean?` (solo autenticado) |
| `EventDetail` | `EventSummary` + `description` · `address?` · `latitude?: number` · `longitude?: number` · `organizer: {id, name}` · `ticketTypes: [TicketTypeDto]` · `zones: [ZoneDto]` · `parkings: [ParkingSummary]` · `sponsors: [SponsorDto]` · `policies: EventPolicyPublic` · `waitlistOpen: boolean` |
| `EventPolicyPublic` | `refundWindowEndsAt?` · `exchangeEnabled: boolean` · `exchangeDepreciationPct: int` · `waitlistEnabled: boolean` · `qrVisibilityHoursBefore: int` — subset **público** de la política (la completa es de organizador) |
| `TicketTypeDto` | `id` · `name` · `description?` · `price: Money` · `zoneName?` · `available: boolean` · `salesEndsAt?` |
| `ZoneDto` | `id` · `name` · `capacity: int` |
| `CategoryDto` | `id: int` · `name` · `icon?` |
| `SponsorDto` | `id` · `name` · `logoUrl?` · `website?` |

Enum `EventStatus`: `DRAFT, PUBLISHED, SOLD_OUT, IN_PROGRESS, FINISHED, CANCELLED, SUSPENDED`.
Nota: `available` es booleano — el cupo numérico exacto NO se expone al público (anti-scraping/reventa).

## 4. Órdenes y pagos

| DTO | Campos y validación |
|---|---|
| `CreateOrderRequest` | `items: [OrderItemRequest](1–10)` |
| `OrderItemRequest` | `type: OrderItemType` · `referenceId: uuid` (TICKET→ticketTypeId · PARKING→parkingId · EXCHANGE_TICKET→temporalReservationId) · `quantity: int(1–10; =1 si EXCHANGE_TICKET)` — **sin precios** |
| `PayOrderRequest` | `method: PaymentMethod` |
| `OrderResponse` | `id` · `status: OrderStatus` · `total: Money` · `expiresAt` · `createdAt` · `items: [OrderItemResponse]` · `payment?: PaymentSummary` |
| `OrderItemResponse` | `id` · `type` · `description: string` (armada por el servidor) · `quantity` · `unitPrice: Money` · `ticketIds?: [uuid]` (tras pago) |
| `PaymentSummary` | `id` · `provider: PaymentProvider` · `status: PaymentStatus` · `amount: Money` |

Enums: `OrderItemType(TICKET,PARKING,EXCHANGE_TICKET)` · `OrderStatus(PENDING,PAID,FAILED,CANCELLED,REFUNDED)` · `PaymentStatus(PENDING,APPROVED,DECLINED,REFUNDED)` · `PaymentProvider(FAKE,STRIPE,PAYPAL,YAPPY,CARD,TRANSFER)` · `PaymentMethod(FAKE,CARD)` (v1).

```json
// POST /orders (Idempotency-Key) → 201
{ "data": { "id": "ord-…", "status": "PENDING", "total": { "amount": "87.00", "currency": "USD" },
  "expiresAt": "2026-07-09T15:00:00Z", "createdAt": "2026-07-09T14:45:00Z",
  "items": [
    { "id": "oi-1", "type": "TICKET",  "description": "VIP — Concierto X", "quantity": 1, "unitPrice": { "amount": "80.00", "currency": "USD" } },
    { "id": "oi-2", "type": "PARKING", "description": "Parking General — Concierto X", "quantity": 1, "unitPrice": { "amount": "7.00", "currency": "USD" } } ] } }
```

## 5. Boletos, QR y cancelación inteligente

| DTO | Campos |
|---|---|
| `TicketResponse` | `id` · `event: EventSummary` · `ticketTypeName` · `zoneName?` · `status: TicketStatus` · `acquiredVia: AcquiredVia` · `purchasedAt` · `qrAvailableAt?: datetime` (cuándo será visible) · `canRecover: boolean` |
| `TicketDetail` | `TicketResponse` + `originalPrice: Money` · `acquisitionPrice: Money` · `history: [TicketHistoryEntry]` |
| `TicketHistoryEntry` | `fromStatus` · `toStatus` · `cause: HistoryCause` · `occurredAt` |
| `QrResponse` | `qrToken: string(JWS opaco)` · `expiresAt` · `refreshAfter: datetime` (re-pedir antes de expirar) |
| `RecoveryOptionsResponse` | `ticketId` · `option: RecoveryOption` · `reason: RecoveryReason` · `refund?: RefundQuote` · `exchange?: ExchangeQuote` · `links: {action?: string}` |
| `RefundQuote` | `amount: Money` (= acquisitionPrice) · `deadline: datetime` |
| `ExchangeQuote` | `originalPrice: Money` · `depreciationPct: int` · `listPrice: Money` · `listingDeadline?: datetime` |

Enums: `TicketStatus(ACTIVE,PUBLISHED_IN_EXCHANGE,REFUND_PENDING,REFUNDED,USED,EXPIRED,CANCELLED,INVALIDATED)` · `AcquiredVia(PRIMARY,EXCHANGE)` · `RecoveryOption(REFUND,EXCHANGE,NONE)` · `RecoveryReason(REFUND_WINDOW_ACTIVE,REFUND_WINDOW_EXPIRED,ACQUIRED_VIA_EXCHANGE,EXCHANGE_DISABLED,TICKET_NOT_RECOVERABLE)` · `HistoryCause` (= CHECK de BD).

Regla ADR-19 en contrato: si `acquiredVia=EXCHANGE`, `option` es `EXCHANGE` o `NONE`, jamás `REFUND`, con `reason=ACQUIRED_VIA_EXCHANGE`.

## 6. Reembolsos

| DTO | Campos |
|---|---|
| `CreateRefundRequest` | `reason: string?(≤500)` |
| `RefundResponse` | `id` · `ticketId` · `amount: Money` · `status: RefundStatus` · `reason?` · `createdAt` · `resolvedAt?` |
| `RejectRefundRequest` | `reason: string(1–500)` |

Enum `RefundStatus(REQUESTED,APPROVED,REJECTED,CANCELLED)`.

## 7. Official Ticket Exchange

| DTO | Campos |
|---|---|
| `ListingResponse` | `id` · `event: EventSummary` · `ticketTypeName` · `zoneName?` · `listPrice: Money` · `originalPrice: Money` · `depreciationPct: int` · `status: ListingStatus` · `expiresAt` — **sin datos del vendedor** (privacidad) |
| `MyListingResponse` | `ListingResponse` + `ticketId` · `estimatedSellerAmount: Money` (informativo, neto de comisión) |
| `CreateReservationResponse` | `id` · `listingId` · `price: Money` · `expiresAt` |

Enum `ListingStatus(WAITLIST_HOLD,PUBLISHED,RESERVED,SOLD,CANCELLED,EXPIRED)` — el marketplace público solo devuelve `PUBLISHED` (Observación O4).

## 8. Waitlist

| DTO | Campos |
|---|---|
| `WaitlistStatusResponse` | `eventId` · `status: WaitlistEntryStatus` · `peopleAhead: int` · `joinedAt` |
| `WaitlistOfferResponse` | `id` · `event: EventSummary` · `source: OfferSource` · `price: Money` (precio de tarifa si INVENTORY; precio de listing si EXCHANGE) · `expiresAt` |
| `AcceptOfferResponse` | `order: OrderResponse` — aceptar crea la orden (flujo C1) |

Enums: `WaitlistEntryStatus(WAITING,OFFERED,FULFILLED,SKIPPED,CANCELLED)` · `OfferSource(INVENTORY,EXCHANGE)` · `OfferStatus(OFFERED,ACCEPTED,EXPIRED,DECLINED)`.

## 9. Parking

| DTO | Campos |
|---|---|
| `ParkingSummary` | `id` · `name` · `type: ParkingType` · `price: Money` · `opensAt` · `closesAt` · `availableSlots: int` |
| `ParkingReservationResponse` | `id` · `parkingName` · `slotCode?` (asignada al check-in) · `event: EventSummary` · `status: ParkingReservationStatus` · `qrAvailableAt?` |

Enums: `ParkingType(VIP,GENERAL,STAFF,MOTO,ACCESSIBLE)` · `ParkingReservationStatus(PENDING,CONFIRMED,IN_USE,COMPLETED,CANCELLED,EXPIRED)`.

## 10. Check-in (organizador/staff)

| DTO | Campos |
|---|---|
| `CheckInRequest` | `qrToken: string` |
| `CheckInResponse` | `result: enum(GRANTED,DENIED)` · `attendeeName?` · `ticketTypeName?` · `zoneName?` · `denialCode?: ErrorCode` · `occurredAt` |

`200` con `result=GRANTED`; los rechazos de validación devuelven Problem (`already_used`, `qr_invalid`…) — `CheckInResponse.DENIED` se reserva para el registro histórico.

## 11. Organizador

| DTO | Campos y validación |
|---|---|
| `CreateEventRequest` | `title(3–200)` · `description` · `categoryId: int` · `venueName` · `address?` · `latitude?/longitude?` (par) · `timezone: IANA` · `startsAt/endsAt` (futuro; ends>starts) |
| `UpdateEventRequest` | = Create, todos opcionales (PATCH, solo DRAFT; publicado solo campos seguros) |
| `EventPolicyRequest` | espejo completo de `event_policies` (sin `event_id`); validaciones = CHECKs de BD; requiere `If-Match` |
| `EventPolicyResponse` | política completa + `version: int` |
| `CreateTicketTypeRequest` | `name` · `description?` · `price: Money` · `zoneId?` · `totalQuantity: int(≥1)` · `salesStartsAt?/salesEndsAt?` |
| `CreateZoneRequest` | `name` · `capacity: int(≥1)` |
| `CreateParkingRequest` | `name` · `type: ParkingType` · `totalSlots: int(≥1)` · `price: Money` · `opensAt/closesAt` |
| `AssignStaffRequest` | `userEmail: string(email)` · `permissions: [string]` |
| `EventStatsResponse` | `ticketsSold: int` · `revenue: Money` · `parkingRevenue: Money` · `attendance: int` · `refundsCount/refundsAmount` · `exchangeListed/exchangeSold: int` · `exchangeFeesGenerated: Money` · `waitlistSize: int` · `occupancyPct: number` |
| `OrganizerDashboardResponse` | agregados por evento y por mes (series `[{period, value}]` para gráficas) |

Excepción justificada a "sin montos en requests": `CreateTicketTypeRequest.price` y `CreateParkingRequest.price` — el organizador **define** precios de venta primaria (es su catálogo); lo prohibido es que el **comprador** envíe montos.

## 12. Administrador

| DTO | Campos |
|---|---|
| `AdminUserResponse` | `UserProfile` + `status: UserStatus` · `deletedAt?` |
| `AdminUpdateUserRequest` | `status?: UserStatus` · `roles?: [RoleCode]` |
| `CategoryRequest` | `name(≤80)` · `icon?` · `active: boolean` |
| `SponsorRequest` | `name(≤120)` · `logoUrl?` · `website?` |
| `GlobalConfigEntry` | `key: string` · `value: object(JSON)` · `description?` · `version: int` (If-Match) |
| `AdminDashboardResponse` | usuarios, eventos/mes, GMV, comisiones, reembolsos, exchange, waitlist (series para gráficas) |

Enum `UserStatus(ACTIVE,BLOCKED,PENDING_VERIFICATION)`.

## 13. Notificaciones y favoritos

| DTO | Campos |
|---|---|
| `NotificationResponse` | `id` · `type: NotificationType` · `title` · `body` · `payload?: object` (deep-link) · `readAt?` · `createdAt` |

Favoritos no tienen DTO propio: `PUT/DELETE /me/favorites/{eventId}` → `204`; `GET /me/favorites` → `[EventSummary]`.

## Reglas de congelamiento

1. Publicado v1, ningún campo se elimina ni cambia de tipo/semántica (doc 06).
2. Todo campo nuevo entra como opcional con default documentado.
3. Android debe tolerar valores enum desconocidos (mapear a `UNKNOWN` interno) — los enums pueden crecer sin v2.
