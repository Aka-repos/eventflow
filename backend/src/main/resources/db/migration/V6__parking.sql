-- EventFlow · V6: schema parking + FKs diferidas (order_items.parking_id, dynamic_qrs.parking_reservation_id)

CREATE TABLE parking.parkings (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    event_id    UUID          NOT NULL,
    name        TEXT          NOT NULL,
    type        TEXT          NOT NULL,
    total_slots INTEGER       NOT NULL,
    price       NUMERIC(12,2) NOT NULL,
    currency    CHAR(3)       NOT NULL,
    opens_at    TIMESTAMPTZ   NOT NULL,
    closes_at   TIMESTAMPTZ   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version     INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_parkings PRIMARY KEY (id),
    CONSTRAINT uq_parkings_name UNIQUE (event_id, name),
    CONSTRAINT fk_parkings_event FOREIGN KEY (event_id) REFERENCES catalog.events (id) ON DELETE RESTRICT,
    CONSTRAINT ck_parkings_type     CHECK (type IN ('VIP','GENERAL','STAFF','MOTO','ACCESSIBLE')),
    CONSTRAINT ck_parkings_slots    CHECK (total_slots >= 0),
    CONSTRAINT ck_parkings_price    CHECK (price >= 0),
    CONSTRAINT ck_parkings_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_parkings_hours    CHECK (closes_at > opens_at)
);
COMMENT ON TABLE parking.parkings IS 'Disponibilidad se deriva de parking_slots.status (sin contador; ver 07-bd-05 M9).';

CREATE TABLE parking.parking_slots (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    parking_id UUID        NOT NULL,
    code       TEXT        NOT NULL,
    status     TEXT        NOT NULL DEFAULT 'AVAILABLE',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT pk_parking_slots PRIMARY KEY (id),
    CONSTRAINT uq_parking_slots_code UNIQUE (parking_id, code),
    CONSTRAINT fk_parking_slots_parking FOREIGN KEY (parking_id) REFERENCES parking.parkings (id) ON DELETE RESTRICT,
    CONSTRAINT ck_parking_slots_status CHECK (status IN ('AVAILABLE','RESERVED','OCCUPIED','OUT_OF_SERVICE','BLOCKED'))
);
CREATE INDEX ix_parking_slots_status ON parking.parking_slots (parking_id, status);

CREATE TABLE parking.parking_reservations (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    slot_id       UUID        NOT NULL,
    order_item_id UUID        NOT NULL,
    user_id       UUID        NOT NULL,
    status        TEXT        NOT NULL DEFAULT 'PENDING',
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT pk_parking_reservations PRIMARY KEY (id),
    CONSTRAINT fk_preservations_slot  FOREIGN KEY (slot_id)       REFERENCES parking.parking_slots (id) ON DELETE RESTRICT,
    CONSTRAINT fk_preservations_item  FOREIGN KEY (order_item_id) REFERENCES commerce.order_items (id)  ON DELETE RESTRICT,
    CONSTRAINT fk_preservations_user  FOREIGN KEY (user_id)       REFERENCES identity.users (id)        ON DELETE RESTRICT,
    CONSTRAINT ck_preservations_status CHECK (status IN ('PENDING','CONFIRMED','IN_USE','COMPLETED','CANCELLED','EXPIRED')),
    CONSTRAINT ck_preservations_pending_expiry CHECK (status <> 'PENDING' OR expires_at IS NOT NULL)  -- auditoría B4
);
CREATE UNIQUE INDEX uq_preservations_slot_live ON parking.parking_reservations (slot_id)
    WHERE status IN ('PENDING','CONFIRMED','IN_USE');
CREATE INDEX ix_preservations_user         ON parking.parking_reservations (user_id, status);
CREATE INDEX ix_preservations_live_expiry  ON parking.parking_reservations (status, expires_at) WHERE status IN ('PENDING','CONFIRMED');
CREATE INDEX ix_preservations_order_item   ON parking.parking_reservations (order_item_id);

-- ===== FKs diferidas =====
ALTER TABLE commerce.order_items
    ADD CONSTRAINT fk_order_items_parking FOREIGN KEY (parking_id) REFERENCES parking.parkings (id) ON DELETE RESTRICT;

ALTER TABLE ticketing.dynamic_qrs
    ADD CONSTRAINT fk_dynamic_qrs_preservation FOREIGN KEY (parking_reservation_id)
        REFERENCES parking.parking_reservations (id) ON DELETE RESTRICT;
