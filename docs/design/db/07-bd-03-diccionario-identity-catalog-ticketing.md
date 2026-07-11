# EventFlow BD — 3. Diccionario de Datos (identity · catalog · ticketing)

> Convenciones globales: PK `id UUID` **v7** generado en la aplicación (orden temporal → mejor localidad de B-tree que v4; ver 07-bd-07). `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` en todas; `updated_at` en las mutables (trigger o app). `version INTEGER NOT NULL DEFAULT 0` = optimistic lock (`@Version`). Enums = `TEXT + CHECK`. FKs `ON UPDATE` no aplica (UUID inmutables); `ON DELETE RESTRICT` salvo indicación. Dinero = `NUMERIC(12,2) CHECK (>= 0)` + `currency CHAR(3) CHECK (~ '^[A-Z]{3}$')`.

## Schema `identity`

### `identity.users` — cuenta de la plataforma
| Columna | Tipo | Null | Default | Restricciones |
|---|---|---|---|---|
| id | UUID | NO | app (v7) | PK |
| email | CITEXT | NO | — | `UNIQUE WHERE deleted_at IS NULL` (parcial); formato validado en app |
| password_hash | TEXT | NO | — | BCrypt; nunca expuesto por API |
| full_name | TEXT | NO | — | `CHECK (length(full_name) BETWEEN 1 AND 200)` |
| phone | TEXT | SÍ | NULL | — |
| status | TEXT | NO | `'ACTIVE'` | `CHECK IN ('ACTIVE','BLOCKED','PENDING_VERIFICATION')` |
| created_at / updated_at | TIMESTAMPTZ | NO | now() | — |
| deleted_at | TIMESTAMPTZ | SÍ | NULL | Soft Delete (ADR-16) |
| version | INTEGER | NO | 0 | optimistic lock |

Índices: único parcial email · `(status)` solo si se filtra en admin (bajo valor, opcional).

### `identity.roles` — catálogo fijo de roles
| Columna | Tipo | Notas |
|---|---|---|
| id | **SMALLINT** PK | catálogo cerrado y semilla fija (1=ADMIN, 2=ORGANIZER, 3=STAFF, 4=ATTENDEE) — UUID sería desperdicio; nunca se expone crudo por API (se expone `code`) |
| code | TEXT | `UNIQUE`, `CHECK IN ('ADMIN','ORGANIZER','STAFF','ATTENDEE')` |

### `identity.user_roles` — asignación N:M
`user_id UUID FK→users ON DELETE CASCADE` · `role_id SMALLINT FK→roles RESTRICT` · `granted_at TIMESTAMPTZ` · `granted_by UUID FK→users SÍ NULL` · **PK compuesta `(user_id, role_id)`**. CASCADE justificado: la asignación no tiene sentido sin el usuario (y users usa soft delete, el cascade físico es excepcional).

### `identity.refresh_tokens` — sesiones con rotación
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK |
| user_id | UUID | NO | FK→users CASCADE |
| token_hash | TEXT | NO | `UNIQUE` (se guarda hash, jamás el token) |
| expires_at | TIMESTAMPTZ | NO | — |
| revoked_at | TIMESTAMPTZ | SÍ | — |
| replaced_by | UUID | SÍ | FK→refresh_tokens (cadena de rotación; detecta reuso robado) |
| device_info | JSONB | SÍ | — |

Índices: `(user_id) WHERE revoked_at IS NULL` (logout global). Limpieza periódica de expirados (job).

### `identity.user_devices` — tokens FCM
`id UUID PK` · `user_id FK→users CASCADE` · `fcm_token TEXT UNIQUE NO` · `platform TEXT CHECK IN ('ANDROID','IOS','WEB')` · `last_seen_at TIMESTAMPTZ`.

### `identity.staff_assignments` — staff de acceso por evento (ADR-13)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK |
| event_id | UUID | NO | FK→catalog.events RESTRICT |
| user_id | UUID | NO | FK→users RESTRICT |
| permissions | TEXT[] | NO | default `'{CHECKIN_EVENT}'`; valores validados en app |
| assigned_by | UUID | NO | FK→users |
| revoked_at | TIMESTAMPTZ | SÍ | — |

Único parcial: `UNIQUE (event_id, user_id) WHERE revoked_at IS NULL`. Índices: `(user_id) WHERE revoked_at IS NULL` (login del staff → sus eventos) · `(event_id)` (roster del evento, auditoría A6).

## Schema `catalog`

### `catalog.categories` — catálogo administrado
`id SMALLINT GENERATED ALWAYS AS IDENTITY PK` (catálogo pequeño, sin riesgo de enumeración: es público) · `name TEXT UNIQUE NO` · `icon TEXT SÍ` · `active BOOLEAN NO default true`.

### `catalog.events` — evento (agregado A2)
| Columna | Tipo | Null | Default | Restricciones |
|---|---|---|---|---|
| id | UUID | NO | app | PK |
| organizer_id | UUID | NO | — | FK→identity.users RESTRICT |
| category_id | SMALLINT | NO | — | FK→categories RESTRICT |
| title | TEXT | NO | — | `CHECK (length BETWEEN 3 AND 200)` |
| description | TEXT | NO | `''` | — |
| venue_name / address | TEXT | NO / SÍ | — | — |
| latitude / longitude | NUMERIC(9,6) | SÍ | NULL | `CHECK` rangos (−90..90 / −180..180); ambos o ninguno |
| timezone | TEXT | NO | `'America/Panama'` | identificador IANA de la zona del venue (auditoría A5); UTC almacena, esta zona presenta y ancla ventanas "N horas antes del evento" |
| starts_at / ends_at | TIMESTAMPTZ | NO | — | `CHECK (ends_at > starts_at)` |
| status | TEXT | NO | `'DRAFT'` | `CHECK IN ('DRAFT','PUBLISHED','SOLD_OUT','IN_PROGRESS','FINISHED','CANCELLED','SUSPENDED')` |
| cover_url | TEXT | SÍ | — | — |
| search_vector | TSVECTOR | NO | generado | `GENERATED ALWAYS AS (to_tsvector('spanish', title ‖ ' ' ‖ description)) STORED` |
| created/updated/deleted_at | TIMESTAMPTZ | —/—/SÍ | — | Soft Delete |
| version | INTEGER | NO | 0 | optimistic lock (ediciones concurrentes de organizadores) |

Índices: **GIN(search_vector)** (búsqueda) · compuesto `(status, starts_at) WHERE deleted_at IS NULL` (listado público paginado) · `(organizer_id)` (panel del organizador) · `(category_id, starts_at)` (filtro por categoría).

### `catalog.event_policies` — configuración por evento (1:1, ADR-02)
| Columna | Tipo | Null | Default | Restricciones |
|---|---|---|---|---|
| event_id | UUID | NO | — | **PK y FK→events RESTRICT** (1:1 real: PK=FK) |
| refund_window_ends_at | TIMESTAMPTZ | SÍ | NULL | NULL = sin reembolso |
| refund_pct | SMALLINT | NO | 100 | `CHECK (refund_pct BETWEEN 0 AND 100)` (política vigente lo fija en 100) |
| exchange_enabled | BOOLEAN | NO | false | — |
| exchange_depreciation_pct | SMALLINT | NO | 10 | `CHECK (BETWEEN 0 AND 100)` (rango comercial 5–20 se valida en app) |
| exchange_listing_deadline | TIMESTAMPTZ | SÍ | NULL | — |
| waitlist_enabled | BOOLEAN | NO | false | — |
| waitlist_offer_minutes | INTEGER | NO | 15 | `CHECK (> 0)` |
| temp_reservation_minutes | INTEGER | NO | 10 | `CHECK (> 0)` |
| qr_visibility_hours_before | INTEGER | NO | 24 | `CHECK (>= 0)` |
| qr_expiration_minutes | INTEGER | NO | 60 | `CHECK (> 0)` |
| cancellation_policy | TEXT | SÍ | — | texto legal |
| extra_policies | JSONB | NO | `'{}'` | políticas futuras sin migración (JSONB justificado: esquema abierto) |
| updated_at / version | — | — | — | optimistic lock |

### `catalog.event_zones`
`id UUID PK` · `event_id FK→events RESTRICT` · `name TEXT NO` · `capacity INTEGER CHECK (> 0)` · `UNIQUE (event_id, name)`.

### `catalog.sponsors` / `catalog.sponsor_events`
`sponsors`: `id UUID PK, name TEXT NO, logo_url TEXT, website TEXT`. `sponsor_events`: **PK (sponsor_id, event_id)**, ambos FK `ON DELETE CASCADE` (asociación pura).

## Schema `ticketing`

### `ticketing.ticket_types` — tarifa e inventario (agregado A3)
| Columna | Tipo | Null | Default | Restricciones |
|---|---|---|---|---|
| id | UUID | NO | app | PK |
| event_id | UUID | NO | — | FK→catalog.events RESTRICT · **`UNIQUE (id, event_id)`** (destino de la FK compuesta de tickets) |
| zone_id | UUID | SÍ | NULL | FK→event_zones RESTRICT |
| name | TEXT | NO | — | `UNIQUE (event_id, name)` |
| price / currency | NUMERIC(12,2) / CHAR(3) | NO | — | `CHECK (price >= 0)` |
| total_quantity | INTEGER | NO | — | `CHECK (>= 0)` |
| sold_quantity | INTEGER | NO | 0 | **`CHECK (sold_quantity BETWEEN 0 AND total_quantity)`** — imposible sobrevender físicamente |
| sales_starts_at / sales_ends_at | TIMESTAMPTZ | SÍ | — | `CHECK (ends > starts)` si ambos |
| created/updated_at, version | — | — | — | optimistic lock (fila caliente: además se usa `FOR UPDATE` al vender) |

Índice: `(event_id)`.

### `ticketing.tickets` — boleto con identidad permanente (agregado A4)
| Columna | Tipo | Null | Default | Restricciones |
|---|---|---|---|---|
| id | UUID | NO | app | PK — **permanente todo el ciclo de vida** |
| ticket_type_id | UUID | NO | — | ─┐ |
| event_id | UUID | NO | — | ─┴ **FK compuesta `(ticket_type_id, event_id) → ticket_types(id, event_id)`**: la desnormalización de `event_id` queda físicamente consistente |
| current_owner_id | UUID | NO | — | FK→identity.users RESTRICT — **una columna = un único propietario vigente** |
| source_order_item_id | UUID | NO | — | FK→commerce.order_items RESTRICT — ítem de la **emisión primaria** (traza histórica, I4) |
| acquisition_order_item_id | UUID | NO | — | FK→commerce.order_items RESTRICT — ítem con el que **el propietario actual** adquirió el boleto; se actualiza en cada transferencia (auditoría C2) |
| acquired_via | TEXT | NO | `'PRIMARY'` | `CHECK IN ('PRIMARY','EXCHANGE')` — soporte físico de ADR-19: solo `PRIMARY` puede solicitar reembolso |
| acquisition_price | NUMERIC(12,2) | NO | — | lo que pagó el propietario actual (= `original_price` en compra primaria; `exchange_price` tras transferencia); base del monto de reembolso (C2) |
| status | TEXT | NO | `'ACTIVE'` | `CHECK IN ('ACTIVE','PUBLISHED_IN_EXCHANGE','REFUND_PENDING','REFUNDED','USED','EXPIRED','CANCELLED','INVALIDATED')` — la exclusión reembolso/publicación es física: una sola columna de estado |
| original_price / currency | NUMERIC(12,2) / CHAR(3) | NO | — | precio pagado en la venta primaria (hecho histórico) |
| policy_snapshot | JSONB | NO | — | copia inmutable de event_policies al comprar (ADR-03) |
| purchased_at | TIMESTAMPTZ | NO | — | — |
| created/updated/deleted_at | — | — | — | Soft Delete |
| version | INTEGER | NO | 0 | optimistic lock |

Índices: `(current_owner_id, status) WHERE deleted_at IS NULL` (mis boletos) · `(event_id, status)` (inventario/estadísticas del organizador) · `(ticket_type_id)` · **`UNIQUE (id, event_id)`** (destino de la FK compuesta de `event_checkins`, auditoría M4).

### `ticketing.ticket_history` — transiciones (append-only)
| Columna | Tipo | Notas |
|---|---|---|
| id | **BIGINT IDENTITY** PK | interno, nunca expuesto por API, altísimo volumen: BIGINT secuencial es más compacto y ordenado que UUID |
| ticket_id | UUID FK→tickets RESTRICT | — |
| from_status / to_status | TEXT | mismo dominio que tickets.status (+`NONE` para emisión) |
| actor_id | UUID SÍ FK→users | NULL = sistema/scheduler |
| cause | TEXT NO | `CHECK IN ('ISSUED','PUBLISH','UNPUBLISH','TRANSFER','REFUND_REQUEST','REFUND_APPROVED','REFUND_REJECTED','CHECKIN','EXPIRE','INVALIDATE','REISSUE','EVENT_CANCELLED')` (auditoría B2) |
| metadata | JSONB SÍ | — |
| occurred_at | TIMESTAMPTZ NO default now() | — |

Índice `(ticket_id, occurred_at)`. `REVOKE UPDATE, DELETE`.

### `ticketing.dynamic_qrs` — QR dinámico (generalizado, I2)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK (es el `qr_id` dentro del JWS) |
| subject_type | TEXT | NO | `CHECK IN ('TICKET','PARKING')` |
| ticket_id | UUID | SÍ | FK→tickets RESTRICT |
| parking_reservation_id | UUID | SÍ | FK→parking.parking_reservations RESTRICT |
| status | TEXT | NO | `CHECK IN ('ACTIVE','BLOCKED','INVALIDATED','CONSUMED','EXPIRED')` |
| key_id | TEXT | NO | `kid` de la llave de firma (rotación) |
| issued_at / expires_at | TIMESTAMPTZ | NO | — |

Checks/índices críticos:
- `CHECK ((subject_type='TICKET' AND ticket_id IS NOT NULL AND parking_reservation_id IS NULL) OR (subject_type='PARKING' AND parking_reservation_id IS NOT NULL AND ticket_id IS NULL))`
- **`UNIQUE (ticket_id) WHERE status IN ('ACTIVE','BLOCKED')`** — jamás dos QR vigentes por boleto
- **`UNIQUE (parking_reservation_id) WHERE status IN ('ACTIVE','BLOCKED')`** — ídem parking
- Índice `(expires_at) WHERE status='ACTIVE'` (barrido de expiración del scheduler)
- Índice `(ticket_id)` completo — el parcial solo cubre vigentes; el historial de QRs de un boleto lo necesita (auditoría A6)
