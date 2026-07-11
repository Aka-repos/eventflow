# EventFlow API — 09. Contrato Android

> Cómo la app Android (estructura del doc `docs/design/03-arquitectura.md` §3) consume el contrato. Los DTOs Kotlin se derivan 1:1 de `05-openapi.yaml` y quedan **congelados** con él. Serialización: kotlinx.serialization con `ignoreUnknownKeys = true` y `coerceInputValues = true`; todo enum del contrato se modela con fallback `UNKNOWN` (doc 06 §5).

## 1. Infraestructura transversal (core/network)

| Pieza | Responsabilidad |
|---|---|
| `AuthInterceptor` | agrega `Authorization: Bearer` desde EncryptedDataStore |
| `TokenAuthenticator` (OkHttp) | ante `401 token_expired`: llama `/auth/refresh`, persiste rotación, reintenta 1 vez; `refresh_token_reused`/fallo ⇒ logout local + navegación a Login |
| `CorrelationInterceptor` | genera `X-Correlation-ID` por request; lo loguea junto al `traceId` de los Problems |
| `IdempotencyKeyStore` | genera y **persiste en Room** la clave por operación de negocio antes del primer intento; se reutiliza en reintentos y se libera al confirmar respuesta |
| `ProblemConverter` | parsea `application/problem+json` → `AppError` enrutando por `code` |
| `MoneyDto` | `{amount: String, currency: String}` → dominio `Money(BigDecimal)`; jamás Double |

`AppError` (dominio): `Network` · `Auth` · `Forbidden(code)` · `NotFound` · `Conflict(code, conflictVersion?)` · `Business(code, detail)` · `Validation(fields)` · `RateLimited(retryAfter)` · `Unknown(traceId?)`.

## 2. Contrato por módulo

Formato: **Retrofit** (interfaz, en `data/remote/api`) · **Repository** (interfaz en `domain/repository`, impl en `data/repository`) · **Cache/Offline** (Room como fuente de verdad de la UI) · **Errores esperados** (códigos que la UI trata explícitamente).

### auth → `AuthApi` / `AuthRepository`
- `register`, `login`, `refresh`, `logout` (DTOs: `RegisterRequest…AuthTokensResponse`).
- Cache: usuario en Room (`UserEntity`); tokens SOLO en EncryptedDataStore.
- Offline: sin red ⇒ sesión local válida permite entrar en modo lectura; login/registro requieren red.
- Errores UI: `invalid_credentials`, `email_already_registered`, `account_blocked`.

### catalog → `EventsApi` / `EventRepository`
- `listEvents(q, filtros, cursor)`, `getEvent(id)`, `listEventParkings`, `listCategories`.
- Cache: **stale-while-revalidate** — Flow desde Room (`EventEntity`, `CategoryEntity`), refresh en background; detalle visitado persiste (requisito "eventos consultados" offline).
- Sincronización: refresh al abrir Home y por `ConnectivityObserver` al recuperar red; cursor jamás se persiste entre sesiones.
- Errores UI: solo `Network` (fallback silencioso a cache con banner "sin conexión").

### orders → `OrdersApi` / `OrderRepository`
- `createOrder` ⚡, `payOrder` ⚡, `cancelOrder`, `listOrders`, `getOrder`.
- Cache: `OrderEntity` para historial offline (lectura).
- Offline: **crear/pagar exigen red** (regla de integridad; jamás se encolan); botón deshabilitado sin conexión.
- Idempotencia: una clave por checkout y otra por intento de pago (persistidas; sobreviven a kill de proceso).
- Errores UI: `event_sold_out` (→ ofrecer waitlist si `waitlistOpen`), `order_expired` (reiniciar checkout), `payment_failed` (reintentar/cambiar método), `listing_not_available`.

### tickets → `TicketsApi` / `TicketRepository`
- `listMyTickets`, `getTicket`, `getTicketQr`, `getRecoveryOptions`, `requestRefund` ⚡, `publishTicketToExchange` ⚡.
- Cache: `TicketEntity` (mis boletos completos offline). El **QR nunca se persiste**: se pide al mostrarse y se re-pide por `refreshAfter`; countdown local hasta `qrAvailableAt`.
- Sincronización: refresh obligatorio de boletos al recuperar red (el estado pudo cambiar: transferencia/reembolso mientras offline) — `SyncManager`.
- Errores UI: `qr_not_yet_visible` (mostrar countdown, no error), `refund_not_allowed_exchange_acquired` (la UI ni ofrece la opción: `recovery-options` manda), `ticket_blocked`.

### exchange → `ExchangeApi` / `ExchangeRepository`
- `listEventExchangeListings`, `reserveListing` ⚡, `cancelListing`, `listMyListings`.
- Cache: listado de mercado solo en memoria (dato altamente volátil — mostrar cache induciría a comprar listings muertos); mis publicaciones en Room.
- Flujo compra: `reserveListing` → countdown con `expiresAt` → `createOrder(EXCHANGE_TICKET)` → `payOrder`.
- Errores UI: `listing_not_available` (refrescar mercado), `reservation_expired` (volver a reservar), `cannot_buy_own_listing`.

### waitlist → `WaitlistApi` / `WaitlistRepository`
- `joinWaitlist`, `leaveWaitlist`, `listMyWaitlist`, `listMyWaitlistOffers`, `acceptOffer` ⚡, `declineOffer`.
- Cache: entradas y ofertas en Room; push `WAITLIST_OFFER` (FCM) dispara refresh inmediato + pantalla de oferta con countdown (`expiresAt`).
- Errores UI: `offer_expired` (mensaje "pasó al siguiente"), `already_in_waitlist`.

### me → `ProfileApi`, `NotificationsApi`, `FavoritesApi`, `ParkingApi`
- Perfil/notificaciones/reservas parking: Room + revalidación.
- **Favoritos: única escritura con cola offline** — `PUT/DELETE` idempotentes se encolan en WorkManager (`FavoriteSyncWork`) y se reconcilian al volver la red (server gana en conflicto). Todo lo demás es escritura online-only.
- `registerDevice` al login y al rotar token FCM.

### checkin (app organizador/staff) → `CheckInApi` / `CheckInRepository`
- `eventCheckIn` ⚡, `parkingCheckIn` ⚡, `parkingCheckOut` ⚡ (CameraX + ML Kit escanean; el servidor decide).
- **Sin modo offline**: sin red el escáner muestra estado bloqueante (prioridad seguridad #1). Respuesta <1 s esperada; timeout corto (5 s) con reintento manual (misma Idempotency-Key ⇒ sin doble check-in).
- Errores UI (semáforo): verde `GRANTED`; rojo con motivo: `already_used`, `qr_invalid`, `qr_expired`, `checkin_wrong_event`, `ticket_blocked`.

### organizer / admin → `OrganizerApi`, `AdminApi`
- CRUD y dashboards: online-first con cache de lectura (stats/series en Room con TTL 5 min).
- Mutaciones con `If-Match`: ante `Conflict(version_conflict)` la UI recarga el recurso (usa `conflictVersion`) y pide confirmar re-aplicación.

## 3. Matriz offline (resumen normativo)

| Operación | Sin conexión |
|---|---|
| Ver eventos consultados, mis boletos, órdenes, reservas, favoritos, notificaciones | ✔ desde Room |
| Buscar eventos nuevos / mercado exchange | ✖ (banner sin conexión) |
| Comprar, pagar, publicar, reservar, reembolsar, aceptar oferta, check-in | ✖ SIEMPRE (nunca se encola) |
| Marcar favorito / quitar favorito | ✔ encolado (WorkManager, server gana) |
| Ver QR | solo si ya visible y `qrToken` vigente en memoria; nunca desde disco |

## 4. Mappers (regla de tres capas)

`Dto → Entity → Domain` con funciones de extensión en `data/mapper` (patrón del skill de arquitectura): los DTOs viven solo en `data/remote`, las entities en `data/local`, y los ViewModels ven únicamente modelos de dominio. Enum desconocido se mapea a `UNKNOWN` en el **mapper Dto→Domain** (una sola frontera de tolerancia).

## Observaciones

| # | Problema | Impacto | Solución | ¿Dominio o contrato? |
|---|---|---|---|---|
| O11 | El requisito original dice "cuando vuelva Internet debe sincronizar" sin acotar escrituras | encolar compras/pagos offline violaría integridad (prioridades #1–2) | matriz §3: solo favoritos se encolan; el resto de la sincronización es de **lectura** (refresh de estado) | solo contrato (ratifica ADR-12) |
| O12 | Push FCM puede llegar antes que la sincronización (oferta de waitlist) | el usuario vería datos stale | todo push con `payload` dispara refresh dirigido del recurso afectado antes de navegar | solo contrato |
