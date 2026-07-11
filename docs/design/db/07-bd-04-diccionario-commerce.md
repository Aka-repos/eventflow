# EventFlow BD — 4. Diccionario de Datos (commerce)

## Schema `commerce`

### `commerce.orders` — orden de compra (agregado A5)
| Columna | Tipo | Null | Default | Restricciones |
|---|---|---|---|---|
| id | UUID | NO | app | PK |
| buyer_id | UUID | NO | — | FK→identity.users RESTRICT |
| status | TEXT | NO | `'PENDING'` | `CHECK IN ('PENDING','PAID','FAILED','CANCELLED','REFUNDED')` |
| total_amount / currency | NUMERIC(12,2) / CHAR(3) | NO | — | `CHECK (total_amount >= 0)`; consistencia con Σ ítems validada en app (misma TX) |
| expires_at | TIMESTAMPTZ | NO | — | ventana para pagar una orden PENDING |
| idempotency_key | UUID | SÍ | — | `UNIQUE WHERE idempotency_key IS NOT NULL` — segunda defensa además de `ops.idempotency_keys` |
| created/updated/deleted_at | TIMESTAMPTZ | — | — | Soft Delete |
| version | INTEGER | NO | 0 | optimistic lock |

Índices: `(buyer_id, created_at DESC)` (historial paginado) · `(status, expires_at) WHERE status='PENDING'` (barrido de expiración).

### `commerce.order_items` — ítems polimórficos con FK físicas (I1)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK |
| order_id | UUID | NO | FK→orders **RESTRICT** (auditoría B1: uniformidad con la filosofía RESTRICT-por-defecto; las órdenes usan Soft Delete y jamás se borran físicamente) |
| item_type | TEXT | NO | `CHECK IN ('TICKET','PARKING','EXCHANGE_TICKET')` |
| ticket_type_id | UUID | SÍ | FK→ticketing.ticket_types RESTRICT |
| parking_id | UUID | SÍ | FK→parking.parkings RESTRICT |
| temporal_reservation_id | UUID | SÍ | FK→temporal_reservations RESTRICT · `UNIQUE` (una reserva del Exchange se compra a lo sumo una vez) |
| quantity | INTEGER | NO | `CHECK (quantity > 0)` · `CHECK (item_type <> 'EXCHANGE_TICKET' OR quantity = 1)` |
| unit_price / currency | NUMERIC(12,2) / CHAR(3) | NO | `CHECK (unit_price >= 0)` |

**CHECK de polimorfismo cerrado** (exactamente una referencia y acorde al tipo):
`(item_type='TICKET' AND ticket_type_id IS NOT NULL AND parking_id IS NULL AND temporal_reservation_id IS NULL) OR (item_type='PARKING' AND parking_id IS NOT NULL AND …) OR (item_type='EXCHANGE_TICKET' AND temporal_reservation_id IS NOT NULL AND …)`

Índices: `(order_id)` · `(ticket_type_id)` y `(parking_id)` (FKs de join, auditoría A6).

### `commerce.payments` — intentos de pago
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK |
| order_id | UUID | NO | FK→orders RESTRICT |
| provider | TEXT | NO | `CHECK IN ('FAKE','STRIPE','PAYPAL','YAPPY','CARD','TRANSFER')` |
| provider_ref | TEXT | SÍ | `UNIQUE (provider, provider_ref) WHERE provider_ref IS NOT NULL` (anti-doble-registro de webhook) |
| status | TEXT | NO | `CHECK IN ('PENDING','APPROVED','DECLINED','REFUNDED')` |
| amount / currency | NUMERIC(12,2) / CHAR(3) | NO | — |
| failure_reason | TEXT | SÍ | — |
| created_at / updated_at | TIMESTAMPTZ | NO | — |

**`UNIQUE (order_id) WHERE status IN ('APPROVED','REFUNDED')`** — una orden jamás se cobra dos veces, incluso después de reembolsada (auditoría A4). Índice `(order_id)`. Regla de flujo (auditoría A2): la fila `PENDING` con `provider_ref` se crea **antes** de invocar al proveedor (patrón payment-intent) y un job de reconciliación idempotente resuelve los `PENDING` huérfanos.

### `commerce.ledger_entries` — libro mayor de partida doble (append-only, ADR-14)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | **BIGINT IDENTITY** | NO | PK — interno, orden natural de inserción útil para conciliación; nunca expuesto por API |
| entry_type | TEXT | NO | `CHECK IN ('SALE','PARKING_SALE','EXCHANGE_SALE','PLATFORM_FEE','SELLER_PAYOUT','REFUND')` |
| source_account / destination_account | TEXT | NO | formato `PLATFORM` \| `BUYER:<uuid>` \| `SELLER:<uuid>` \| `ORGANIZER:<uuid>` · `CHECK (source_account <> destination_account)` |
| amount / currency | NUMERIC(12,2) / CHAR(3) | NO | `CHECK (amount > 0)` |
| fee_amount | NUMERIC(12,2) | SÍ | `CHECK (fee_amount >= 0)` |
| reference_type / reference_id | TEXT / UUID | NO | `ORDER` \| `TRANSFER` \| `REFUND` + id (referencia débil deliberada: el ledger sobrevive a cualquier refactor de tablas operativas) |
| event_id | UUID | SÍ | FK→catalog.events RESTRICT (dimensión de reporting) |
| occurred_at | TIMESTAMPTZ | NO | default now() |
| details | JSONB | SÍ | desglose (depreciación aplicada, etc.) |

Índices: `(event_id, occurred_at)` · `(entry_type, occurred_at)` · `(reference_type, reference_id)`. `REVOKE UPDATE, DELETE` (inmutabilidad física).

### `commerce.exchange_listings` — publicación en el Exchange (agregado A6)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK |
| ticket_id | UUID | NO | FK→ticketing.tickets RESTRICT |
| seller_id | UUID | NO | FK→users RESTRICT (propietario al publicar) |
| original_price | NUMERIC(12,2) | NO | copiado del ticket (hecho histórico del cálculo) |
| list_price | NUMERIC(12,2) | NO | **`CHECK (list_price <= original_price)`** — el sobreprecio es físicamente imposible (regla antifraude en BD) |
| depreciation_pct | SMALLINT | NO | `CHECK (BETWEEN 0 AND 100)` — % aplicado al calcular |
| currency | CHAR(3) | NO | — |
| status | TEXT | NO | `CHECK IN ('WAITLIST_HOLD','PUBLISHED','RESERVED','SOLD','CANCELLED','EXPIRED')` — `WAITLIST_HOLD` (auditoría A1): listing creado con precio fijado pero retenido para la fila FIFO; invisible en el marketplace |
| published_at / expires_at | TIMESTAMPTZ | NO | — |
| created/updated_at, version | — | — | optimistic lock |

**`UNIQUE (ticket_id) WHERE status IN ('WAITLIST_HOLD','PUBLISHED','RESERVED')`** — una sola publicación activa por boleto. Índices: `(status, expires_at) WHERE status IN ('WAITLIST_HOLD','PUBLISHED','RESERVED')` (expiración) · consulta de mercado vía join a tickets `(event_id)`.

### `commerce.temporal_reservations` — reserva temporal de compra
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK |
| listing_id | UUID | NO | FK→exchange_listings RESTRICT |
| buyer_id | UUID | NO | FK→users RESTRICT (≠ seller se valida en app: cross-table) |
| status | TEXT | NO | `CHECK IN ('ACTIVE','COMPLETED','EXPIRED','FAILED')` |
| expires_at | TIMESTAMPTZ | NO | — |

**`UNIQUE (listing_id) WHERE status='ACTIVE'`** — con el único de listings, la cadena garantiza a lo sumo **una reserva activa por ticket**. Índice `(status, expires_at) WHERE status='ACTIVE'`.

### `commerce.ticket_transfers` — transferencia consumada (append-only, agregado A7)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK (se expone en el historial del boleto por API → UUID, no BIGINT) |
| ticket_id | UUID | NO | FK→tickets RESTRICT |
| listing_id | UUID | NO | FK→exchange_listings RESTRICT |
| from_owner_id / to_owner_id | UUID | NO | FK→users RESTRICT · `CHECK (from_owner_id <> to_owner_id)` |
| original_price / exchange_price | NUMERIC(12,2) | NO | `CHECK (exchange_price <= original_price)` |
| depreciation_pct | SMALLINT | NO | — |
| fee_amount / seller_amount | NUMERIC(12,2) | NO | **`CHECK (fee_amount + seller_amount = exchange_price)`** — el dinero cuadra por definición |
| currency | CHAR(3) | NO | — |
| transferred_at | TIMESTAMPTZ | NO | default now() |

Índices: `(ticket_id, transferred_at)` · `(listing_id)` (auditoría A6). `REVOKE UPDATE, DELETE`. Redondeo oficial (auditoría M1): `fee_amount = round_half_up(exchange_price × fee_pct, 2)`; `seller_amount = exchange_price − fee_amount` (el residuo siempre favorece al vendedor).

### `commerce.waitlist_entries` — lista de espera FIFO (agregado A8, I3)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK |
| event_id | UUID | NO | FK→events RESTRICT |
| user_id | UUID | NO | FK→users RESTRICT |
| queue_seq | **BIGINT GENERATED ALWAYS AS IDENTITY** | NO | orden FIFO **inmutable y monótono**; nadie se renumera al salir alguien |
| status | TEXT | NO | `CHECK IN ('WAITING','OFFERED','FULFILLED','SKIPPED','CANCELLED')` |
| joined_at / updated_at | TIMESTAMPTZ | NO | — |
| version | INTEGER | NO | optimistic lock |

**`UNIQUE (event_id, user_id) WHERE status IN ('WAITING','OFFERED')`** — una inscripción activa por usuario y evento. Índice **`(event_id, status, queue_seq)`** — tomar la cabeza FIFO es un index scan directo.

### `commerce.waitlist_offers` — oferta con ventana (fuente polimórfica, auditoría C1)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK |
| entry_id | UUID | NO | FK→waitlist_entries RESTRICT |
| source_type | TEXT | NO | `CHECK IN ('INVENTORY','EXCHANGE')` — INVENTORY: cupo liberado por reembolso/cancelación (se compra un ticket **nuevo** del tipo); EXCHANGE: boleto concreto retenido en `WAITLIST_HOLD` |
| ticket_type_id | UUID | SÍ | FK→ticketing.ticket_types RESTRICT |
| listing_id | UUID | SÍ | FK→exchange_listings RESTRICT |
| status | TEXT | NO | `CHECK IN ('OFFERED','ACCEPTED','EXPIRED','DECLINED')` |
| offered_at / expires_at | TIMESTAMPTZ | NO | — |
| responded_at | TIMESTAMPTZ | SÍ | — |

`CHECK` de polimorfismo cerrado: `(source_type='INVENTORY' AND ticket_type_id IS NOT NULL AND listing_id IS NULL) OR (source_type='EXCHANGE' AND listing_id IS NOT NULL AND ticket_type_id IS NULL)`.

Únicos parciales: **`(listing_id) WHERE status='OFFERED'`** (un listing no se ofrece a dos personas) y **`(entry_id) WHERE status='OFFERED'`** (una oferta viva por entrada). Para INVENTORY no hay unicidad por tipo: N cupos liberados pueden ofrecerse en paralelo a N usuarios distintos. Índices: `(status, expires_at) WHERE status='OFFERED'` · `(entry_id)` (auditoría A6). La aceptación crea una Orden estándar (ítem `TICKET` o `EXCHANGE_TICKET`), reutilizando el pipeline de pago.

### `commerce.refund_requests` — solicitud de reembolso (agregado A9)
| Columna | Tipo | Null | Restricciones |
|---|---|---|---|
| id | UUID | NO | PK |
| ticket_id | UUID | NO | FK→tickets RESTRICT |
| requester_id | UUID | NO | FK→users RESTRICT |
| payment_id | UUID | NO | FK→payments RESTRICT — el pago APROBADO de la orden de **adquisición** del propietario actual (auditorías C2/A7); el expediente queda autocontenido |
| amount / currency | NUMERIC(12,2) / CHAR(3) | NO | congelado al solicitar: `= tickets.acquisition_price` (100% de lo que pagó el propietario actual, C2) |
| status | TEXT | NO | `CHECK IN ('REQUESTED','APPROVED','REJECTED','CANCELLED')` |
| reason | TEXT | SÍ | — |
| resolved_by / resolved_at | UUID / TIMESTAMPTZ | SÍ | FK→users; `CHECK (status IN ('REQUESTED','CANCELLED') OR resolved_by IS NOT NULL)` |
| created/updated/deleted_at, version | — | — | Soft Delete + optimistic lock |

**`UNIQUE (ticket_id) WHERE status='REQUESTED'`** — un reembolso activo por boleto. La exclusión con publicaciones la garantiza `tickets.status` (una sola columna no puede valer `REFUND_PENDING` y `PUBLISHED_IN_EXCHANGE` a la vez). **ADR-19**: solo boletos con `acquired_via='PRIMARY'` pueden crear solicitudes — regla cross-table validada en la capa de aplicación dentro de la misma TX (con test obligatorio). Índices: `(status) WHERE status='REQUESTED'` · `(ticket_id)` completo para el expediente histórico (auditoría A6).
