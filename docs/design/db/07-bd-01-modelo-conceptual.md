# EventFlow BD — 1. Modelo Conceptual (DDD)

> Serie de diseño de base de datos: `01` conceptual · `02` ER · `03/04/05` diccionario de datos · `06` integridad, índices y estrategias · `07` normalización y revisión crítica.
> Sin SQL ni migraciones: modelo para revisión y aprobación.

## 1. Agregados

Un agregado define un **límite transaccional**: todo lo que debe cambiar de forma atómica y consistente vive dentro de él. Las referencias entre agregados son **por identidad (UUID)**, nunca composición.

| # | Agregado (raíz) | Miembros internos | Por qué este límite |
|---|---|---|---|
| A1 | **User** (`users`) | `user_roles`, `refresh_tokens`, `user_devices` | Credenciales, roles y sesiones cambian juntos y solo tienen sentido con el usuario. Un refresh token jamás se manipula sin validar a su dueño. |
| A2 | **Event** (`events`) | `event_policies` (1:1), `event_zones`, `sponsor_events` | Publicar un evento exige políticas y zonas consistentes en el mismo instante (misma transacción, misma `version`). `event_policies` es entidad separada (ADR-02) pero **dentro** del agregado: no existe sin su evento. |
| A3 | **TicketType** (`ticket_types`) | — | *Decisión DDD deliberada:* el inventario NO vive dentro del agregado Event. Vender un boleto bloquea solo la fila del tipo (límite transaccional mínimo); si el inventario fuera parte de Event, cada venta serializaría contra ediciones del organizador. |
| A4 | **Ticket** (`tickets`) | `dynamic_qrs`, `ticket_history` | La regla "todo cambio de propietario invalida el QR y registra historial" exige atomicidad ticket+QR+historial: mismo agregado. El Ticket ID es permanente (identidad del agregado). |
| A5 | **Order** (`orders`) | `order_items`, `payments` | Requisito: el pago siempre se asocia a la Orden, nunca al boleto. Confirmar pago transiciona orden e ítems juntos. |
| A6 | **ExchangeListing** (`exchange_listings`) | `temporal_reservations` | La reserva temporal solo existe respecto a una publicación; `PUBLISHED→RESERVED` y la creación de la reserva son un solo cambio atómico. |
| A7 | **TicketTransfer** (`ticket_transfers`) | — | Registro inmutable (hecho consumado) de una transferencia. Agregado propio *append-only*: nunca se edita, solo se consulta. |
| A8 | **WaitlistEntry** (`waitlist_entries`) | `waitlist_offers` | La oferta es el ciclo de vida de la entrada (WAITING→OFFERED→…); expirar una oferta y saltar la entrada es atómico. |
| A9 | **RefundRequest** (`refund_requests`) | — | Proceso con ciclo de vida propio (solicitud→resolución) que referencia Ticket y Order por identidad. |
| A10 | **Parking** (`parkings`) | `parking_slots` | La capacidad y las plazas son composición física del estacionamiento; bloquear una plaza no bloquea el evento. |
| A11 | **ParkingReservation** (`parking_reservations`) | — | Pertenece al ciclo de vida del **cliente** (se crea vía Orden, expira, se usa), no al del estacionamiento: agregado propio que referencia plaza y order_item. |
| A12 | **CheckIn** (`event_checkins`, `parking_checkins`) | — | Hechos inmutables de acceso; nunca se modifican (evidencia antifraude). |
| A13 | **LedgerEntry** (`ledger_entries`) | — | Asiento contable inmutable de partida doble (ADR-14). |
| A14 | Catálogos: **Category**, **Sponsor** | — | Agregados triviales de administración. |
| A15 | Plataforma: **GlobalConfig**, **IdempotencyKey**, **OutboxEvent**, **AuditLog**, **Notification**, **Favorite** | — | Soporte transversal; `Favorite` es una asociación pura User↔Event dentro del contexto del usuario. |

## 2. Entidades vs Value Objects vs Enumeraciones

### Value Objects (sin identidad; se persisten embebidos en columnas)

| VO | Composición | Se materializa como |
|---|---|---|
| **Money** | `amount` (decimal) + `currency` (ISO-4217) | par de columnas `*_amount NUMERIC(12,2)` + `currency CHAR(3)`; nunca FLOAT (ADR-05) |
| **GeoPoint** | latitud + longitud | `latitude NUMERIC(9,6)`, `longitude NUMERIC(9,6)` |
| **DateRange** | inicio + fin | `starts_at`, `ends_at` + `CHECK (ends_at > starts_at)` |
| **PolicySnapshot** | copia inmutable de EventPolicy al comprar | `tickets.policy_snapshot JSONB` (ADR-03) |
| **EmailAddress** | email normalizado | `CITEXT` (único case-insensitive) |
| **QRToken** | JWS firmado (`qr_id`,`kid`,`exp`) | no se persiste: se deriva de `dynamic_qrs` + llave de firma |
| **AccountRef** | tipo de cuenta + uuid (`BUYER:<id>`, `PLATFORM`…) | `TEXT` con formato validado (ledger) |
| **DeviceInfo / AuditContext** | dispositivo, IP, observaciones | `device_info JSONB`, `ip INET` |

### Enumeraciones (persistidas como `TEXT` + `CHECK`, ver §07-bd-06 la justificación frente a `ENUM` nativo)

`user_status`, `role_code`, `event_status`, `ticket_status`, `qr_status`, `qr_subject_type`, `order_status`, `order_item_type`, `payment_status`, `payment_provider`, `listing_status`, `temporal_reservation_status`, `refund_status`, `waitlist_status`, `waitlist_offer_status`, `parking_type`, `parking_slot_status`, `parking_reservation_status`, `checkin_result`, `checkin_direction`, `ledger_entry_type`, `outbox_status`, `notification_type`.

### Objetos de configuración

- **EventPolicy** (por evento, dentro del agregado Event): reglas de reembolso, exchange, waitlist, QR + `extra_policies JSONB` para políticas futuras sin migración (ADR-02).
- **GlobalConfig** (plataforma): clave→`JSONB` tipado (comisión del Exchange, tiempos por defecto, proveedores habilitados). Solo Admin escribe.

## 3. Eventos de Dominio relevantes para la persistencia

El catálogo completo vive en `docs/design/03-arquitectura.md` §2.4 (ADR-18). Impacto en BD:

- Todos se persisten en **`outbox_events`** dentro de la transacción del caso de uso (ADR-09).
- `audit_log` y las proyecciones de analytics se **derivan** de esos eventos (consumidores), no se escriben inline.
- `TicketReleased(cause)` es el evento integrador que encadena Reembolso/Expiración → Waitlist → Exchange (secuencia S5).

## 4. Relaciones y cardinalidad (entre agregados)

| Relación | Cardinalidad | Naturaleza |
|---|---|---|
| User organiza Event | 1 → N | referencia (`organizer_id`) |
| Category clasifica Event | 1 → N | referencia |
| Event ↔ Sponsor | N ↔ M | asociación `sponsor_events` |
| Event define TicketType | 1 → N | referencia (agregados separados, ver A3) |
| EventZone ubica TicketType | 1 → N (opcional) | referencia |
| TicketType instancia Ticket | 1 → N | referencia + **FK compuesta** que fija el mismo evento (ver 07-bd-03) |
| User es propietario actual de Ticket | 1 → N | referencia `current_owner_id` — **una sola columna = un solo propietario vigente, físico** |
| OrderItem origina Ticket | 1 → N (qty) | referencia `source_order_item_id` (trazabilidad de reembolso) |
| Order contiene OrderItem | 1 → N | composición |
| Order se paga con Payment | 1 → N intentos, **1 APPROVED** | composición + índice único parcial |
| Ticket publica ExchangeListing | 1 → N históricas, **1 activa** | referencia + índice único parcial |
| ExchangeListing reserva TemporalReservation | 1 → N históricas, **1 ACTIVE** | composición + índice único parcial |
| ExchangeListing concreta TicketTransfer | 1 → 0..1 | referencia |
| Event tiene WaitlistEntry | 1 → N (FIFO por `queue_seq`) | referencia |
| WaitlistEntry recibe WaitlistOffer | 1 → N históricas, **1 OFFERED** | composición + índice único parcial |
| Ticket solicita RefundRequest | 1 → N históricas, **1 REQUESTED** | referencia + índice único parcial |
| Event dispone Parking | 1 → N | referencia |
| Parking contiene ParkingSlot | 1 → N | composición |
| ParkingSlot se reserva en ParkingReservation | 1 → N históricas, **1 vigente** | referencia + índice único parcial |
| Ticket/ParkingReservation genera DynamicQR | 1 → N históricos, **1 vigente** | composición polimórfica controlada (CHECK uno-de) |
| Ticket registra TicketHistory / EventCheckin | 1 → N | *append-only*; **1 check-in GRANTED por ticket** (índice único parcial) |
| User ↔ Event favoritos | N ↔ M | asociación `favorites` |
| Order/Transfer/Refund origina LedgerEntry | 1 → N | referencia débil (`reference_type` + `reference_id`) |

## 5. Inconsistencias detectadas respecto al modelo inicial (doc 04) y resolución

| # | Inconsistencia | Resolución adoptada en esta serie |
|---|---|---|
| I1 | `order_items.ref_id` polimórfico **sin FK física** — la BD no podía garantizar que el ítem apunte a algo existente | Tres columnas FK tipadas (`ticket_type_id`, `parking_id`, `temporal_reservation_id`) + `CHECK` de exactamente-una según `item_type` |
| I2 | El QR de parking (`parking_reservations.qr_id`) no tenía tabla dueña consistente con "un solo QR activo" | `dynamic_qrs` se generaliza con `subject_type (TICKET\|PARKING)` y FKs excluyentes; índice único parcial por cada sujeto |
| I3 | `waitlist_entries.position` mutable exige renumerar al salir alguien → carrera y updates masivos | `queue_seq BIGINT` monótono e inmutable (identity); FIFO = `ORDER BY queue_seq`; nadie se renumera jamás |
| I4 | No existía vínculo físico Ticket → OrderItem de origen (necesario para trazar el pago en un reembolso) | `tickets.source_order_item_id FK` obligatorio |

Estas cuatro correcciones están incorporadas en el modelo lógico/físico de los documentos 03–05.
