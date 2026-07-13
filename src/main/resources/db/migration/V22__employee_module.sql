-- =============================================================================
-- V22 — Employee module
--
-- Employee records (HR view of asset custodians — not every employee is a
-- system user), onboarding/offboarding checklists with asset issue/return
-- items, and an optional employee recipient on checkout_records.
-- Idempotent guards match the house style so re-runs against evolved dev
-- databases are safe.
-- =============================================================================

CREATE TABLE IF NOT EXISTS employee (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id    UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    employee_number    VARCHAR(100),
    first_name         VARCHAR(255) NOT NULL,
    last_name          VARCHAR(255) NOT NULL,
    email              VARCHAR(255),
    phone              VARCHAR(100),
    job_title          VARCHAR(255),
    department_id      UUID REFERENCES department(id) ON DELETE SET NULL,
    manager_id         UUID REFERENCES employee(id)   ON DELETE SET NULL,
    user_id            UUID REFERENCES app_user(id)   ON DELETE SET NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'ONBOARDING',
    hire_date          DATE,
    termination_date   DATE,
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ,
    deleted_at         TIMESTAMPTZ,
    created_by         VARCHAR(255),
    modified_by        VARCHAR(255)
);

-- Employee numbers are org-scoped; soft-deleted rows must not block reuse.
CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_org_number
    ON employee(organisation_id, employee_number)
    WHERE employee_number IS NOT NULL AND deleted_at IS NULL;

-- At most one active employee record per linked system user per org.
CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_org_user
    ON employee(organisation_id, user_id)
    WHERE user_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_employee_org        ON employee(organisation_id);
CREATE INDEX IF NOT EXISTS idx_employee_department ON employee(department_id);
CREATE INDEX IF NOT EXISTS idx_employee_status     ON employee(organisation_id, status);

CREATE TABLE IF NOT EXISTS employee_checklist (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id    UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    employee_id        UUID NOT NULL REFERENCES employee(id)     ON DELETE CASCADE,
    checklist_type     VARCHAR(20) NOT NULL,          -- ONBOARDING | OFFBOARDING
    status             VARCHAR(20) NOT NULL DEFAULT 'OPEN',  -- OPEN | COMPLETED
    completed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ,
    deleted_at         TIMESTAMPTZ,
    created_by         VARCHAR(255),
    modified_by        VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_employee_checklist_employee
    ON employee_checklist(employee_id);

CREATE TABLE IF NOT EXISTS employee_checklist_item (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checklist_id        UUID NOT NULL REFERENCES employee_checklist(id) ON DELETE CASCADE,
    title               VARCHAR(500) NOT NULL,
    item_type           VARCHAR(20) NOT NULL DEFAULT 'GENERAL',  -- GENERAL | ASSET_ISSUE | ASSET_RETURN
    asset_id            UUID REFERENCES asset(id)            ON DELETE SET NULL,
    checkout_record_id  UUID REFERENCES checkout_records(id) ON DELETE SET NULL,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    completed           BOOLEAN NOT NULL DEFAULT false,
    completed_by_id     UUID REFERENCES app_user(id) ON DELETE SET NULL,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    modified_by         VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_checklist_item_checklist
    ON employee_checklist_item(checklist_id);

-- Optional employee recipient on checkouts. checked_out_by_id (app_user) stays
-- NOT NULL as the acting custodian of record; employee_id is the person the
-- asset was issued to when they are not (or not only) a system user.
ALTER TABLE checkout_records
    ADD COLUMN IF NOT EXISTS employee_id UUID REFERENCES employee(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_checkout_records_employee
    ON checkout_records(employee_id)
    WHERE employee_id IS NOT NULL;
