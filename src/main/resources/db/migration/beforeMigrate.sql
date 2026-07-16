-- =============================================================================
-- Flyway beforeMigrate callback
--
-- Creates the four core tables that V1-V6 migrations depend on (via FK
-- references or ALTER TABLE statements) before any versioned migration runs.
--
-- WHY THIS EXISTS:
--   V1 references organisation(id) via FK.
--   V3 alters the role table.
--   V4 references role(id) via FK.
--   V5 references app_user(id) and role(id) via FK.
--   V6 alters the audit_event table.
--   All four tables are created by V7 — but V7 runs AFTER V1-V6, so on a
--   fresh database those migrations would fail.
--
-- This callback runs BEFORE any versioned migration, ensuring the prerequisite
-- tables always exist.  Every statement uses CREATE TABLE IF NOT EXISTS so it
-- is completely idempotent and safe against pre-existing schemas.
-- =============================================================================

-- ── ORGANISATION ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS organisation (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  VARCHAR(255) NOT NULL,
    registration_number   VARCHAR(255),
    tax_id                VARCHAR(255),
    industry              VARCHAR(255),
    country               VARCHAR(255),
    address               TEXT,
    contact_email         VARCHAR(255),
    contact_phone         VARCHAR(100),
    timezone              VARCHAR(100),
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    billing_currency      CHAR(3)      NOT NULL DEFAULT 'GHS',
    dpo_name              VARCHAR(255),
    dpo_email             VARCHAR(255),
    data_residency_region VARCHAR(10)  NOT NULL DEFAULT 'GH',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,
    deleted_at            TIMESTAMPTZ,
    created_by            VARCHAR(255),
    modified_by           VARCHAR(255)
);

-- ── ROLE ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS role (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     UUID        REFERENCES organisation(id) ON DELETE CASCADE,
    name                VARCHAR(255),
    description         TEXT,
    system_role         BOOLEAN     NOT NULL DEFAULT false,
    grant_all_permissions BOOLEAN   NOT NULL DEFAULT false,
    is_system_role      BOOLEAN     NOT NULL DEFAULT false,
    permissions         TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    modified_by         VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_role_org ON role(organisation_id);

-- ── APP_USER ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app_user (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id             UUID        REFERENCES organisation(id) ON DELETE CASCADE,
    email                       VARCHAR(255) NOT NULL,
    password_hash               VARCHAR(255),
    first_name                  VARCHAR(255) NOT NULL DEFAULT '',
    last_name                   VARCHAR(255) NOT NULL DEFAULT '',
    phone                       VARCHAR(100),
    employee_id                 VARCHAR(100),
    job_title                   VARCHAR(255),
    status                      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    email_verified              BOOLEAN      NOT NULL DEFAULT false,
    last_login_at               TIMESTAMPTZ,
    reset_password_token        VARCHAR(100),
    reset_password_token_expiry TIMESTAMPTZ,
    reset_password_token_used   BOOLEAN      NOT NULL DEFAULT false,
    mfa_enabled                 BOOLEAN      NOT NULL DEFAULT false,
    mfa_secret                  VARCHAR(100),
    role_id                     UUID         REFERENCES role(id) ON DELETE SET NULL,
    department_id               UUID,
    failed_login_attempts       INTEGER      NOT NULL DEFAULT 0,
    locked_until                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ,
    deleted_at                  TIMESTAMPTZ,
    created_by                  VARCHAR(255),
    modified_by                 VARCHAR(255),
    CONSTRAINT uk_user_email_per_org      UNIQUE (email, organisation_id),
    CONSTRAINT uk_user_employeeid_per_org UNIQUE (employee_id, organisation_id)
);

CREATE INDEX IF NOT EXISTS idx_app_user_org ON app_user(organisation_id);

-- ── AUDIT_EVENT ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_event (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID        REFERENCES organisation(id) ON DELETE CASCADE,
    actor_id        UUID,
    actor_email     VARCHAR(255),
    method          VARCHAR(10),
    path            TEXT,
    query           TEXT,
    handler         VARCHAR(255),
    response_status INTEGER,
    success         BOOLEAN,
    message         TEXT,
    request_id      VARCHAR(255),
    client_ip       VARCHAR(100),
    user_agent      TEXT,
    response_time_ms BIGINT,
    event_type      VARCHAR(50)  NOT NULL DEFAULT 'API_REQUEST',
    target_id       VARCHAR(100),
    old_value       VARCHAR(1000),
    new_value       VARCHAR(1000),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_audit_event_org_pre ON audit_event(organisation_id);
