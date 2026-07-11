-- EventFlow · V4: schema ticketing
-- FKs circulares diferidas: tickets.*_order_item_id → commerce.order_items (se añaden en V5);
-- dynamic_qrs.parking_reservation_id → parking.parking_reservations (se añade en V6).

CREATE TABLE ticketing.ticket_types (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    event_id       UUID          NOT NULL,
    zone_id        UUID,
    name           TEXT          NOT NULL,
    description    TEXT,
    price          NUMERIC(12,2) NOT NULL,
    currency       CHAR(3)       NOT NULL,
    total_quantity INTEGER       NOT NULL,
    sold_quantity  INTEGER       NOT NULL DEFAULT 0,
    sales_starts_at TIMESTAMPTZ,
    sales_ends_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_ticket_types PRIMARY KEY (id),
    CONSTRAINT uq_ticket_types_id_event UNIQUE (id, event_id),   -- destino de la FK compuesta de tickets
    CONSTRAINT uq_ticket_types_name     UNIQUE (event_id, name),
    CONSTRAINT fk_ticket_types_event FOREIGN KEY (event_id) REFERENCES catalog.events (id)      ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_types_zone  FOREIGN KEY (zone_id)  REFERENCES catalog.event_zones (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ticket_types_price    CHECK (price >= 0),
    CONSTRAINT ck_ticket_types_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_ticket_types_total    CHECK (total_quantity >= 0),
    CONSTRAINT ck_ticket_types_sold     CHECK (sold_quantity BETWEEN 0 AND total_quantity),  -- sobreventa físicamente imposible
    CONSTRAINT ck_ticket_types_sales    CHECK (sales_starts_at IS NULL OR sales_ends_at IS NULL OR sales_ends_at > sales_starts_at)
);
CREATE INDEX ix_ticket_types_event ON ticketing.ticket_types (event_id);
COMMENT ON COLUMN ticketing.ticket_types.sold_quantity IS 'Unidades comprometidas (PENDING no expiradas + PAID). Cupo liberado con oferta waitlist OFFERED sigue comprometido (07-bd-06 M5/C1). Modificar solo bajo SELECT FOR UPDATE.';

CREATE TABLE ticketing.tickets (
    id                        UUID          NOT NULL DEFAULT gen_random_uuid(),
    ticket_type_id            UUID          NOT NULL,
    event_id                  UUID          NOT NULL,
    current_owner_id          UUID          NOT NULL,
    source_order_item_id      UUID          NOT NULL,  -- FK en V5 (ítem de la emisión primaria, I4)
    acquisition_order_item_id UUID          NOT NULL,  -- FK en V5 (ítem del propietario actual, auditoría C2)
    acquired_via              TEXT          NOT NULL DEFAULT 'PRIMARY',
    status                    TEXT          NOT NULL DEFAULT 'ACTIVE',
    original_price            NUMERIC(12,2) NOT NULL,
    acquisition_price         NUMERIC(12,2) NOT NULL,  -- base del reembolso (C2/ADR-19)
    currency                  CHAR(3)       NOT NULL,
    policy_snapshot           JSONB         NOT NULL,  -- ADR-03: condiciones vigentes al comprar
    purchased_at              TIMESTAMPTZ   NOT NULL,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at                TIMESTAMPTZ,
    version                   INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_tickets PRIMARY KEY (id),
    CONSTRAINT uq_tickets_id_event UNIQUE (id, event_id),  -- destino de la FK compuesta de event_checkins (M4)
    -- FK compuesta: la desnormalización de event_id no puede divergir de la tarifa
    CONSTRAINT fk_tickets_type_event FOREIGN KEY (ticket_type_id, event_id)
        REFERENCES ticketing.ticket_types (id, event_id) ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_owner FOREIGN KEY (current_owner_id) REFERENCES identity.users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tickets_acquired_via CHECK (acquired_via IN ('PRIMARY','EXCHANGE')),  -- ADR-19
    CONSTRAINT ck_tickets_status CHECK (status IN
        ('ACTIVE','PUBLISHED_IN_EXCHANGE','REFUND_PENDING','REFUNDED','USED','EXPIRED','CANCELLED','INVALIDATED')),
    CONSTRAINT ck_tickets_original_price    CHECK (original_price >= 0),
    CONSTRAINT ck_tickets_acquisition_price CHECK (acquisition_price >= 0),
    CONSTRAINT ck_tickets_currency          CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX ix_tickets_owner_status ON ticketing.tickets (current_owner_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_tickets_event_status ON ticketing.tickets (event_id, status);
CREATE INDEX ix_tickets_type         ON ticketing.tickets (ticket_type_id);
COMMENT ON TABLE ticketing.tickets IS 'Ticket ID permanente; solo cambia el propietario. current_owner_id única columna = un dueño vigente físico. acquired_via=EXCHANGE nunca puede solicitar reembolso (ADR-19, validado en app + test).';

CREATE TABLE ticketing.ticket_history (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY,
    ticket_id   UUID        NOT NULL,
    from_status TEXT        NOT NULL,
    to_status   TEXT        NOT NULL,
    actor_id    UUID,
    cause       TEXT        NOT NULL,
    metadata    JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_ticket_history PRIMARY KEY (id),
    CONSTRAINT fk_ticket_history_ticket FOREIGN KEY (ticket_id) REFERENCES ticketing.tickets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_history_actor  FOREIGN KEY (actor_id)  REFERENCES identity.users (id)    ON DELETE RESTRICT,
    CONSTRAINT ck_ticket_history_cause CHECK (cause IN
        ('ISSUED','PUBLISH','UNPUBLISH','TRANSFER','REFUND_REQUEST','REFUND_APPROVED','REFUND_REJECTED',
         'CHECKIN','EXPIRE','INVALIDATE','REISSUE','EVENT_CANCELLED'))
);
CREATE INDEX ix_ticket_history_ticket ON ticketing.ticket_history (ticket_id, occurred_at);
COMMENT ON TABLE ticketing.ticket_history IS 'Append-only (privilegios en V8). El historial del boleto nunca se pierde.';

CREATE TABLE ticketing.dynamic_qrs (
    id                     UUID        NOT NULL DEFAULT gen_random_uuid(),  -- qr_id dentro del JWS
    subject_type           TEXT        NOT NULL,
    ticket_id              UUID,
    parking_reservation_id UUID,                                            -- FK en V6
    status                 TEXT        NOT NULL DEFAULT 'ACTIVE',
    key_id                 TEXT        NOT NULL,                            -- kid de la llave de firma (rotación)
    issued_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_dynamic_qrs PRIMARY KEY (id),
    CONSTRAINT fk_dynamic_qrs_ticket FOREIGN KEY (ticket_id) REFERENCES ticketing.tickets (id) ON DELETE RESTRICT,
    CONSTRAINT ck_dynamic_qrs_subject_type CHECK (subject_type IN ('TICKET','PARKING')),
    CONSTRAINT ck_dynamic_qrs_status CHECK (status IN ('ACTIVE','BLOCKED','INVALIDATED','CONSUMED','EXPIRED')),
    CONSTRAINT ck_dynamic_qrs_subject_oneof CHECK (
        (subject_type = 'TICKET'  AND ticket_id IS NOT NULL AND parking_reservation_id IS NULL) OR
        (subject_type = 'PARKING' AND parking_reservation_id IS NOT NULL AND ticket_id IS NULL)
    )
);
-- Jamás dos QR vigentes por sujeto (regla física central del dominio)
CREATE UNIQUE INDEX uq_dynamic_qrs_ticket_live  ON ticketing.dynamic_qrs (ticket_id)              WHERE status IN ('ACTIVE','BLOCKED');
CREATE UNIQUE INDEX uq_dynamic_qrs_parking_live ON ticketing.dynamic_qrs (parking_reservation_id) WHERE status IN ('ACTIVE','BLOCKED');
CREATE INDEX ix_dynamic_qrs_expiry ON ticketing.dynamic_qrs (expires_at) WHERE status = 'ACTIVE';
CREATE INDEX ix_dynamic_qrs_ticket ON ticketing.dynamic_qrs (ticket_id);  -- historial completo (A6)
COMMENT ON TABLE ticketing.dynamic_qrs IS 'El QR codifica solo {qr_id, kid, exp} firmado (JWS ES256). Toda validación es server-side (ADR-08).';
