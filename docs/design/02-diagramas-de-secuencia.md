# EventFlow — Diagramas de Secuencia (flujos críticos)

> Diagramas en Mermaid (renderizan en VSCode/GitHub). Convenciones:
> - `App` = app Android (Compose + ViewModel + UseCase); `API` = Spring Boot; `BD` = PostgreSQL (Supabase).
> - Toda operación crítica lleva header `Idempotency-Key` (ADR-07).
> - `FOR UPDATE` indica bloqueo pesimista de fila (ADR-06). `Outbox` = evento de dominio persistido en la misma transacción (ADR-09).

## S1. Autenticación (login + refresh transparente)

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant Interceptor as AuthInterceptor (OkHttp)
    participant API
    participant BD

    App->>API: POST /auth/login {email, password}
    API->>BD: valida credenciales (BCrypt) + rol
    API-->>App: 200 {accessToken (15m), refreshToken (14d), user}
    App->>App: guarda tokens cifrados (EncryptedDataStore) + usuario en Room

    Note over App,API: ... más tarde, el access token expira ...
    App->>Interceptor: request a recurso protegido
    Interceptor->>API: GET /events (Bearer expirado)
    API-->>Interceptor: 401 token_expired
    Interceptor->>API: POST /auth/refresh {refreshToken}
    API->>BD: valida refresh token (rotación: invalida el anterior)
    API-->>Interceptor: 200 {nuevo accessToken + refreshToken}
    Interceptor->>API: reintenta GET /events
    API-->>App: 200 datos
    Note over Interceptor: si el refresh falla → logout local + navegación a Login
```

## S2. Compra primaria de boletos (+ parking) mediante Orden

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant API
    participant BD
    participant Pay as PaymentProvider
    participant Outbox

    App->>API: POST /orders {items:[{TICKET_TYPE,qty},{PARKING,qty}]} (Idempotency-Key)
    API->>BD: BEGIN TX
    API->>BD: SELECT ticket_type FOR UPDATE (inventario)
    API->>BD: SELECT parking FOR UPDATE (disponibilidad)
    alt sin inventario suficiente
        API-->>App: 409 sold_out (+ ¿waitlist habilitada? → oferta de unirse)
    else inventario OK
        API->>BD: decrementa cupo, crea Order(PENDING)+items, reserva plazas (RESERVED, expires_at)
        API->>BD: COMMIT
        API-->>App: 201 {orderId, total, expiresAt}
    end

    App->>API: POST /orders/{id}/pay {paymentMethod} (Idempotency-Key)
    API->>Pay: authorize(orderId, total)
    alt pago aprobado
        Pay-->>API: approved(paymentRef)
        API->>BD: BEGIN TX: Order→PAID, Payment(APPROVED)
        API->>BD: crea Tickets(ACTIVE, policy_snapshot) + QRs(PENDING_VISIBILITY)
        API->>BD: ReservaParking→CONFIRMED
        API->>BD: ledger: asiento de venta · auditoría
        API->>Outbox: OrderPaid → notificación "compra exitosa"
        API->>BD: COMMIT
        API-->>App: 200 {order PAID, tickets[]}
    else pago rechazado / expira
        Pay-->>API: declined
        API->>BD: TX: Order→FAILED, libera inventario y plazas (AVAILABLE)
        API-->>App: 402 payment_failed {reason}
    end
```

## S3. Cancelación inteligente → Reembolso (período activo)

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant API
    participant BD
    participant Org as Organizador
    participant Pay as PaymentProvider
    participant Outbox

    App->>API: GET /tickets/{id}/recovery-options
    API->>BD: lee ticket + policy_snapshot + estado
    API-->>App: 200 {option: REFUND, amount: 100%, deadline}
    Note over App: la UI solo muestra la opción válida

    App->>API: POST /tickets/{id}/refund-requests (Idempotency-Key)
    API->>BD: TX: valida propietario + estado ACTIVE +<br/>sin listing activo (índice único parcial)
    API->>BD: Refund(REQUESTED), Ticket→REFUND_PENDING, QR bloqueado
    API-->>App: 201 refund REQUESTED

    Org->>API: POST /refund-requests/{id}/approve
    API->>Pay: refund(paymentRef, 100%)
    Pay-->>API: refunded
    API->>BD: TX: Refund→APPROVED, Ticket→REFUNDED, QR→INVALIDATED
    API->>BD: boleto vuelve al inventario (cupo +1) · ledger (comisión = 0) · auditoría
    API->>Outbox: TicketReleased(cause=REFUND) → dispara flujo Waitlist (S5)
    API->>Outbox: notificación al asistente
    Note over BD: si el organizador rechaza → Refund→REJECTED,<br/>Ticket→ACTIVE, QR se desbloquea
```

## S4. Cancelación inteligente → Publicación en el Exchange (período expirado)

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant API
    participant BD

    App->>API: GET /tickets/{id}/recovery-options
    API->>BD: policy_snapshot: reembolso expirado, exchange habilitado
    API-->>App: 200 {option: EXCHANGE, originalPrice: "80.00",<br/>depreciation: 10%, listPrice: "72.00"}
    Note over App: el precio lo calcula el servidor;<br/>el usuario solo confirma

    App->>API: POST /tickets/{id}/exchange-listings (Idempotency-Key)
    API->>BD: TX: valida propietario, estado ACTIVE, sin refund activo,<br/>exchange habilitado, fecha límite de publicación
    API->>BD: Listing(PUBLISHED, price="72.00" USD, expires_at),<br/>Ticket→PUBLISHED_IN_EXCHANGE, QR→BLOCKED
    API->>BD: auditoría
    API-->>App: 201 listing PUBLISHED
    Note over BD: el propietario NO cambia mientras esté publicado.<br/>Puede cancelar: Listing→CANCELLED, Ticket→ACTIVE, QR se desbloquea.
```

## S5. Liberación de boleto → Waitlist (FIFO) → Exchange

```mermaid
sequenceDiagram
    autonumber
    participant Outbox as Outbox/Scheduler
    participant API as WaitlistService
    participant BD
    participant U1 as Usuario #1 (FIFO)
    participant U2 as Usuario #2

    Outbox->>API: TicketReleased(ticketId, cause)
    API->>BD: TX: SELECT waitlist head FOR UPDATE (por evento, orden FIFO)
    alt waitlist habilitada y con usuarios
        API->>BD: WaitlistOffer(OFFERED, expires_at = now + ventana configurable)
        API->>U1: notificación "boleto disponible, tienes 15 min"
        alt U1 compra a tiempo
            U1->>API: POST /waitlist-offers/{id}/accept → crea Orden (flujo S2)
            API->>BD: WaitlistEntry→FULFILLED
        else oferta expira (scheduler)
            Outbox->>API: expira oferta (expires_at vencido)
            API->>BD: Offer→EXPIRED, WaitlistEntry→SKIPPED
            API->>U2: ofrece al siguiente (repite FIFO)
        end
    else waitlist vacía / deshabilitada / nadie aceptó
        API->>BD: el boleto queda disponible para venta directa<br/>o permanece elegible para Exchange
    end
    Note over API,BD: todo ofrecimiento, aceptación y expiración queda auditado
```

## S6. Compra en el Official Ticket Exchange (reserva temporal + transferencia)

```mermaid
sequenceDiagram
    autonumber
    participant Comprador as App (comprador)
    participant API
    participant BD
    participant Pay as PaymentProvider
    participant Vendedor as App (vendedor)

    Comprador->>API: POST /exchange-listings/{id}/reservations (Idempotency-Key)
    API->>BD: TX: SELECT listing FOR UPDATE
    alt listing PUBLISHED
        API->>BD: Listing→RESERVED, TemporalReservation(expires_at = now + t_config)
        API-->>Comprador: 201 {reservationId, price, expiresAt}
    else ya reservado/vendido
        API-->>Comprador: 409 listing_not_available
    end

    Comprador->>API: POST /orders {items:[{EXCHANGE_TICKET, reservationId}]} + pay
    API->>Pay: authorize(precio exchange)
    alt pago confirmado
        Pay-->>API: approved
        API->>BD: BEGIN TX (transferencia atómica)
        API->>BD: Ticket: owner → comprador (Ticket ID NO cambia)
        API->>BD: QR anterior → INVALIDATED · genera QR nuevo (único activo)
        API->>BD: TicketTransfer {precio original, valor exchange, depreciación,<br/>comisión, monto al vendedor, ambos propietarios, fecha/hora}
        API->>BD: ledger: comisión EventFlow + pago al vendedor
        API->>BD: Listing→SOLD · WaitlistEntry/Reservation cerradas · auditoría
        API->>BD: COMMIT
        API-->>Comprador: 200 boleto transferido (QR visible según ventana)
        API-->>Vendedor: notificación "boleto vendido, recibes $X"
    else pago falla o reserva expira
        API->>BD: TX: Reservation→EXPIRED/FAILED, Listing→PUBLISHED
        API-->>Comprador: 402 payment_failed
        Note over BD: el propietario nunca cambió; el QR sigue bloqueado<br/>mientras el listing esté publicado
    end
```

## S7. Check-in al evento (QR dinámico, validación server-side)

```mermaid
sequenceDiagram
    autonumber
    participant Staff as App Staff (escáner)
    participant API
    participant BD

    Note over Staff: el asistente muestra su QR<br/>(visible solo dentro de la ventana configurada)
    Staff->>API: POST /events/{id}/check-ins {qrToken} (Idempotency-Key)
    API->>API: verifica firma JWS (kid) + exp del token
    API->>BD: TX: SELECT qr JOIN ticket FOR UPDATE
    alt QR ACTIVO + ticket ACTIVE + propietario vigente + evento correcto + staff autorizado
        API->>BD: Ticket→USED, QR→CONSUMED, CheckIn registrado (+auditoría con dispositivo/IP)
        API-->>Staff: 200 ✅ {nombre, tipo de boleto, zona}
    else QR inválido / ya usado / bloqueado / de otro evento
        API->>BD: registra intento fallido (auditoría antifraude)
        API-->>Staff: 422 ❌ {código: qr_invalid | already_used | ticket_blocked}
    end
    Note over API: la decisión SIEMPRE es del servidor;<br/>el escáner solo muestra el resultado
```

## S8. Parking: reserva, entrada y salida

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant API
    participant BD
    participant Staff as Escáner parking

    App->>API: POST /orders {items:[{PARKING, parkingId, type: VIP}]}
    API->>BD: TX: SELECT plazas disponibles FOR UPDATE
    API->>BD: Plaza AVAILABLE→RESERVED (con la orden, flujo S2)
    API-->>App: reserva confirmada + QR de parking

    Note over Staff: llegada al evento
    Staff->>API: POST /parkings/{id}/check-ins {qrToken}
    API->>BD: TX: valida QR + reserva → Plaza RESERVED→OCCUPIED
    API-->>Staff: 200 ✅ plaza asignada

    Note over Staff: salida
    Staff->>API: POST /parkings/{id}/check-outs {qrToken}
    API->>BD: TX: Plaza OCCUPIED→AVAILABLE · registra salida (auditoría)
    API-->>Staff: 200 ✅
    Note over BD: OUT_OF_SERVICE/BLOCKED solo por acción del organizador;<br/>una reserva no usada expira → Plaza vuelve a AVAILABLE (scheduler)
```

## Invariantes que estos flujos garantizan

1. **Nunca hay dos QR activos para un boleto** (índice único parcial + transacción de transferencia).
2. **Nunca hay reembolso y publicación simultáneos** (validación cruzada en TX + índices únicos parciales).
3. **El propietario solo cambia con pago confirmado** (S2/S6: la transferencia vive dentro de la transacción post-confirmación).
4. **La Waitlist siempre tiene prioridad sobre el Exchange** al liberarse un boleto (S3→S5).
5. **Dos compradores no pueden ganar el mismo boleto/plaza** (`FOR UPDATE` en la fila caliente).
6. **Reintentos de red no duplican efectos** (`Idempotency-Key` en compras, pagos, reembolsos, check-ins).
