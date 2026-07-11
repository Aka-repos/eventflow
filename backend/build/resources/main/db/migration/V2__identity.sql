-- EventFlow · V2: schema identity (staff_assignments se crea en V3, depende de catalog.events)

CREATE TABLE identity.users (
    id            UUID                NOT NULL DEFAULT gen_random_uuid(),
    email         extensions.citext   NOT NULL,
    password_hash TEXT                NOT NULL,
    full_name     TEXT                NOT NULL,
    phone         TEXT,
    status        TEXT                NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ         NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    version       INTEGER             NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT ck_users_status        CHECK (status IN ('ACTIVE','BLOCKED','PENDING_VERIFICATION')),
    CONSTRAINT ck_users_full_name_len CHECK (length(full_name) BETWEEN 1 AND 200)
);
-- Soft Delete (ADR-16): unicidad solo sobre cuentas vivas
CREATE UNIQUE INDEX uq_users_email_alive ON identity.users (email) WHERE deleted_at IS NULL;
COMMENT ON TABLE identity.users IS 'Cuentas. Soft Delete: GDPR se resuelve anonimizando, no borrando (trazabilidad de tickets/ledger).';

CREATE TABLE identity.roles (
    id   SMALLINT NOT NULL,
    code TEXT     NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_code UNIQUE (code),
    CONSTRAINT ck_roles_code CHECK (code IN ('ADMIN','ORGANIZER','STAFF','ATTENDEE'))
);
COMMENT ON TABLE identity.roles IS 'Catálogo cerrado (SMALLINT deliberado; se expone code, no id). Semilla en V9.';

CREATE TABLE identity.user_roles (
    user_id    UUID        NOT NULL,
    role_id    SMALLINT    NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by UUID,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user    FOREIGN KEY (user_id)    REFERENCES identity.users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role    FOREIGN KEY (role_id)    REFERENCES identity.roles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_roles_granter FOREIGN KEY (granted_by) REFERENCES identity.users (id) ON DELETE RESTRICT
);

CREATE TABLE identity.refresh_tokens (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    token_hash  TEXT        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID,
    device_info JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user     FOREIGN KEY (user_id)     REFERENCES identity.users (id)          ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_replaced FOREIGN KEY (replaced_by) REFERENCES identity.refresh_tokens (id) ON DELETE SET NULL
);
CREATE INDEX ix_refresh_tokens_user_active ON identity.refresh_tokens (user_id) WHERE revoked_at IS NULL;
COMMENT ON TABLE identity.refresh_tokens IS 'Rotación: replaced_by encadena tokens; reuso de un token rotado = señal de robo.';

CREATE TABLE identity.user_devices (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    fcm_token    TEXT        NOT NULL,
    platform     TEXT        NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_devices PRIMARY KEY (id),
    CONSTRAINT uq_user_devices_token UNIQUE (fcm_token),
    CONSTRAINT ck_user_devices_platform CHECK (platform IN ('ANDROID','IOS','WEB')),
    CONSTRAINT fk_user_devices_user FOREIGN KEY (user_id) REFERENCES identity.users (id) ON DELETE CASCADE
);
CREATE INDEX ix_user_devices_user ON identity.user_devices (user_id);
