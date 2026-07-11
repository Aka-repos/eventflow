-- EventFlow · V3: schema catalog + identity.staff_assignments (depende de events)

CREATE TABLE catalog.categories (
    id     SMALLINT GENERATED ALWAYS AS IDENTITY,
    name   TEXT     NOT NULL,
    icon   TEXT,
    active BOOLEAN  NOT NULL DEFAULT true,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uq_categories_name UNIQUE (name)
);

CREATE TABLE catalog.events (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    organizer_id  UUID          NOT NULL,
    category_id   SMALLINT      NOT NULL,
    title         TEXT          NOT NULL,
    description   TEXT          NOT NULL DEFAULT '',
    venue_name    TEXT          NOT NULL,
    address       TEXT,
    latitude      NUMERIC(9,6),
    longitude     NUMERIC(9,6),
    timezone      TEXT          NOT NULL DEFAULT 'America/Panama',  -- IANA; ancla ventanas "N horas antes" (auditoría A5)
    starts_at     TIMESTAMPTZ   NOT NULL,
    ends_at       TIMESTAMPTZ   NOT NULL,
    status        TEXT          NOT NULL DEFAULT 'DRAFT',
    cover_url     TEXT,
    search_vector TSVECTOR      GENERATED ALWAYS AS (to_tsvector('spanish', title || ' ' || description)) STORED,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    version       INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_events PRIMARY KEY (id),
    CONSTRAINT fk_events_organizer FOREIGN KEY (organizer_id) REFERENCES identity.users (id)    ON DELETE RESTRICT,
    CONSTRAINT fk_events_category  FOREIGN KEY (category_id)  REFERENCES catalog.categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_events_status     CHECK (status IN ('DRAFT','PUBLISHED','SOLD_OUT','IN_PROGRESS','FINISHED','CANCELLED','SUSPENDED')),
    CONSTRAINT ck_events_title_len  CHECK (length(title) BETWEEN 3 AND 200),
    CONSTRAINT ck_events_dates      CHECK (ends_at > starts_at),
    CONSTRAINT ck_events_lat_range  CHECK (latitude  IS NULL OR latitude  BETWEEN -90  AND 90),
    CONSTRAINT ck_events_lng_range  CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_events_geo_pair   CHECK ((latitude IS NULL) = (longitude IS NULL))
);
CREATE INDEX ix_events_status_starts ON catalog.events (status, starts_at) WHERE deleted_at IS NULL;
CREATE INDEX ix_events_organizer     ON catalog.events (organizer_id);
CREATE INDEX ix_events_category      ON catalog.events (category_id, starts_at);
CREATE INDEX ix_events_search        ON catalog.events USING GIN (search_vector);

CREATE TABLE catalog.event_policies (
    event_id                   UUID        NOT NULL,
    refund_window_ends_at      TIMESTAMPTZ,
    refund_pct                 SMALLINT    NOT NULL DEFAULT 100,
    exchange_enabled           BOOLEAN     NOT NULL DEFAULT false,
    exchange_depreciation_pct  SMALLINT    NOT NULL DEFAULT 10,
    exchange_listing_deadline  TIMESTAMPTZ,
    waitlist_enabled           BOOLEAN     NOT NULL DEFAULT false,
    waitlist_offer_minutes     INTEGER     NOT NULL DEFAULT 15,
    temp_reservation_minutes   INTEGER     NOT NULL DEFAULT 10,
    qr_visibility_hours_before INTEGER     NOT NULL DEFAULT 24,
    qr_expiration_minutes      INTEGER     NOT NULL DEFAULT 60,
    cancellation_policy        TEXT,
    extra_policies             JSONB       NOT NULL DEFAULT '{}',
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                    INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT pk_event_policies PRIMARY KEY (event_id),  -- 1:1 real (PK = FK)
    CONSTRAINT fk_event_policies_event FOREIGN KEY (event_id) REFERENCES catalog.events (id) ON DELETE RESTRICT,
    CONSTRAINT ck_policies_refund_pct   CHECK (refund_pct BETWEEN 0 AND 100),
    CONSTRAINT ck_policies_depreciation CHECK (exchange_depreciation_pct BETWEEN 0 AND 100),
    CONSTRAINT ck_policies_waitlist_min CHECK (waitlist_offer_minutes > 0),
    CONSTRAINT ck_policies_reserve_min  CHECK (temp_reservation_minutes > 0),
    CONSTRAINT ck_policies_qr_vis       CHECK (qr_visibility_hours_before >= 0),
    CONSTRAINT ck_policies_qr_exp       CHECK (qr_expiration_minutes > 0)
);
COMMENT ON TABLE catalog.event_policies IS 'ADR-02: reglas configurables separadas del evento. extra_policies JSONB = extensión sin migración.';

CREATE TABLE catalog.event_zones (
    id       UUID    NOT NULL DEFAULT gen_random_uuid(),
    event_id UUID    NOT NULL,
    name     TEXT    NOT NULL,
    capacity INTEGER NOT NULL,
    CONSTRAINT pk_event_zones PRIMARY KEY (id),
    CONSTRAINT uq_event_zones_name UNIQUE (event_id, name),
    CONSTRAINT ck_event_zones_capacity CHECK (capacity > 0),
    CONSTRAINT fk_event_zones_event FOREIGN KEY (event_id) REFERENCES catalog.events (id) ON DELETE RESTRICT
);

CREATE TABLE catalog.sponsors (
    id       UUID NOT NULL DEFAULT gen_random_uuid(),
    name     TEXT NOT NULL,
    logo_url TEXT,
    website  TEXT,
    CONSTRAINT pk_sponsors PRIMARY KEY (id)
);

CREATE TABLE catalog.sponsor_events (
    sponsor_id UUID NOT NULL,
    event_id   UUID NOT NULL,
    CONSTRAINT pk_sponsor_events PRIMARY KEY (sponsor_id, event_id),
    CONSTRAINT fk_sponsor_events_sponsor FOREIGN KEY (sponsor_id) REFERENCES catalog.sponsors (id) ON DELETE CASCADE,
    CONSTRAINT fk_sponsor_events_event   FOREIGN KEY (event_id)   REFERENCES catalog.events (id)   ON DELETE CASCADE
);

-- Staff de acceso (ADR-13): vive en identity pero depende de events
CREATE TABLE identity.staff_assignments (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    event_id    UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    permissions TEXT[]      NOT NULL DEFAULT '{CHECKIN_EVENT}',
    assigned_by UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at  TIMESTAMPTZ,
    CONSTRAINT pk_staff_assignments PRIMARY KEY (id),
    CONSTRAINT fk_staff_event    FOREIGN KEY (event_id)    REFERENCES catalog.events (id)  ON DELETE RESTRICT,
    CONSTRAINT fk_staff_user     FOREIGN KEY (user_id)     REFERENCES identity.users (id)  ON DELETE RESTRICT,
    CONSTRAINT fk_staff_assigner FOREIGN KEY (assigned_by) REFERENCES identity.users (id)  ON DELETE RESTRICT
);
CREATE UNIQUE INDEX uq_staff_active ON identity.staff_assignments (event_id, user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_staff_user_active   ON identity.staff_assignments (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_staff_event         ON identity.staff_assignments (event_id);  -- auditoría A6
