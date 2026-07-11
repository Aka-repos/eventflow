-- EventFlow · V5: schema commerce
-- Orden interno por dependencias: orders → exchange_listings → temporal_reservations →
-- order_items → payments → refund_requests → waitlist → transfers → ledger.
-- order_items.parking_id queda sin FK hasta V6. Al final: FKs diferidas de ticketing.tickets.

CREATE TABLE commerce.orders (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    buyer_id        UUID          NOT NULL,
    status          TEXT          NOT NULL DEFAULT 'PENDING',
    total_amount    NUMERIC(12,2) NOT NULL,
    currency        CHAR(3)       NOT NULL,
    expires_at      TIMESTAMPTZ   NOT NULL,
    idempotency_key UUID,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    version         INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES identity.users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_orders_status   CHECK (status IN ('PENDING','PAID','FAILED','CANCELLED','REFUNDED')),
    CONSTRAINT ck_orders_total    CHECK (total_amount >= 0),
    CONSTRAINT ck_orders_currency CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE UNIQUE INDEX uq_orders_idem_key ON commerce.orders (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_orders_buyer   ON commerce.orders (buyer_id, created_at DESC);
CREATE INDEX ix_orders_pending ON commerce.orders (status, expires_at) WHERE status = 'PENDING';
COMMENT ON TABLE commerce.orders IS 'Consistencia total_amount = Σ ítems y moneda homogénea: capa de aplicación en la misma TX (test obligatorio, 07-bd-06 §9). REFUNDED solo si el 100% de ítems se devuelve (M3).';

CREATE TABLE commerce.exchange_listings (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),
    ticket_id         UUID          NOT NULL,
    seller_id         UUID          NOT NULL,
    original_price    NUMERIC(12,2) NOT NULL,
    list_price        NUMERIC(12,2) NOT NULL,
    depreciation_pct  SMALLINT      NOT NULL,
    currency          CHAR(3)       NOT NULL,
    status            TEXT          NOT NULL,
    published_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version           INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_exchange_listings PRIMARY KEY (id),
    CONSTRAINT fk_listings_ticket FOREIGN KEY (ticket_id) REFERENCES ticketing.tickets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_listings_seller FOREIGN KEY (seller_id) REFERENCES identity.users (id)    ON DELETE RESTRICT,
    -- WAITLIST_HOLD (auditoría A1): retenido para la fila FIFO, invisible en el marketplace
    CONSTRAINT ck_listings_status CHECK (status IN ('WAITLIST_HOLD','PUBLISHED','RESERVED','SOLD','CANCELLED','EXPIRED')),
    CONSTRAINT ck_listings_no_markup CHECK (list_price <= original_price),  -- sobreprecio físicamente imposible
    CONSTRAINT ck_listings_prices    CHECK (list_price >= 0 AND original_price >= 0),
    CONSTRAINT ck_listings_depreciation CHECK (depreciation_pct BETWEEN 0 AND 100),
    CONSTRAINT ck_listings_currency  CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE UNIQUE INDEX uq_listings_ticket_live ON commerce.exchange_listings (ticket_id)
    WHERE status IN ('WAITLIST_HOLD','PUBLISHED','RESERVED');
CREATE INDEX ix_listings_live_expiry ON commerce.exchange_listings (status, expires_at)
    WHERE status IN ('WAITLIST_HOLD','PUBLISHED','RESERVED');

CREATE TABLE commerce.temporal_reservations (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    listing_id UUID        NOT NULL,
    buyer_id   UUID        NOT NULL,
    status     TEXT        NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_temporal_reservations PRIMARY KEY (id),
    CONSTRAINT fk_treservations_listing FOREIGN KEY (listing_id) REFERENCES commerce.exchange_listings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_treservations_buyer   FOREIGN KEY (buyer_id)   REFERENCES identity.users (id)             ON DELETE RESTRICT,
    CONSTRAINT ck_treservations_status CHECK (status IN ('ACTIVE','COMPLETED','EXPIRED','FAILED'))
);
CREATE UNIQUE INDEX uq_treservations_listing_active ON commerce.temporal_reservations (listing_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_treservations_active_expiry ON commerce.temporal_reservations (status, expires_at) WHERE status = 'ACTIVE';

CREATE TABLE commerce.order_items (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    order_id                UUID          NOT NULL,
    item_type               TEXT          NOT NULL,
    ticket_type_id          UUID,
    parking_id              UUID,          -- FK en V6
    temporal_reservation_id UUID,
    quantity                INTEGER       NOT NULL,
    unit_price              NUMERIC(12,2) NOT NULL,
    currency                CHAR(3)       NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT uq_order_items_treservation UNIQUE (temporal_reservation_id),  -- una reserva se compra a lo sumo una vez
    CONSTRAINT fk_order_items_order        FOREIGN KEY (order_id)       REFERENCES commerce.orders (id)         ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_ticket_type  FOREIGN KEY (ticket_type_id) REFERENCES ticketing.ticket_types (id)  ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_treservation FOREIGN KEY (temporal_reservation_id) REFERENCES commerce.temporal_reservations (id) ON DELETE RESTRICT,
    CONSTRAINT ck_order_items_type     CHECK (item_type IN ('TICKET','PARKING','EXCHANGE_TICKET')),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_exchange_qty CHECK (item_type <> 'EXCHANGE_TICKET' OR quantity = 1),
    CONSTRAINT ck_order_items_price    CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- Polimorfismo cerrado (auditoría I1): exactamente una referencia, acorde al tipo
    CONSTRAINT ck_order_items_ref_oneof CHECK (
        (item_type = 'TICKET'          AND ticket_type_id IS NOT NULL AND parking_id IS NULL     AND temporal_reservation_id IS NULL) OR
        (item_type = 'PARKING'         AND parking_id IS NOT NULL     AND ticket_type_id IS NULL AND temporal_reservation_id IS NULL) OR
        (item_type = 'EXCHANGE_TICKET' AND temporal_reservation_id IS NOT NULL AND ticket_type_id IS NULL AND parking_id IS NULL)
    )
);
CREATE INDEX ix_order_items_order       ON commerce.order_items (order_id);
CREATE INDEX ix_order_items_ticket_type ON commerce.order_items (ticket_type_id);  -- A6
CREATE INDEX ix_order_items_parking     ON commerce.order_items (parking_id);      -- A6

CREATE TABLE commerce.payments (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    order_id       UUID          NOT NULL,
    provider       TEXT          NOT NULL,
    provider_ref   TEXT,
    status         TEXT          NOT NULL DEFAULT 'PENDING',
    amount         NUMERIC(12,2) NOT NULL,
    currency       CHAR(3)       NOT NULL,
    failure_reason TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES commerce.orders (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payments_provider CHECK (provider IN ('FAKE','STRIPE','PAYPAL','YAPPY','CARD','TRANSFER')),
    CONSTRAINT ck_payments_status   CHECK (status IN ('PENDING','APPROVED','DECLINED','REFUNDED')),
    CONSTRAINT ck_payments_amount   CHECK (amount >= 0),
    CONSTRAINT ck_payments_currency CHECK (currency ~ '^[A-Z]{3}$')
);
-- Auditoría A4: una orden jamás se cobra dos veces, incluso tras reembolso
CREATE UNIQUE INDEX uq_payments_order_settled ON commerce.payments (order_id) WHERE status IN ('APPROVED','REFUNDED');
CREATE UNIQUE INDEX uq_payments_provider_ref  ON commerce.payments (provider, provider_ref) WHERE provider_ref IS NOT NULL;
CREATE INDEX ix_payments_order ON commerce.payments (order_id);
COMMENT ON TABLE commerce.payments IS 'Patrón payment-intent (auditoría A2): la fila PENDING se crea ANTES de invocar al proveedor; job de reconciliación idempotente resuelve PENDING huérfanos.';

CREATE TABLE commerce.refund_requests (
    id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    ticket_id    UUID          NOT NULL,
    requester_id UUID          NOT NULL,
    payment_id   UUID          NOT NULL,  -- pago de la ADQUISICIÓN del dueño actual (C2/A7)
    amount       NUMERIC(12,2) NOT NULL,  -- congelado = tickets.acquisition_price
    currency     CHAR(3)       NOT NULL,
    status       TEXT          NOT NULL DEFAULT 'REQUESTED',
    reason       TEXT,
    resolved_by  UUID,
    resolved_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    version      INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_refund_requests PRIMARY KEY (id),
    CONSTRAINT fk_refunds_ticket    FOREIGN KEY (ticket_id)    REFERENCES ticketing.tickets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_requester FOREIGN KEY (requester_id) REFERENCES identity.users (id)    ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_payment   FOREIGN KEY (payment_id)   REFERENCES commerce.payments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_resolver  FOREIGN KEY (resolved_by)  REFERENCES identity.users (id)    ON DELETE RESTRICT,
    CONSTRAINT ck_refunds_status   CHECK (status IN ('REQUESTED','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_refunds_amount   CHECK (amount >= 0),
    CONSTRAINT ck_refunds_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_refunds_resolution CHECK (status IN ('REQUESTED','CANCELLED') OR resolved_by IS NOT NULL)
);
CREATE UNIQUE INDEX uq_refunds_ticket_active ON commerce.refund_requests (ticket_id) WHERE status = 'REQUESTED';
CREATE INDEX ix_refunds_requested ON commerce.refund_requests (status) WHERE status = 'REQUESTED';
CREATE INDEX ix_refunds_ticket    ON commerce.refund_requests (ticket_id);  -- A6
COMMENT ON TABLE commerce.refund_requests IS 'ADR-19: solo tickets acquired_via=PRIMARY pueden solicitar (regla cross-table en app, test obligatorio).';

CREATE TABLE commerce.waitlist_entries (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    event_id   UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    queue_seq  BIGINT      GENERATED ALWAYS AS IDENTITY,  -- FIFO inmutable (auditoría I3)
    status     TEXT        NOT NULL DEFAULT 'WAITING',
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT pk_waitlist_entries PRIMARY KEY (id),
    CONSTRAINT fk_waitlist_event FOREIGN KEY (event_id) REFERENCES catalog.events (id) ON DELETE RESTRICT,
    CONSTRAINT fk_waitlist_user  FOREIGN KEY (user_id)  REFERENCES identity.users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_waitlist_status CHECK (status IN ('WAITING','OFFERED','FULFILLED','SKIPPED','CANCELLED'))
);
CREATE UNIQUE INDEX uq_waitlist_active ON commerce.waitlist_entries (event_id, user_id) WHERE status IN ('WAITING','OFFERED');
CREATE INDEX ix_waitlist_fifo ON commerce.waitlist_entries (event_id, status, queue_seq);

CREATE TABLE commerce.waitlist_offers (
    id             UUID        NOT NULL DEFAULT gen_random_uuid(),
    entry_id       UUID        NOT NULL,
    source_type    TEXT        NOT NULL,
    ticket_type_id UUID,
    listing_id     UUID,
    status         TEXT        NOT NULL DEFAULT 'OFFERED',
    offered_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL,
    responded_at   TIMESTAMPTZ,
    CONSTRAINT pk_waitlist_offers PRIMARY KEY (id),
    CONSTRAINT fk_offers_entry       FOREIGN KEY (entry_id)       REFERENCES commerce.waitlist_entries (id)  ON DELETE RESTRICT,
    CONSTRAINT fk_offers_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES ticketing.ticket_types (id)     ON DELETE RESTRICT,
    CONSTRAINT fk_offers_listing     FOREIGN KEY (listing_id)     REFERENCES commerce.exchange_listings (id) ON DELETE RESTRICT,
    CONSTRAINT ck_offers_status CHECK (status IN ('OFFERED','ACCEPTED','EXPIRED','DECLINED')),
    -- Fuente polimórfica (auditoría C1): INVENTORY = cupo liberado (ticket nuevo); EXCHANGE = listing en WAITLIST_HOLD
    CONSTRAINT ck_offers_source_type  CHECK (source_type IN ('INVENTORY','EXCHANGE')),
    CONSTRAINT ck_offers_source_oneof CHECK (
        (source_type = 'INVENTORY' AND ticket_type_id IS NOT NULL AND listing_id IS NULL) OR
        (source_type = 'EXCHANGE'  AND listing_id IS NOT NULL     AND ticket_type_id IS NULL)
    )
);
CREATE UNIQUE INDEX uq_offers_listing_live ON commerce.waitlist_offers (listing_id) WHERE status = 'OFFERED';
CREATE UNIQUE INDEX uq_offers_entry_live   ON commerce.waitlist_offers (entry_id)   WHERE status = 'OFFERED';
CREATE INDEX ix_offers_live_expiry ON commerce.waitlist_offers (status, expires_at) WHERE status = 'OFFERED';
CREATE INDEX ix_offers_entry       ON commerce.waitlist_offers (entry_id);       -- A6
CREATE INDEX ix_offers_ticket_type ON commerce.waitlist_offers (ticket_type_id);

CREATE TABLE commerce.ticket_transfers (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    ticket_id        UUID          NOT NULL,
    listing_id       UUID          NOT NULL,
    from_owner_id    UUID          NOT NULL,
    to_owner_id      UUID          NOT NULL,
    original_price   NUMERIC(12,2) NOT NULL,
    exchange_price   NUMERIC(12,2) NOT NULL,
    depreciation_pct SMALLINT      NOT NULL,
    fee_amount       NUMERIC(12,2) NOT NULL,
    seller_amount    NUMERIC(12,2) NOT NULL,
    currency         CHAR(3)       NOT NULL,
    transferred_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_ticket_transfers PRIMARY KEY (id),
    CONSTRAINT fk_transfers_ticket  FOREIGN KEY (ticket_id)     REFERENCES ticketing.tickets (id)          ON DELETE RESTRICT,
    CONSTRAINT fk_transfers_listing FOREIGN KEY (listing_id)    REFERENCES commerce.exchange_listings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfers_from    FOREIGN KEY (from_owner_id) REFERENCES identity.users (id)             ON DELETE RESTRICT,
    CONSTRAINT fk_transfers_to      FOREIGN KEY (to_owner_id)   REFERENCES identity.users (id)             ON DELETE RESTRICT,
    CONSTRAINT ck_transfers_distinct_owners CHECK (from_owner_id <> to_owner_id),
    CONSTRAINT ck_transfers_no_markup       CHECK (exchange_price <= original_price),
    CONSTRAINT ck_transfers_money_squares   CHECK (fee_amount + seller_amount = exchange_price),  -- el dinero cuadra por definición
    CONSTRAINT ck_transfers_amounts CHECK (fee_amount >= 0 AND seller_amount >= 0 AND exchange_price >= 0 AND original_price >= 0),
    CONSTRAINT ck_transfers_currency CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX ix_transfers_ticket  ON commerce.ticket_transfers (ticket_id, transferred_at);
CREATE INDEX ix_transfers_listing ON commerce.ticket_transfers (listing_id);  -- A6
COMMENT ON TABLE commerce.ticket_transfers IS 'Append-only. Redondeo (M1): fee = round_half_up(price×pct,2); seller = price − fee.';

CREATE TABLE commerce.ledger_entries (
    id                  BIGINT        GENERATED ALWAYS AS IDENTITY,
    entry_type          TEXT          NOT NULL,
    source_account      TEXT          NOT NULL,
    destination_account TEXT          NOT NULL,
    amount              NUMERIC(12,2) NOT NULL,
    currency            CHAR(3)       NOT NULL,
    fee_amount          NUMERIC(12,2),
    reference_type      TEXT          NOT NULL,
    reference_id        UUID          NOT NULL,
    event_id            UUID,
    occurred_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    details             JSONB,
    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),
    CONSTRAINT fk_ledger_event FOREIGN KEY (event_id) REFERENCES catalog.events (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ledger_entry_type CHECK (entry_type IN ('SALE','PARKING_SALE','EXCHANGE_SALE','PLATFORM_FEE','SELLER_PAYOUT','REFUND')),
    CONSTRAINT ck_ledger_ref_type   CHECK (reference_type IN ('ORDER','TRANSFER','REFUND')),
    CONSTRAINT ck_ledger_amount     CHECK (amount > 0),
    CONSTRAINT ck_ledger_fee        CHECK (fee_amount IS NULL OR fee_amount >= 0),
    CONSTRAINT ck_ledger_accounts_differ CHECK (source_account <> destination_account),
    CONSTRAINT ck_ledger_currency   CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX ix_ledger_event     ON commerce.ledger_entries (event_id, occurred_at);
CREATE INDEX ix_ledger_type      ON commerce.ledger_entries (entry_type, occurred_at);
CREATE INDEX ix_ledger_reference ON commerce.ledger_entries (reference_type, reference_id);
COMMENT ON TABLE commerce.ledger_entries IS 'Partida doble append-only (ADR-14). Cuentas: PLATFORM | BUYER:<uuid> | SELLER:<uuid> | ORGANIZER:<uuid>. Dashboard y conciliación leen de aquí.';

-- ===== FKs diferidas de V4 (ciclo ticketing ↔ commerce, inherente al dominio) =====
ALTER TABLE ticketing.tickets
    ADD CONSTRAINT fk_tickets_source_item      FOREIGN KEY (source_order_item_id)      REFERENCES commerce.order_items (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_tickets_acquisition_item FOREIGN KEY (acquisition_order_item_id) REFERENCES commerce.order_items (id) ON DELETE RESTRICT;
