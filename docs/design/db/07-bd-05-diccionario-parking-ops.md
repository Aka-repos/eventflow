# EventFlow BD — 5. Diccionario de Datos (parking · ops)

## Schema `parking`

### `parking.parkings` — estacionamiento por evento (agregado A10)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK |
| event_id | UUID | NO | FK→catalog.events RESTRICT |
| name | TEXT | NO | `UNIQUE (event_id, name)` |
| type | TEXT | NO | `CHECK IN ('VIP','GENERAL','STAFF','MOTO','ACCESSIBLE')` |
| total_slots | INTEGER | NO | `CHECK (>= 0)` |
| price / currency | NUMERIC(12,2) / CHAR(3) | NO | `CHECK (price >= 0)` |
| opens_at / closes_at | TIMESTAMPTZ | NO | `CHECK (closes_at > opens_at)` |
| created/updated_at, version | — | — | optimistic lock |

Nota: la disponibilidad **no** se cachea como contador; se deriva de `parking_slots.status` (cardinalidad pequeña por parking, un `COUNT` indexado basta — a diferencia de ticket_types, aquí las plazas son filas físicas).

### `parking.parking_slots` — plaza física
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK |
| parking_id | UUID | NO | FK→parkings RESTRICT |
| code | TEXT | NO | `UNIQUE (parking_id, code)` |
| status | TEXT | NO | `CHECK IN ('AVAILABLE','RESERVED','OCCUPIED','OUT_OF_SERVICE','BLOCKED')` default `'AVAILABLE'` |
| updated_at, version | — | — | optimistic lock + `FOR UPDATE` al reservar |

Índice `(parking_id, status)` (contar disponibilidad y elegir plaza libre).

### `parking.parking_reservations` — reserva del cliente (agregado A11)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK |
| slot_id | UUID | NO | FK→parking_slots RESTRICT |
| order_item_id | UUID | NO | FK→commerce.order_items RESTRICT |
| user_id | UUID | NO | FK→identity.users RESTRICT |
| status | TEXT | NO | `CHECK IN ('PENDING','CONFIRMED','IN_USE','COMPLETED','CANCELLED','EXPIRED')` |
| expires_at | TIMESTAMPTZ | SÍ | `CHECK (status <> 'PENDING' OR expires_at IS NOT NULL)` (auditoría B4) |
| created/updated_at, version | — | — | optimistic lock |

**`UNIQUE (slot_id) WHERE status IN ('PENDING','CONFIRMED','IN_USE')`** — una plaza no admite dos reservas vigentes. Índices: `(user_id, status)` · `(status, expires_at) WHERE status IN ('PENDING','CONFIRMED')`.

## Schema `ops`

### `ops.event_checkins` — accesos al evento (append-only, agregado A12)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | **BIGINT IDENTITY** | NO | PK (interno, alto volumen) |
| ticket_id | UUID | NO | FK→ticketing.tickets RESTRICT |
| qr_id | UUID | NO | FK→ticketing.dynamic_qrs RESTRICT |
| event_id | UUID | NO | **FK compuesta `(ticket_id, event_id) → ticketing.tickets (id, event_id)`** — la desnormalización queda físicamente consistente (auditoría M4), mismo patrón que tickets↔ticket_types |
| scanned_by | UUID | NO | FK→users RESTRICT (organizador o staff) |
| result | TEXT | NO | `CHECK IN ('GRANTED','DENIED')` |
| denial_reason | TEXT | SÍ | `CHECK (result='GRANTED' OR denial_reason IS NOT NULL)` |
| device_info | JSONB | SÍ | — |
| ip | INET | SÍ | tipo nativo (valida formato, permite análisis por rango) |
| occurred_at | TIMESTAMPTZ | NO | default now() |

**`UNIQUE (ticket_id) WHERE result='GRANTED'`** — un boleto entra una sola vez, físicamente. Índices: `(event_id, occurred_at)` (métricas en vivo) · `(ticket_id)`. `REVOKE UPDATE, DELETE`.

### `ops.parking_checkins` — entradas/salidas de parking (append-only)
`id BIGINT IDENTITY PK` · `reservation_id FK→parking_reservations RESTRICT` · `direction TEXT CHECK IN ('IN','OUT')` · `result TEXT CHECK IN ('GRANTED','DENIED')` · `scanned_by FK→users` · `device_info JSONB` · `ip INET` · `occurred_at TIMESTAMPTZ`. Índice `(reservation_id, occurred_at)`. `REVOKE UPDATE, DELETE`.

### `ops.favorites` — asociación pura
**PK (user_id, event_id)**, ambos FK **CASCADE** · `created_at`. Índice implícito del PK cubre "mis favoritos"; índice `(event_id)` para métricas de interés.

### `ops.notifications`
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK (se expone por API) |
| user_id | UUID | NO | FK→users **CASCADE** |
| type | TEXT | NO | `CHECK IN ('PURCHASE_SUCCESS','EVENT_REMINDER','PARKING_RESERVED','SCHEDULE_CHANGE','CANCELLATION','WAITLIST_OFFER','EXCHANGE_SOLD','REFUND_RESOLVED','GENERIC')` |
| title / body | TEXT | NO | — |
| payload | JSONB | SÍ | deep-link data |
| read_at | TIMESTAMPTZ | SÍ | — |
| created_at | TIMESTAMPTZ | NO | — |

Índices: `(user_id, created_at DESC)` · parcial `(user_id) WHERE read_at IS NULL` (badge de no leídas).

### `ops.global_config` — configuración de plataforma
`key TEXT PK` (p. ej. `exchange.fee_pct`, `payments.providers`) · `value JSONB NO` · `description TEXT` · `updated_by FK→users` · `updated_at` · `version INTEGER` (optimistic lock: dos admins editando). JSONB justificado: valores heterogéneos por clave, esquema abierto, sin queries por dentro del valor.

### `ops.idempotency_keys` — deduplicación de operaciones críticas (ADR-07)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| user_id | UUID | NO | ─┐ **PK compuesta (user_id, idem_key)** — el scope por usuario impide que un tercero "queme" claves ajenas |
| idem_key | UUID | NO | ─┘ |
| endpoint | TEXT | NO | — |
| request_hash | TEXT | NO | detecta reuso de clave con payload distinto → `422` |
| response_status | SMALLINT | SÍ | — |
| response_body | JSONB | SÍ | respuesta persistida que se re-entrega tal cual |
| created_at / expires_at | TIMESTAMPTZ | NO | TTL (48 h) + job de limpieza |

Índice `(expires_at)` para el barrido.

### `ops.outbox_events` — eventos de dominio pendientes (ADR-09)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | **BIGINT IDENTITY** | NO | PK — el orden de inserción ≈ orden de publicación |
| aggregate_type / aggregate_id | TEXT / UUID | NO | — |
| event_type | TEXT | NO | catálogo ADR-18 |
| payload | JSONB | NO | versionado (`eventVersion`) |
| status | TEXT | NO | `CHECK IN ('PENDING','PROCESSED','FAILED')` |
| attempts | SMALLINT | NO | default 0 |
| last_error | TEXT | SÍ | — |
| created_at / processed_at | TIMESTAMPTZ | NO/SÍ | — |

Índice **parcial `(created_at) WHERE status='PENDING'`** — el polling del dispatcher escanea solo lo pendiente (el índice se mantiene diminuto aunque la tabla crezca).

### `ops.audit_log` — auditoría (append-only, poblada por consumidor del outbox)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | **BIGINT IDENTITY** | NO | PK |
| actor_id | UUID | SÍ | NULL = sistema; **sin FK a users** (deliberado: la auditoría debe sobrevivir a cualquier operación sobre users y no bloquearla) |
| action | TEXT | NO | — |
| entity_type / entity_id | TEXT / TEXT | NO | referencia débil (sobrevive refactors) |
| ip | INET | SÍ | — |
| device | TEXT | SÍ | — |
| details | JSONB | SÍ | — |
| occurred_at | TIMESTAMPTZ | NO | default now() |

Índices: `(entity_type, entity_id, occurred_at)` · `(actor_id, occurred_at)`. `REVOKE UPDATE, DELETE`. **Preparada para particionar por rango mensual** cuando el volumen lo exija (PK incluiría `occurred_at`); no se particiona en el MVP.
