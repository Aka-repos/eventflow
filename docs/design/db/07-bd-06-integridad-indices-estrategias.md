# EventFlow BD — 6. Restricciones, Índices y Estrategias

## 7. Catálogo de restricciones críticas (la BD impide físicamente la violación)

| Regla de negocio | Mecanismo físico |
|---|---|
| Un único QR vigente por boleto | `UNIQUE (ticket_id) WHERE status IN ('ACTIVE','BLOCKED')` en `dynamic_qrs` |
| Un único QR vigente por reserva de parking | ídem sobre `parking_reservation_id` |
| Un solo propietario activo por Ticket | `tickets.current_owner_id` es **una columna** NOT NULL — no existe forma de tener dos |
| Una publicación activa por Ticket | `UNIQUE (ticket_id) WHERE status IN ('WAITLIST_HOLD','PUBLISHED','RESERVED')` en `exchange_listings` (incluye la retención por waitlist, A1) |
| Un reembolso activo por Ticket | `UNIQUE (ticket_id) WHERE status='REQUESTED'` en `refund_requests` |
| Reembolso y publicación jamás simultáneos | `tickets.status` es una sola columna: no puede valer `REFUND_PENDING` y `PUBLISHED_IN_EXCHANGE` a la vez |
| Una reserva temporal activa por listing (⇒ por ticket) | `UNIQUE (listing_id) WHERE status='ACTIVE'` encadenado con el único de listings |
| Una inscripción de waitlist activa por usuario/evento | `UNIQUE (event_id, user_id) WHERE status IN ('WAITING','OFFERED')` |
| Un listing no se ofrece a dos personas de la fila | `UNIQUE (listing_id) WHERE status='OFFERED'` en `waitlist_offers` (fuente polimórfica INVENTORY/EXCHANGE, C1) |
| Una oferta viva por entrada de waitlist | `UNIQUE (entry_id) WHERE status='OFFERED'` |
| Un boleto entra al evento una sola vez | `UNIQUE (ticket_id) WHERE result='GRANTED'` en `event_checkins` |
| Una plaza sin dobles reservas vigentes | `UNIQUE (slot_id) WHERE status IN ('PENDING','CONFIRMED','IN_USE')` |
| Una orden no se cobra dos veces (ni tras reembolso) | `UNIQUE (order_id) WHERE status IN ('APPROVED','REFUNDED')` en `payments` (A4) |
| El reembolso devuelve lo que pagó el dueño actual | `refund_requests.amount` + `payment_id` congelados al solicitar desde `tickets.acquisition_price` (C2); `acquired_via='EXCHANGE'` bloquea la solicitud (ADR-19, capa de aplicación con test obligatorio) |
| checkin.event coincide con el evento del ticket | FK compuesta `event_checkins (ticket_id, event_id) → tickets (id, event_id)` (M4) |
| Sin sobreventa de inventario | `CHECK (sold_quantity BETWEEN 0 AND total_quantity)` en `ticket_types` |
| Sin sobreprecio en el Exchange | `CHECK (list_price <= original_price)` en listings y `CHECK (exchange_price <= original_price)` en transfers |
| El dinero de la transferencia cuadra | `CHECK (fee_amount + seller_amount = exchange_price)` |
| Nadie se transfiere a sí mismo | `CHECK (from_owner_id <> to_owner_id)` |
| Ledger sin asientos a la misma cuenta | `CHECK (source_account <> destination_account)` |
| Ítems polimórficos siempre apuntan a algo real | 3 FKs tipadas + CHECK exactamente-una (I1) |
| Un email por cuenta viva | `UNIQUE (email) WHERE deleted_at IS NULL` |
| Reuso de reserva del Exchange | `UNIQUE (temporal_reservation_id)` en `order_items` |
| Historia/ledger/auditoría inmutables | `REVOKE UPDATE, DELETE` sobre las 6 tablas append-only |

**Política ON DELETE**: `RESTRICT` por defecto (las entidades de negocio usan Soft Delete, nunca DELETE físico); `CASCADE` solo en composición/asociación pura sin valor histórico propio (`user_roles`, `refresh_tokens`, `user_devices`, `sponsor_events`, `favorites`, `notifications`). `order_items` pasó a RESTRICT (auditoría B1). `ON UPDATE` es irrelevante: las PK UUID jamás cambian.

## 8. Índices y rendimiento por consulta caliente

| Consulta frecuente | Índice que la sirve | Nota |
|---|---|---|
| Home: eventos publicados próximos | `events (status, starts_at) WHERE deleted_at IS NULL` | keyset sobre `(starts_at, id)` |
| Búsqueda de texto | `GIN (search_vector)` | tsvector generado STORED |
| Mis boletos | `tickets (current_owner_id, status) WHERE deleted_at IS NULL` | — |
| Panel del organizador | `events (organizer_id)` + `tickets (event_id, status)` | — |
| Mercado del Exchange por evento | `exchange_listings (status, expires_at)` + join a `tickets (event_id)` | — |
| Cabeza de la waitlist | `waitlist_entries (event_id, status, queue_seq)` | index scan directo al primer WAITING |
| Barridos del scheduler | parciales sobre `(expires_at)`/`(status, expires_at)` en qrs, orders, listings, reservations, offers, parking_reservations | índices diminutos: solo filas pendientes |
| Dispatcher del outbox | parcial `(created_at) WHERE status='PENDING'` | — |
| Historial de compras | `orders (buyer_id, created_at DESC)` | keyset `(created_at, id)` |
| Notificaciones no leídas | parcial `(user_id) WHERE read_at IS NULL` | badge O(1) |
| Métricas en vivo del evento | `event_checkins (event_id, occurred_at)` + `ledger_entries (event_id, occurred_at)` | dashboard lee ledger, no tablas calientes |

**Paginación**: keyset/cursor en todos los listados grandes (`WHERE (col, id) > (:cursor_col, :cursor_id) ORDER BY col, id LIMIT n+1`), codificada opaca en la API (doc 05). OFFSET solo en tablas de administración pequeñas.

**Cuellos de botella previstos y mitigación**:
1. `ticket_types` bajo `FOR UPDATE` en ventas masivas → transacción mínima (lock → decrementa → commit), sin llamadas externas dentro del lock (el pago ocurre después, sobre la orden).
2. Cabeza de waitlist en eventos populares → un solo lock por liberación, no por lector.
3. `audit_log`/`ledger_entries`/`notifications` crecen sin límite → BIGINT compacto, índices mínimos, partición por rango cuando supere ~10⁷ filas.
4. Contadores del dashboard → nunca `COUNT(*)` sobre transaccionales en caliente: proyecciones desde outbox o vistas materializadas refrescadas.

## 9. Estrategia de integridad (defensa en profundidad)

1. **Dominio** (Java): máquinas de estado rechazan transiciones ilegales (ADR-04).
2. **Transacción** (application layer): `FOR UPDATE` + validaciones cruzadas (p. ej. buyer ≠ seller).
3. **Base de datos** (esta capa): CHECKs, FKs, únicos parciales del §7 — si las capas 1–2 fallan por un bug, la BD rechaza el commit.
4. **Privilegios**: el usuario de conexión de Spring **no** tiene `UPDATE/DELETE` sobre tablas append-only ni `TRUNCATE` en ninguna.

Reglas cross-table que la BD **no** puede expresar declarativamente (se documentan como responsabilidad exclusiva de la capa 2, con test obligatorio): consistencia `orders.total_amount = Σ ítems`; `event_checkins.event_id` coincide con el del ticket; moneda homogénea dentro de una orden; buyer ≠ seller en reservas. Se evaluó usar triggers: se descarta en el MVP (lógica oculta, difícil de testear); queda anotado como opción de endurecimiento futuro.

## 10. Estrategia de concurrencia

| Tabla | Mecanismo | Por qué |
|---|---|---|
| `ticket_types` | **Pesimista** (`FOR UPDATE`) + `version` | contención real de compradores simultáneos; el CHECK de cantidad es la red final |
| `exchange_listings`, `temporal_reservations` | Pesimista + únicos parciales | dos compradores del mismo boleto: uno gana, el otro recibe 409 |
| `parking_slots` | Pesimista + único parcial | ídem plazas |
| `waitlist_entries` (cabeza FIFO) | Pesimista sobre la fila candidata | evita ofrecer el mismo boleto dos veces |
| `dynamic_qrs` (check-in) | Pesimista + único parcial GRANTED | dos escáneres validando el mismo QR |
| `events`, `event_policies`, `ticket_types`, `parkings`, `global_config` | **Optimista** (`version`) | ediciones humanas concurrentes: perder el lock debe ser visible (409), no silencioso |
| `tickets`, `orders`, `refund_requests` | Optimista + transiciones de estado con `WHERE status = :esperado` | update condicional: 0 filas afectadas = conflicto detectado |

**Orden canónico de adquisición de locks (auditoría A3, obligatorio en code review):** dentro de una transacción que bloquee varias filas relacionadas, adquirir siempre en este orden: (1) la raíz `ticketing.tickets`, (2) sus hijos (`dynamic_qrs`), (3) agregados vecinos (`exchange_listings`, `temporal_reservations`, `waitlist_*`, `ticket_types`, `orders`) en orden alfabético de tabla y PK ascendente. Ejemplo: el check-in resuelve el `qr_id` **sin lock**, luego `SELECT ticket FOR UPDATE`, luego `SELECT qr FOR UPDATE` — mismo orden que la transferencia ⇒ sin deadlocks. El job de expiración de órdenes bloquea `orders → ticket_types` (mismo orden que la compra).

**Outbox multi-instancia (auditoría M7):** el dispatcher consume con `SELECT … FOR UPDATE SKIP LOCKED LIMIT n` sobre el índice parcial de `PENDING` — varios workers sin doble entrega ni contención.

**Semántica de `sold_quantity` (auditoría M5):** cuenta unidades **comprometidas** (órdenes `PENDING` no expiradas + `PAID`). La liberación por expiración usa el orden de locks canónico. **Regla de retención waitlist (cierre de consistencia C1):** un cupo liberado por reembolso/cancelación NO decrementa `sold_quantity` mientras exista una oferta `OFFERED` sobre él — la oferta ES el compromiso; solo cuando la fila se agota (o está deshabilitada) el cupo se libera al público. Así la prioridad de la Waitlist es coherente con el inventario físico.

Nota Supabase: con el pooler en modo transacción (Supavisor/pgBouncer) **no usar advisory locks ni `SET` de sesión**; todos los mecanismos elegidos (row locks, versiones) son transaction-scoped y seguros. Recomendación (auditoría A8): pool dedicado de HikariCP contra la **conexión directa, puerto 5432** (o pooler en modo sesión); el modo transacción degrada los prepared statements de Hibernate.

## 11. Estrategia de auditoría

- `audit_log` se puebla **exclusivamente** desde el consumidor del outbox (ADR-09/18): todo evento de dominio genera registro; imposible "olvidar" auditar.
- Contexto de request (actor, IP, dispositivo) viaja en el payload del evento desde la capa API.
- Complementos por diseño: `ticket_history` (transiciones), `ticket_transfers` (propiedad), `ledger_entries` (dinero), `*_checkins` (accesos incl. **intentos denegados** para antifraude).
- Todas append-only con `REVOKE UPDATE, DELETE`; retención larga; partición futura por mes.

## 12. Estrategia de Soft Delete (qué sí y qué no)

| Con `deleted_at` | Justificación |
|---|---|
| `users` | referenciado por tickets/órdenes/ledger; borrar rompe trazabilidad. GDPR se resuelve **anonimizando** columnas de perfil, no borrando la fila |
| `events` | historial económico y boletos dependen de él |
| `tickets`, `orders` | contratos de compra; jamás desaparecen |
| `refund_requests` | expediente del proceso |

| Sin Soft Delete | Justificación |
|---|---|
| Append-only (history, transfers, ledger, checkins, audit) | inmutables: ni DELETE físico ni lógico |
| `payments` | registro financiero inmutable en la práctica; su "cancelación" es un estado, no un borrado (se omite `deleted_at` deliberadamente pese a la lista de ADR-16: un pago jamás se elimina, ni lógicamente) |
| `event_policies`, `dynamic_qrs`, listings, reservations, offers, waitlist, parking_* | su ciclo de vida ya se expresa con estados terminales (`CANCELLED`, `EXPIRED`, `INVALIDATED`); un `deleted_at` duplicaría semántica |
| Asociaciones (`favorites`, `user_roles`, `sponsor_events`) y `notifications`, `refresh_tokens`, `idempotency_keys` | datos operativos sin valor histórico contractual; DELETE físico + CASCADE |

Regla de implementación: repositorios filtran `deleted_at IS NULL` por defecto (en JPA: `@Where` o specification); los únicos son parciales para permitir re-registro de email.

## 13. Estrategia de versionado

- **Optimistic lock** (`version INTEGER`): tablas de la sección 10; mapeo directo a `@Version` de JPA.
- **Versionado de datos de negocio**: las políticas no se versionan en tabla aparte — cada boleto lleva su `policy_snapshot` (ADR-03), que es el versionado que el negocio necesita (condiciones al momento de compra).
- **Versionado de eventos de dominio**: `payload.eventVersion` en `outbox_events` para evolucionar consumidores sin migrar historial.
- **Versionado de esquema**: Flyway `V<n>__` (una migración por módulo del roadmap), aditivo y compatible: columnas nuevas nullable o con default; nunca renombrar en caliente (expand → migrate → contract).
