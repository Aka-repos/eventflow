# EventFlow — Diseño de API REST y Contratos Android ↔ Spring Boot

> ⚠️ **SUPERSEDED (2026-07-09):** este documento fue la exploración de diseño de la API. El **contrato oficial y congelado** vive en [`docs/api/`](../api/01-api-guidelines.md) con `docs/api/05-openapi.yaml` como fuente única de verdad. Diferencias clave: errores en RFC 9457 (`application/problem+json`) en lugar del envelope `{error:{…}}` de este documento, headers formalizados (`X-Correlation-ID`, `If-Match`) y endpoints adicionales. Ante cualquier discrepancia, gana `docs/api/`.

## 1. Convenciones

- Base: `https://<host>/api/v1` — versionado por path desde el día 1.
- Recursos en plural, kebab-case, sin verbos; acciones no-CRUD como sub-recursos POST (`/orders/{id}/pay`).
- Autenticación: `Authorization: Bearer <accessToken>` en todo excepto `/auth/**` y catálogo público.
- Idempotencia: header **`Idempotency-Key: <uuid>`** obligatorio en POSTs marcados ⚡.
- Paginación: cursor (`?cursor=&limit=`) en listados grandes; respuesta con `meta.nextCursor`.
- Fechas ISO-8601 UTC (ADR-17; la app convierte a zona local en presentación); dinero como `{ "amount": "72.00", "currency": "USD" }` — el monto viaja como **string decimal** (nunca número de punto flotante JSON) y se mapea a `BigDecimal`/`Money` (ADR-05).
- JSON en camelCase (contrato compartido con los DTOs de Retrofit).

### Envelope de respuesta

```json
// Éxito                                  // Error
{ "data": { ... },                        { "error": {
  "meta": { "nextCursor": "..." } }           "code": "listing_not_available",
                                              "message": "El boleto ya fue reservado por otro usuario",
                                              "details": [ {"field": "…", "message": "…"} ] } }
```

Códigos HTTP: `200/201/204` éxito · `400/422` validación · `401` token · `403` autorización/QR no visible · `404` no existe (o no es tuyo, para no filtrar existencia) · `409` conflicto de estado/concurrencia/sold_out · `402` pago rechazado · `429` rate limit.

## 2. Catálogo de endpoints

### Auth (`/auth`)
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/auth/register` | Registro de asistente |
| POST | `/auth/login` | Login → access + refresh |
| POST | `/auth/refresh` | Rotación de refresh token |
| POST | `/auth/logout` | Revoca refresh token |

### Catálogo (público lectura)
| GET | `/events?q=&categoryId=&dateFrom=&dateTo=&status=&near=lat,lng&cursor=` | Búsqueda/filtrado |
| GET | `/events/{id}` | Detalle (+ zonas, ticketTypes, parkings, sponsors) |
| GET | `/categories` · `/events/{id}/parkings` | Soporte de catálogo |

### Órdenes y pagos (asistente)
| POST ⚡ | `/orders` | Crea orden `{items:[{type, refId, quantity}]}` → `201 PENDING` |
| POST ⚡ | `/orders/{id}/pay` | Ejecuta pago `{method}` → `200 PAID` / `402` |
| POST | `/orders/{id}/cancel` | Cancela orden PENDING |
| GET | `/orders` · `/orders/{id}` | Historial y detalle |

### Boletos (asistente)
| GET | `/tickets?status=` | Mis boletos (propietario actual) |
| GET | `/tickets/{id}` | Detalle + historial propio |
| GET | `/tickets/{id}/qr` | QR activo — `403 qr_not_yet_visible` fuera de ventana |
| GET | `/tickets/{id}/recovery-options` | **Cancelación inteligente**: la única acción válida |
| POST ⚡ | `/tickets/{id}/refund-requests` | Solicita reembolso |
| POST ⚡ | `/tickets/{id}/exchange-listings` | Publica en el Exchange (precio lo fija el servidor) |

### Official Ticket Exchange
| GET | `/events/{id}/exchange-listings` | Boletos publicados del evento |
| POST ⚡ | `/exchange-listings/{id}/reservations` | Reserva temporal → luego `/orders` con `EXCHANGE_TICKET` |
| DELETE | `/exchange-listings/{id}` | Vendedor cancela su publicación |

### Waitlist
| POST | `/events/{id}/waitlist` | Unirse (evento SOLD_OUT) |
| DELETE | `/events/{id}/waitlist` | Salir |
| GET | `/waitlist-offers` | Mis ofertas activas |
| POST ⚡ | `/waitlist-offers/{id}/accept` | Acepta → crea orden con prioridad |

### Parking
| GET | `/parkings/{id}` | Disponibilidad por tipo |
| POST ⚡ | `/parkings/{id}/check-ins` · `/check-outs` | Staff: entrada/salida por QR |

### Asistente — varios
| PUT | `/me` · GET `/me` | Perfil |
| PUT | `/me/favorites/{eventId}` · DELETE ídem · GET `/me/favorites` | Favoritos (idempotente por PUT) |
| GET | `/me/notifications` · POST `/me/notifications/{id}/read` | Notificaciones |
| POST | `/me/devices` | Registra token FCM |

### Organizador (`/organizer`)
| POST/PUT/DELETE | `/organizer/events` `/organizer/events/{id}` | CRUD de eventos (DRAFT) |
| POST | `/organizer/events/{id}/publish` | DRAFT → PUBLISHED |
| GET/PUT | `/organizer/events/{id}/policy` | Políticas del evento (`If-Match`/version) |
| POST/PUT/DELETE | `.../ticket-types` `.../zones` `.../parkings` | Inventario y zonas |
| GET | `/organizer/events/{id}/refund-requests` | Pendientes |
| POST | `/refund-requests/{id}/approve` · `/reject` | Resolución ⚡ |
| POST ⚡ | `/organizer/events/{id}/check-ins` | Escaneo de QR `{qrToken}` |
| POST | `/organizer/tickets/{id}/invalidate` · `/reissue` | Control del organizador ⚡ |
| POST/DELETE | `/organizer/events/{id}/staff` | Asignar staff |
| GET | `/organizer/events/{id}/stats` · `/organizer/dashboard` | Métricas e ingresos |

### Administrador (`/admin`)
| GET/POST/PUT/DELETE | `/admin/users` · `/admin/organizers` | Gestión de cuentas |
| CRUD | `/admin/categories` · `/admin/sponsors` | Catálogos |
| GET/PUT | `/admin/config` | Configuración global (comisión, tiempos, proveedores) |
| GET | `/admin/dashboard` · `/admin/reports?type=&from=&to=` | Reportes |

## 3. Contratos clave (ejemplos)

### `GET /tickets/{id}/recovery-options` → 200

```json
{ "data": {
    "ticketId": "9f2c…",
    "option": "EXCHANGE",                  // REFUND | EXCHANGE | NONE
    "refund": null,
    "exchange": {
      "originalPrice": { "amount": "80.00", "currency": "USD" },
      "depreciationPct": 10,
      "listPrice": { "amount": "72.00", "currency": "USD" },
      "listingDeadline": "2026-08-01T00:00:00Z"
    },
    "reason": "refund_window_expired"
} }
```

### `POST /orders` (compra en Exchange) → 201

```json
// Request  (Idempotency-Key: 550e8400-…)
{ "items": [ { "type": "EXCHANGE_TICKET", "refId": "<temporalReservationId>", "quantity": 1 } ] }

// Response
{ "data": {
    "id": "ord_…", "status": "PENDING",
    "total": { "amount": "72.00", "currency": "USD" },
    "expiresAt": "2026-07-08T21:45:00Z",
    "items": [ { "type": "EXCHANGE_TICKET", "description": "General — Concierto X", "unitPrice": { "amount": "72.00", "currency": "USD" } } ]
} }
```

### `GET /tickets/{id}/qr` → 200 (dentro de la ventana)

```json
{ "data": {
    "qrToken": "eyJhbGciOiJFUzI1NiIsImtpZCI6ImsxIn0…",   // JWS: solo qr_id + exp
    "expiresAt": "2026-08-15T02:00:00Z",
    "refreshAfter": "2026-08-15T01:00:00Z"                 // la app re-pide antes de expirar
} }
```

### `POST /organizer/events/{id}/check-ins` → 200 / 422

```json
// Request: { "qrToken": "eyJ…" }
// 200:  { "data": { "result": "GRANTED", "attendeeName": "Ana P.", "ticketType": "VIP", "zone": "A" } }
// 422:  { "error": { "code": "already_used", "message": "Boleto utilizado a las 19:42" } }
```

### Interfaz Retrofit espejo (referencia para el módulo Android)

```kotlin
interface TicketApi {
    @GET("tickets/{id}/recovery-options")
    suspend fun recoveryOptions(@Path("id") id: String): ApiResponse<RecoveryOptionsDto>

    @POST("tickets/{id}/exchange-listings")
    suspend fun publishToExchange(
        @Path("id") id: String,
        @Header("Idempotency-Key") key: String
    ): ApiResponse<ListingDto>
}
```

## 4. Reglas transversales del contrato

1. El servidor **nunca** acepta precios del cliente: los calcula y responde.
2. `404` para recursos ajenos (no `403`), evitando enumeración.
3. Endpoints de organizador validan propiedad del evento; de staff, asignación al evento.
4. Errores de concurrencia optimista → `409 version_conflict` con la versión vigente para que la UI recargue.
5. OpenAPI (`/v3/api-docs` + Swagger UI) es la fuente de verdad publicada del contrato; los DTOs de Android se mantienen espejo.
