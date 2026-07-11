-- EventFlow · V7: schema ops (check-ins, engagement, plataforma)

CREATE TABLE ops.event_checkins (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY,
    ticket_id     UUID        NOT NULL,
    event_id      UUID        NOT NULL,
    qr_id         UUID        NOT NULL,
    scanned_by    UUID        NOT NULL,
    result        TEXT        NOT NULL,
    denial_reason TEXT,
    device_info   JSONB,
    ip            INET,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_event_checkins PRIMARY KEY (id),
    -- FK compuesta (auditoría M4): el event_id desnormalizado no puede divergir del ticket
    CONSTRAINT fk_checkins_ticket_event FOREIGN KEY (ticket_id, event_id) REFERENCES ticketing.tickets (id, event_id) ON DELETE RESTRICT,
    CONSTRAINT fk_checkins_qr      FOREIGN KEY (qr_id)      REFERENCES ticketing.dynamic_qrs (id) ON DELETE RESTRICT,
    CONSTRAINT fk_checkins_scanner FOREIGN KEY (scanned_by) REFERENCES identity.users (id)        ON DELETE RESTRICT,
    CONSTRAINT ck_checkins_result CHECK (result IN ('GRANTED','DENIED')),
    CONSTRAINT ck_checkins_denial CHECK (result = 'GRANTED' OR denial_reason IS NOT NULL)
);
-- Un boleto entra una sola vez: ley física (auditoría; migrar a política por evento si algún día hay re-entrada, M6)
CREATE UNIQUE INDEX uq_checkins_granted ON ops.event_checkins (ticket_id) WHERE result = 'GRANTED';
CREATE INDEX ix_checkins_event  ON ops.event_checkins (event_id, occurred_at);
CREATE INDEX ix_checkins_ticket ON ops.event_checkins (ticket_id);
COMMENT ON TABLE ops.event_checkins IS 'Append-only. Registra también intentos DENIED (evidencia antifraude).';

CREATE TABLE ops.parking_checkins (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY,
    reservation_id UUID        NOT NULL,
    direction      TEXT        NOT NULL,
    result         TEXT        NOT NULL,
    scanned_by     UUID        NOT NULL,
    device_info    JSONB,
    ip             INET,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_parking_checkins PRIMARY KEY (id),
    CONSTRAINT fk_pcheckins_reservation FOREIGN KEY (reservation_id) REFERENCES parking.parking_reservations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pcheckins_scanner     FOREIGN KEY (scanned_by)     REFERENCES identity.users (id)               ON DELETE RESTRICT,
    CONSTRAINT ck_pcheckins_direction CHECK (direction IN ('IN','OUT')),
    CONSTRAINT ck_pcheckins_result    CHECK (result IN ('GRANTED','DENIED'))
);
CREATE INDEX ix_pcheckins_reservation ON ops.parking_checkins (reservation_id, occurred_at);

CREATE TABLE ops.favorites (
    user_id    UUID        NOT NULL,
    event_id   UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_favorites PRIMARY KEY (user_id, event_id),
    CONSTRAINT fk_favorites_user  FOREIGN KEY (user_id)  REFERENCES identity.users (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorites_event FOREIGN KEY (event_id) REFERENCES catalog.events (id) ON DELETE CASCADE
);
CREATE INDEX ix_favorites_event ON ops.favorites (event_id);

CREATE TABLE ops.notifications (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    type       TEXT        NOT NULL,
    title      TEXT        NOT NULL,
    body       TEXT        NOT NULL,
    payload    JSONB,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES identity.users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_type CHECK (type IN
        ('PURCHASE_SUCCESS','EVENT_REMINDER','PARKING_RESERVED','SCHEDULE_CHANGE','CANCELLATION',
         'WAITLIST_OFFER','EXCHANGE_SOLD','REFUND_RESOLVED','GENERIC'))
);
CREATE INDEX ix_notifications_user   ON ops.notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON ops.notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE ops.global_config (
    key         TEXT        NOT NULL,
    value       JSONB       NOT NULL,
    description TEXT,
    updated_by  UUID,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT pk_global_config PRIMARY KEY (key),
    CONSTRAINT fk_global_config_updater FOREIGN KEY (updated_by) REFERENCES identity.users (id) ON DELETE RESTRICT
);
COMMENT ON TABLE ops.global_config IS 'Comisión del Exchange, tiempos por defecto, proveedores. Solo ADMIN escribe (autorización en app).';

CREATE TABLE ops.idempotency_keys (
    user_id         UUID        NOT NULL,
    idem_key        UUID        NOT NULL,
    endpoint        TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status SMALLINT,
    response_body   JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (user_id, idem_key),  -- scope por usuario: nadie quema claves ajenas
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES identity.users (id) ON DELETE CASCADE
);
CREATE INDEX ix_idempotency_expiry ON ops.idempotency_keys (expires_at);
COMMENT ON TABLE ops.idempotency_keys IS 'ADR-07. TTL 48h + job de limpieza. Mismo key con request_hash distinto ⇒ 422.';

CREATE TABLE ops.outbox_events (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY,
    aggregate_type TEXT        NOT NULL,
    aggregate_id   UUID        NOT NULL,
    event_type     TEXT        NOT NULL,
    payload        JSONB       NOT NULL,
    status         TEXT        NOT NULL DEFAULT 'PENDING',
    attempts       SMALLINT    NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at   TIMESTAMPTZ,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING','PROCESSED','FAILED'))
);
-- El dispatcher consume con SELECT ... FOR UPDATE SKIP LOCKED (auditoría M7)
CREATE INDEX ix_outbox_pending ON ops.outbox_events (created_at) WHERE status = 'PENDING';
COMMENT ON TABLE ops.outbox_events IS 'ADR-09: persistido en la misma TX del caso de uso. payload.eventVersion versiona el contrato.';

CREATE TABLE ops.audit_log (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY,
    actor_id    UUID,        -- sin FK deliberadamente: la auditoría sobrevive a todo (07-bd-05)
    action      TEXT        NOT NULL,
    entity_type TEXT        NOT NULL,
    entity_id   TEXT        NOT NULL,
    ip          INET,
    device      TEXT,
    details     JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);
CREATE INDEX ix_audit_entity ON ops.audit_log (entity_type, entity_id, occurred_at);
CREATE INDEX ix_audit_actor  ON ops.audit_log (actor_id, occurred_at);
COMMENT ON TABLE ops.audit_log IS 'Append-only, poblada por el consumidor del outbox. Particionar por mes al superar ~10^7 filas.';
