-- V7: AssetIQ full schema baseline
-- Idempotent CREATE TABLE IF NOT EXISTS — safe to re-run against an existing schema.
-- Generated as part of QA finding CR-01 remediation.

SET statement_timeout = 0;
SET client_encoding = 'UTF8';

-- ============================================================
-- CORE TENANT
-- ============================================================

CREATE TABLE IF NOT EXISTS organisation (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(255) NOT NULL UNIQUE,
    registration_number VARCHAR(255) UNIQUE,
    tax_id              VARCHAR(255) UNIQUE,
    industry            VARCHAR(255),
    country             VARCHAR(255),
    address             TEXT,
    contact_email       VARCHAR(255) UNIQUE,
    contact_phone       VARCHAR(100),
    timezone            VARCHAR(100),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    billing_currency    CHAR(3)      NOT NULL DEFAULT 'GHS',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    modified_by         VARCHAR(255)
);

-- ============================================================
-- USERS & ROLES
-- ============================================================

CREATE TABLE IF NOT EXISTS role (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id  UUID REFERENCES organisation(id) ON DELETE CASCADE,
    name             VARCHAR(255),
    description      TEXT,
    is_system_role   BOOLEAN NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    modified_by      VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_role_org ON role(organisation_id);

CREATE TABLE IF NOT EXISTS role_permission (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id     UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission  VARCHAR(100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ,
    created_by  VARCHAR(255),
    modified_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_role_permission_role ON role_permission(role_id);

CREATE TABLE IF NOT EXISTS app_user (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id             UUID REFERENCES organisation(id) ON DELETE CASCADE,
    email                       VARCHAR(255) NOT NULL,
    password_hash               VARCHAR(255),
    first_name                  VARCHAR(255) NOT NULL,
    last_name                   VARCHAR(255) NOT NULL,
    phone                       VARCHAR(100),
    employee_id                 VARCHAR(100),
    job_title                   VARCHAR(255),
    status                      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    email_verified              BOOLEAN NOT NULL DEFAULT false,
    last_login_at               TIMESTAMPTZ,
    reset_password_token        VARCHAR(100),
    reset_password_token_expiry TIMESTAMPTZ,
    reset_password_token_used   BOOLEAN NOT NULL DEFAULT false,
    mfa_enabled                 BOOLEAN NOT NULL DEFAULT false,
    mfa_secret                  VARCHAR(100),
    role_id                     UUID REFERENCES role(id) ON DELETE SET NULL,
    department_id               UUID,   -- FK added after department table
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ,
    deleted_at                  TIMESTAMPTZ,
    created_by                  VARCHAR(255),
    modified_by                 VARCHAR(255),
    CONSTRAINT uk_user_email_per_org      UNIQUE (email, organisation_id),
    CONSTRAINT uk_user_employeeid_per_org UNIQUE (employee_id, organisation_id)
);

CREATE INDEX IF NOT EXISTS idx_app_user_org ON app_user(organisation_id);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES role(id)     ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS user_role_composition (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES role(id)     ON DELETE CASCADE,
    UNIQUE (user_id, role_id)
);

-- ============================================================
-- ORGANISATIONAL STRUCTURE
-- ============================================================

CREATE TABLE IF NOT EXISTS department (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id      UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name                 VARCHAR(255) NOT NULL,
    description          TEXT,
    department_code      VARCHAR(100),
    cost_center_code     VARCHAR(100),
    budget_limit         NUMERIC(15,2),
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    parent_department_id UUID REFERENCES department(id) ON DELETE SET NULL,
    manager_id           UUID REFERENCES app_user(id)  ON DELETE SET NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ,
    deleted_at           TIMESTAMPTZ,
    created_by           VARCHAR(255),
    modified_by          VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_department_org ON department(organisation_id);

-- Now add the FK from app_user.department_id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'app_user'
          AND constraint_name = 'fk_app_user_department'
    ) THEN
        ALTER TABLE app_user
            ADD CONSTRAINT fk_app_user_department
            FOREIGN KEY (department_id) REFERENCES department(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS location (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id  UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    address          TEXT,
    city             VARCHAR(255),
    country          VARCHAR(255),
    building         VARCHAR(255),
    floor            VARCHAR(100),
    room             VARCHAR(100),
    geo_coordinates  TEXT,
    latitude         DOUBLE PRECISION,
    longitude        DOUBLE PRECISION,
    type             VARCHAR(50),
    parent_location_id UUID REFERENCES location(id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    modified_by      VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_location_org ON location(organisation_id);

CREATE TABLE IF NOT EXISTS category (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id              UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name                         VARCHAR(255) NOT NULL,
    description                  TEXT,
    asset_prefix_code            VARCHAR(50),
    default_warranty_period_months INT,
    parent_category_id           UUID REFERENCES category(id) ON DELETE SET NULL,
    depreciation_policy_id       UUID,   -- FK added after depreciation_policy table
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ,
    deleted_at                   TIMESTAMPTZ,
    created_by                   VARCHAR(255),
    modified_by                  VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_category_org ON category(organisation_id);

CREATE TABLE IF NOT EXISTS supplier (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name                VARCHAR(255) NOT NULL,
    registration_number VARCHAR(255),
    contact_person      VARCHAR(255),
    contact_name        VARCHAR(255),
    email               VARCHAR(255),
    phone               VARCHAR(100),
    address             TEXT,
    website             VARCHAR(500),
    bank_details        TEXT,
    tax_id              VARCHAR(255),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    modified_by         VARCHAR(255),
    CONSTRAINT uk_supplier_regnum_per_org  UNIQUE (registration_number, organisation_id),
    CONSTRAINT uk_supplier_email_per_org   UNIQUE (email, organisation_id),
    CONSTRAINT uk_supplier_taxid_per_org   UNIQUE (tax_id, organisation_id)
);

CREATE INDEX IF NOT EXISTS idx_supplier_org ON supplier(organisation_id);

-- ============================================================
-- DEPRECIATION
-- ============================================================

CREATE TABLE IF NOT EXISTS depreciation_policy (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id      UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name                 VARCHAR(255) NOT NULL,
    description          TEXT,
    method               VARCHAR(30)  NOT NULL,
    rate                 NUMERIC(8,4),
    useful_life_months   INT,
    salvage_value_pct    NUMERIC(5,2),
    salvage_value_percent NUMERIC(5,2),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ,
    deleted_at           TIMESTAMPTZ,
    created_by           VARCHAR(255),
    modified_by          VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_depreciation_policy_org ON depreciation_policy(organisation_id);

-- Now add FK from category.depreciation_policy_id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'category'
          AND constraint_name = 'fk_category_depreciation_policy'
    ) THEN
        ALTER TABLE category
            ADD CONSTRAINT fk_category_depreciation_policy
            FOREIGN KEY (depreciation_policy_id) REFERENCES depreciation_policy(id) ON DELETE SET NULL;
    END IF;
END $$;

-- ============================================================
-- ASSETS
-- ============================================================

CREATE TABLE IF NOT EXISTS asset (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id          UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name                     VARCHAR(255) NOT NULL,
    asset_tag                VARCHAR(100),
    serial_number            VARCHAR(255),
    barcode_qr_code          VARCHAR(500),
    description              TEXT,
    status                   VARCHAR(30),
    asset_type               VARCHAR(30),
    manufacturer             VARCHAR(255),
    model                    VARCHAR(255),
    category_id              UUID REFERENCES category(id)     ON DELETE SET NULL,
    location_id              UUID REFERENCES location(id)     ON DELETE SET NULL,
    department_id            UUID REFERENCES department(id)   ON DELETE SET NULL,
    assigned_user_id         UUID REFERENCES app_user(id)     ON DELETE SET NULL,
    supplier_id              UUID REFERENCES supplier(id)     ON DELETE SET NULL,
    purchase_date            DATE,
    purchase_cost            NUMERIC(15,2),
    purchase_price           NUMERIC(15,2),
    currency                 CHAR(3) NOT NULL DEFAULT 'GHS',
    current_book_value       NUMERIC(15,2),
    current_value            NUMERIC(15,2),
    residual_value           NUMERIC(15,2),
    warranty_expiry_date     DATE,
    useful_life_months       INT,
    depreciation_method      VARCHAR(30),
    depreciation_policy_id   UUID REFERENCES depreciation_policy(id) ON DELETE SET NULL,
    condition                VARCHAR(20),
    cost_center              VARCHAR(100),
    procurement_type         VARCHAR(10),
    invoice_id               VARCHAR(255),
    insurance_policy_id      VARCHAR(255),
    insurance_policy_expiry  DATE,
    insurance_premium_per_year NUMERIC(15,2),
    downtime_cost_per_day    NUMERIC(15,2),
    last_scanned_at          TIMESTAMPTZ,
    last_scanned_by_id       UUID REFERENCES app_user(id)     ON DELETE SET NULL,
    parent_asset_id          UUID REFERENCES asset(id)        ON DELETE SET NULL,
    purchase_order_id        UUID,   -- FK added after purchase_order table
    notes                    TEXT,
    image_url                VARCHAR(500),
    qr_code                  VARCHAR(500),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ,
    deleted_at               TIMESTAMPTZ,
    created_by               VARCHAR(255),
    modified_by              VARCHAR(255),
    CONSTRAINT uk_asset_tag_per_organisation    UNIQUE (asset_tag, organisation_id),
    CONSTRAINT uk_serial_number_per_organisation UNIQUE (serial_number, organisation_id)
);

CREATE INDEX IF NOT EXISTS idx_asset_org ON asset(organisation_id);
CREATE INDEX IF NOT EXISTS idx_asset_status ON asset(status);
CREATE INDEX IF NOT EXISTS idx_asset_category ON asset(category_id);
CREATE INDEX IF NOT EXISTS idx_asset_department ON asset(department_id);

-- ============================================================
-- ASSET SUPPORT TABLES
-- ============================================================

CREATE TABLE IF NOT EXISTS asset_custom_field (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id    UUID NOT NULL REFERENCES asset(id) ON DELETE CASCADE,
    field_name  VARCHAR(100),
    field_value TEXT,
    field_type  VARCHAR(30),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ,
    created_by  VARCHAR(255),
    modified_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_asset_custom_field_asset ON asset_custom_field(asset_id);

CREATE TABLE IF NOT EXISTS asset_transfer (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id             UUID NOT NULL REFERENCES asset(id)      ON DELETE CASCADE,
    organisation_id      UUID REFERENCES organisation(id)        ON DELETE CASCADE,
    from_user_id         UUID REFERENCES app_user(id)            ON DELETE SET NULL,
    to_user_id           UUID REFERENCES app_user(id)            ON DELETE SET NULL,
    from_department_id   UUID REFERENCES department(id)          ON DELETE SET NULL,
    to_department_id     UUID REFERENCES department(id)          ON DELETE SET NULL,
    from_location_id     UUID REFERENCES location(id)            ON DELETE SET NULL,
    to_location_id       UUID REFERENCES location(id)            ON DELETE SET NULL,
    transfer_date        DATE,
    reason               TEXT,
    status               VARCHAR(30),
    requested_by_user_id UUID REFERENCES app_user(id)            ON DELETE SET NULL,
    approved_by_user_id  UUID REFERENCES app_user(id)            ON DELETE SET NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ,
    deleted_at           TIMESTAMPTZ,
    created_by           VARCHAR(255),
    modified_by          VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_asset_transfer_asset ON asset_transfer(asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_transfer_org   ON asset_transfer(organisation_id);

CREATE TABLE IF NOT EXISTS maintenance_record (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id       UUID NOT NULL REFERENCES asset(id) ON DELETE CASCADE,
    scheduled_date DATE,
    completed_date DATE,
    type           VARCHAR(50),
    description    TEXT,
    cost           NUMERIC(15,2),
    performed_by   VARCHAR(255),
    vendor         VARCHAR(255),
    status         VARCHAR(30),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ,
    created_by     VARCHAR(255),
    modified_by    VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_maintenance_record_asset ON maintenance_record(asset_id);

CREATE TABLE IF NOT EXISTS disposal_record (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id             UUID NOT NULL REFERENCES asset(id)  ON DELETE CASCADE,
    disposal_date        DATE,
    reason               TEXT,
    method               VARCHAR(50),
    proceeds             NUMERIC(15,2),
    approved_by_user_id  UUID REFERENCES app_user(id)        ON DELETE SET NULL,
    notes                TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ,
    deleted_at           TIMESTAMPTZ,
    created_by           VARCHAR(255),
    modified_by          VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_disposal_record_asset ON disposal_record(asset_id);

CREATE TABLE IF NOT EXISTS checkout_record (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id       UUID NOT NULL REFERENCES asset(id)    ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    checked_out_at TIMESTAMPTZ,
    due_date       DATE,
    returned_at    TIMESTAMPTZ,
    notes          TEXT,
    status         VARCHAR(30),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ,
    created_by     VARCHAR(255),
    modified_by    VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_checkout_record_asset ON checkout_record(asset_id);
CREATE INDEX IF NOT EXISTS idx_checkout_record_user  ON checkout_record(user_id);

CREATE TABLE IF NOT EXISTS lease_record (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id        UUID NOT NULL REFERENCES asset(id) ON DELETE CASCADE,
    lessee_name     VARCHAR(255),
    start_date      DATE,
    end_date        DATE,
    monthly_payment NUMERIC(15,2),
    currency        CHAR(3),
    status          VARCHAR(30),
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_lease_record_asset ON lease_record(asset_id);

-- ============================================================
-- PURCHASING
-- ============================================================

CREATE TABLE IF NOT EXISTS purchase_order (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    po_number           VARCHAR(100),
    supplier_id         UUID REFERENCES supplier(id)    ON DELETE SET NULL,
    status              VARCHAR(30),
    order_date          DATE,
    expected_delivery   DATE,
    total_amount        NUMERIC(15,2),
    currency            CHAR(3),
    notes               TEXT,
    approved_by_user_id UUID REFERENCES app_user(id)   ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    modified_by         VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_purchase_order_org ON purchase_order(organisation_id);

-- Now add FK from asset.purchase_order_id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'asset'
          AND constraint_name = 'fk_asset_purchase_order'
    ) THEN
        ALTER TABLE asset
            ADD CONSTRAINT fk_asset_purchase_order
            FOREIGN KEY (purchase_order_id) REFERENCES purchase_order(id) ON DELETE SET NULL;
    END IF;
END $$;

-- ============================================================
-- FINANCIAL
-- ============================================================

CREATE TABLE IF NOT EXISTS budget (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id  UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name             VARCHAR(255),
    fiscal_year      INT,
    department_id    UUID REFERENCES department(id) ON DELETE SET NULL,
    category         VARCHAR(100),
    allocated_amount NUMERIC(15,2),
    spent_amount     NUMERIC(15,2) NOT NULL DEFAULT 0,
    currency         CHAR(3),
    period_start     DATE,
    period_end       DATE,
    status           VARCHAR(30),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    modified_by      VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_budget_org ON budget(organisation_id);

CREATE TABLE IF NOT EXISTS expense (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    asset_id            UUID REFERENCES asset(id)        ON DELETE SET NULL,
    user_id             UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    description         TEXT,
    amount              NUMERIC(15,2),
    currency            CHAR(3),
    expense_date        DATE,
    category            VARCHAR(100),
    receipt_url         VARCHAR(500),
    status              VARCHAR(30),
    approved_by_user_id UUID REFERENCES app_user(id)    ON DELETE SET NULL,
    budget_id           UUID REFERENCES budget(id)       ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    modified_by         VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_expense_org ON expense(organisation_id);

-- ============================================================
-- SOFTWARE & CONTRACTS
-- ============================================================

CREATE TABLE IF NOT EXISTS software_license (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name                VARCHAR(255) NOT NULL,
    vendor              VARCHAR(255) NOT NULL,
    license_key         VARCHAR(500),
    product_name        VARCHAR(255),
    version             VARCHAR(100),
    license_type        VARCHAR(50),
    seat_count          INT,
    seats_used          INT NOT NULL DEFAULT 0,
    total_seats         INT,
    used_seats          INT NOT NULL DEFAULT 0,
    purchase_date       DATE,
    expiry_date         DATE,
    renewal_date        DATE,
    purchase_cost       NUMERIC(15,2),
    annual_renewal_cost NUMERIC(15,2),
    cost                NUMERIC(15,2),
    currency            CHAR(3) NOT NULL DEFAULT 'GHS',
    support_end_date    DATE,
    status              VARCHAR(30),
    auto_renew          BOOLEAN NOT NULL DEFAULT false,
    license_document_url VARCHAR(500),
    notes               TEXT,
    asset_id            UUID REFERENCES asset(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    modified_by         VARCHAR(255),
    CONSTRAINT uk_license_key_per_org UNIQUE (license_key, organisation_id)
);

CREATE INDEX IF NOT EXISTS idx_software_license_org ON software_license(organisation_id);

CREATE TABLE IF NOT EXISTS contract (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id      UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    title                VARCHAR(255) NOT NULL,
    vendor               VARCHAR(255),
    contract_number      VARCHAR(100),
    type                 VARCHAR(50),
    start_date           DATE,
    end_date             DATE,
    value                NUMERIC(15,2),
    currency             CHAR(3),
    status               VARCHAR(30),
    auto_renew           BOOLEAN NOT NULL DEFAULT false,
    renewal_notice_days  INT,
    document_url         VARCHAR(500),
    notes                TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ,
    deleted_at           TIMESTAMPTZ,
    created_by           VARCHAR(255),
    modified_by          VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_contract_org ON contract(organisation_id);

-- ============================================================
-- NOTIFICATIONS
-- ============================================================

CREATE TABLE IF NOT EXISTS notification (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES app_user(id)     ON DELETE CASCADE,
    type            VARCHAR(50),
    title           VARCHAR(255),
    message         TEXT,
    read            BOOLEAN NOT NULL DEFAULT false,
    read_at         TIMESTAMPTZ,
    action_url      VARCHAR(500),
    entity_id       UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_notification_org         ON notification(organisation_id);
CREATE INDEX IF NOT EXISTS idx_notification_user_org    ON notification(user_id, organisation_id);
CREATE INDEX IF NOT EXISTS idx_notification_read        ON notification(read, deleted_at);

CREATE TABLE IF NOT EXISTS notification_preferences (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE UNIQUE,
    email_enabled        BOOLEAN NOT NULL DEFAULT true,
    email_deprecation    BOOLEAN NOT NULL DEFAULT true,
    email_maintenance    BOOLEAN NOT NULL DEFAULT true,
    email_approval       BOOLEAN NOT NULL DEFAULT true,
    email_system         BOOLEAN NOT NULL DEFAULT false,
    email_transfer       BOOLEAN NOT NULL DEFAULT true,
    email_disposal       BOOLEAN NOT NULL DEFAULT true,
    email_purchase_order BOOLEAN NOT NULL DEFAULT true,
    in_app_enabled       BOOLEAN NOT NULL DEFAULT true,
    in_app_notifications BOOLEAN NOT NULL DEFAULT true,
    push_enabled         BOOLEAN NOT NULL DEFAULT false,
    push_notifications   BOOLEAN NOT NULL DEFAULT true,
    daily_digest         BOOLEAN NOT NULL DEFAULT true,
    digest_time          VARCHAR(5),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ,
    deleted_at           TIMESTAMPTZ,
    created_by           VARCHAR(255),
    modified_by          VARCHAR(255),
    CONSTRAINT uk_notif_prefs_user UNIQUE (user_id)
);

-- ============================================================
-- AUDIT & EVENTS
-- ============================================================

CREATE TABLE IF NOT EXISTS audit_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID REFERENCES organisation(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES app_user(id)     ON DELETE SET NULL,
    action          VARCHAR(100),
    entity_type     VARCHAR(100),
    entity_id       UUID,
    old_value       TEXT,
    new_value       TEXT,
    ip_address      VARCHAR(50),
    user_agent      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_audit_event_org ON audit_event(organisation_id);

CREATE TABLE IF NOT EXISTS asset_audit (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id             UUID NOT NULL REFERENCES asset(id)    ON DELETE CASCADE,
    performed_by_user_id UUID REFERENCES app_user(id)          ON DELETE SET NULL,
    audit_date           DATE,
    status               VARCHAR(30),
    notes                TEXT,
    discrepancy_notes    TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ,
    deleted_at           TIMESTAMPTZ,
    created_by           VARCHAR(255),
    modified_by          VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_asset_audit_asset ON asset_audit(asset_id);

CREATE TABLE IF NOT EXISTS audit_item (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_audit_id UUID NOT NULL REFERENCES asset_audit(id) ON DELETE CASCADE,
    asset_id       UUID NOT NULL REFERENCES asset(id)       ON DELETE CASCADE,
    found          BOOLEAN NOT NULL DEFAULT false,
    condition      VARCHAR(50),
    notes          TEXT,
    expected_location VARCHAR(255),
    actual_location   VARCHAR(255),
    discrepancy_flag  BOOLEAN NOT NULL DEFAULT false,
    remarks        TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_item_audit ON audit_item(asset_audit_id);

-- ============================================================
-- REPORTS & IMPORTS
-- ============================================================

CREATE TABLE IF NOT EXISTS report_metadata (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES app_user(id) ON DELETE SET NULL,
    name            VARCHAR(255),
    report_type     VARCHAR(80),
    type            VARCHAR(50),
    status          VARCHAR(30),
    format          VARCHAR(20),
    filename        VARCHAR(120),
    content_type    VARCHAR(200),
    storage_key     VARCHAR(500),
    file_url        VARCHAR(500),
    file_size       BIGINT,
    parameters      TEXT,
    row_count       INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_report_metadata_org         ON report_metadata(organisation_id);
CREATE INDEX IF NOT EXISTS idx_report_metadata_org_created ON report_metadata(organisation_id, created_at);

CREATE TABLE IF NOT EXISTS asset_import_job (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES app_user(id) ON DELETE SET NULL,
    file_url        VARCHAR(500),
    storage_key     VARCHAR(500),
    filename        VARCHAR(300),
    content_type    VARCHAR(200),
    status          VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    dry_run         BOOLEAN NOT NULL DEFAULT false,
    total_rows      INT NOT NULL DEFAULT 0,
    processed_rows  INT,
    imported        INT NOT NULL DEFAULT 0,
    failed_rows     INT,
    skipped         INT NOT NULL DEFAULT 0,
    errors          TEXT,
    errors_json     TEXT,
    error_summary   VARCHAR(2000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_asset_import_job_status     ON asset_import_job(status);
CREATE INDEX IF NOT EXISTS idx_asset_import_job_org_status ON asset_import_job(organisation_id, status);

-- ============================================================
-- WEBHOOKS
-- ============================================================

CREATE TABLE IF NOT EXISTS webhook (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    url             VARCHAR(500) NOT NULL,
    events          TEXT,
    secret          VARCHAR(255),
    active          BOOLEAN NOT NULL DEFAULT true,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_webhook_org ON webhook(organisation_id);

CREATE TABLE IF NOT EXISTS webhook_delivery (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id      UUID NOT NULL REFERENCES webhook(id) ON DELETE CASCADE,
    event_type      VARCHAR(100),
    payload         TEXT,
    response_code   INT,
    response_body   TEXT,
    delivered_at    TIMESTAMPTZ,
    success         BOOLEAN,
    retry_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_webhook_delivery_webhook ON webhook_delivery(webhook_id);

-- ============================================================
-- IDEMPOTENCY
-- ============================================================

CREATE TABLE IF NOT EXISTS idempotency_record (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id  UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    operation        VARCHAR(120) NOT NULL,
    idempotency_key  VARCHAR(220) NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,
    response_job_id  UUID,
    response_json    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    modified_by      VARCHAR(255),
    CONSTRAINT uk_idempotency_org_op_key UNIQUE (organisation_id, operation, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_org_op ON idempotency_record(organisation_id, operation);

-- ============================================================
-- QR REVISIONS
-- ============================================================

CREATE TABLE IF NOT EXISTS qr_revision (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id              UUID NOT NULL REFERENCES asset(id)    ON DELETE CASCADE,
    qr_code               VARCHAR(500),
    generated_at          TIMESTAMPTZ,
    generated_by_user_id  UUID REFERENCES app_user(id)          ON DELETE SET NULL,
    reason                TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,
    deleted_at            TIMESTAMPTZ,
    created_by            VARCHAR(255),
    modified_by           VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_qr_revision_asset ON qr_revision(asset_id);

-- ============================================================
-- FEATURE FLAGS
-- ============================================================

CREATE TABLE IF NOT EXISTS feature_flag (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_key            VARCHAR(120) NOT NULL UNIQUE,
    description         VARCHAR(500),
    enabled_globally    BOOLEAN NOT NULL DEFAULT false,
    rollout_percentage  INT NOT NULL DEFAULT 100,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    modified_by         VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_feature_flag_key ON feature_flag(flag_key);

CREATE TABLE IF NOT EXISTS feature_flag_organisation (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_flag_id  UUID NOT NULL REFERENCES feature_flag(id)   ON DELETE CASCADE,
    organisation_id  UUID NOT NULL REFERENCES organisation(id)    ON DELETE CASCADE,
    enabled          BOOLEAN,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    modified_by      VARCHAR(255),
    UNIQUE (feature_flag_id, organisation_id)
);

CREATE INDEX IF NOT EXISTS idx_feature_flag_org ON feature_flag_organisation(organisation_id);

-- ============================================================
-- STORAGE CONFIG
-- ============================================================

CREATE TABLE IF NOT EXISTS organisation_storage_config (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE UNIQUE,
    provider        VARCHAR(30),
    bucket_name     VARCHAR(255),
    region          VARCHAR(100),
    access_key      VARCHAR(255),
    secret_key      TEXT,
    endpoint        VARCHAR(500),
    path_prefix     VARCHAR(255),
    report_prefix   VARCHAR(200) NOT NULL DEFAULT 'reports',
    import_prefix   VARCHAR(200) NOT NULL DEFAULT 'imports',
    presign_minutes INT NOT NULL DEFAULT 15,
    s3_enabled      BOOLEAN NOT NULL DEFAULT false,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_org_storage_org_id ON organisation_storage_config(organisation_id);

-- ============================================================
-- BILLING & SUBSCRIPTIONS
-- ============================================================

CREATE TABLE IF NOT EXISTS subscription_plan (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(100) NOT NULL UNIQUE,
    name           VARCHAR(255),
    description    TEXT,
    monthly_price  NUMERIC(15,2),
    annual_price   NUMERIC(15,2),
    currency       CHAR(3),
    max_assets     INT,
    max_users      INT,
    features       TEXT,
    active         BOOLEAN NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ,
    created_by     VARCHAR(255),
    modified_by    VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS organisation_subscription (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id             UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE UNIQUE,
    plan_id                     VARCHAR(100),
    paystack_customer_code      VARCHAR(255),
    paystack_subscription_code  VARCHAR(255),
    status                      VARCHAR(30),
    current_period_start        TIMESTAMPTZ,
    current_period_end          TIMESTAMPTZ,
    seat_count                  INT,
    billing_email               VARCHAR(255),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ,
    deleted_at                  TIMESTAMPTZ,
    created_by                  VARCHAR(255),
    modified_by                 VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_org_subscription_org ON organisation_subscription(organisation_id);

CREATE TABLE IF NOT EXISTS billing_payment (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id          UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    amount                   NUMERIC(15,2),
    currency                 CHAR(3),
    paystack_reference       VARCHAR(255) UNIQUE,
    paystack_transaction_id  VARCHAR(255),
    status                   VARCHAR(30),
    payment_date             TIMESTAMPTZ,
    plan_id                  VARCHAR(100),
    description              TEXT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ,
    deleted_at               TIMESTAMPTZ,
    created_by               VARCHAR(255),
    modified_by              VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_billing_payment_org ON billing_payment(organisation_id);

-- ============================================================
-- EXCHANGE RATES
-- ============================================================

CREATE TABLE IF NOT EXISTS exchange_rate (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    base_currency   CHAR(3),
    target_currency CHAR(3),
    rate            NUMERIC(20,8),
    source          VARCHAR(100),
    fetched_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255),
    UNIQUE (base_currency, target_currency, fetched_at)
);

-- ============================================================
-- CLOUD ASSETS
-- ============================================================

CREATE TABLE IF NOT EXISTS cloud_asset (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    provider        VARCHAR(30),
    resource_id     VARCHAR(500),
    resource_type   VARCHAR(100),
    region          VARCHAR(100),
    name            VARCHAR(255),
    status          VARCHAR(30),
    tags            TEXT,
    monthly_cost    NUMERIC(15,2),
    currency        CHAR(3),
    last_seen_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_cloud_asset_org ON cloud_asset(organisation_id);

CREATE TABLE IF NOT EXISTS cloud_cost_record (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id  UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    cloud_asset_id   UUID REFERENCES cloud_asset(id)           ON DELETE SET NULL,
    provider         VARCHAR(30),
    service          VARCHAR(100),
    cost             NUMERIC(15,2),
    currency         CHAR(3),
    period_start     DATE,
    period_end       DATE,
    tags             TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    modified_by      VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_cloud_cost_record_org ON cloud_cost_record(organisation_id);

-- ============================================================
-- VENDOR PERFORMANCE
-- ============================================================

CREATE TABLE IF NOT EXISTS vendor_performance_review (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id  UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    supplier_id      UUID NOT NULL REFERENCES supplier(id)     ON DELETE CASCADE,
    reviewer_user_id UUID REFERENCES app_user(id)              ON DELETE SET NULL,
    review_date      DATE,
    score            INT,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    modified_by      VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_vendor_performance_review_org ON vendor_performance_review(organisation_id);

-- ============================================================
-- SSO CONFIG
-- ============================================================

CREATE TABLE IF NOT EXISTS org_sso_config (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id  UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE UNIQUE,
    provider         VARCHAR(30),
    client_id        VARCHAR(255),
    client_secret    TEXT,
    metadata_url     VARCHAR(500),
    enabled          BOOLEAN NOT NULL DEFAULT false,
    auto_provision   BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    modified_by      VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_org_sso_config_org ON org_sso_config(organisation_id);

-- ============================================================
-- PREDICTIVE INSIGHTS
-- ============================================================

CREATE TABLE IF NOT EXISTS predictive_insight (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    asset_id        UUID REFERENCES asset(id)                 ON DELETE SET NULL,
    insight_type    VARCHAR(100),
    description     TEXT,
    confidence      NUMERIC(5,4),
    recommendation  TEXT,
    resolved        BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_predictive_insight_org ON predictive_insight(organisation_id);

-- ============================================================
-- RISK & SECURITY
-- ============================================================

CREATE TABLE IF NOT EXISTS risk_register (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    category        VARCHAR(100),
    likelihood      INT,
    impact          INT,
    risk_score      INT,
    owner_user_id   UUID REFERENCES app_user(id)              ON DELETE SET NULL,
    status          VARCHAR(30),
    mitigation      TEXT,
    review_date     DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_risk_register_org ON risk_register(organisation_id);

CREATE TABLE IF NOT EXISTS security_incident (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id       UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    title                 VARCHAR(255) NOT NULL,
    description           TEXT,
    severity              VARCHAR(30),
    status                VARCHAR(30),
    reported_by_user_id   UUID REFERENCES app_user(id)              ON DELETE SET NULL,
    assigned_to_user_id   UUID REFERENCES app_user(id)              ON DELETE SET NULL,
    occurred_at           TIMESTAMPTZ,
    resolved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,
    deleted_at            TIMESTAMPTZ,
    created_by            VARCHAR(255),
    modified_by           VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_security_incident_org ON security_incident(organisation_id);

CREATE TABLE IF NOT EXISTS security_policy (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id  UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    type             VARCHAR(50),
    content          TEXT,
    version          VARCHAR(30),
    effective_date   DATE,
    review_date      DATE,
    status           VARCHAR(30),
    owner_user_id    UUID REFERENCES app_user(id)              ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    modified_by      VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_security_policy_org ON security_policy(organisation_id);

CREATE TABLE IF NOT EXISTS security_zone (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    risk_level      VARCHAR(20),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_security_zone_org ON security_zone(organisation_id);

-- ============================================================
-- ICS / OT ASSETS
-- ============================================================

CREATE TABLE IF NOT EXISTS ics_asset (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id   UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name              VARCHAR(255) NOT NULL,
    type              VARCHAR(50),
    protocol          VARCHAR(50),
    ip_address        VARCHAR(50),
    mac_address       VARCHAR(50),
    firmware_version  VARCHAR(100),
    location_id       UUID REFERENCES location(id)              ON DELETE SET NULL,
    security_zone_id  UUID REFERENCES security_zone(id)         ON DELETE SET NULL,
    last_seen_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,
    deleted_at        TIMESTAMPTZ,
    created_by        VARCHAR(255),
    modified_by       VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_ics_asset_org ON ics_asset(organisation_id);

CREATE TABLE IF NOT EXISTS patch_record (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id             UUID REFERENCES asset(id)     ON DELETE SET NULL,
    ics_asset_id         UUID REFERENCES ics_asset(id) ON DELETE SET NULL,
    patch_id             VARCHAR(100),
    title                VARCHAR(255),
    severity             VARCHAR(30),
    status               VARCHAR(30),
    applied_at           TIMESTAMPTZ,
    applied_by_user_id   UUID REFERENCES app_user(id)  ON DELETE SET NULL,
    notes                TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ,
    deleted_at           TIMESTAMPTZ,
    created_by           VARCHAR(255),
    modified_by          VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS vulnerability_scan (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    scan_date       TIMESTAMPTZ,
    tool            VARCHAR(100),
    summary         TEXT,
    critical_count  INT NOT NULL DEFAULT 0,
    high_count      INT NOT NULL DEFAULT 0,
    medium_count    INT NOT NULL DEFAULT 0,
    low_count       INT NOT NULL DEFAULT 0,
    report_url      VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_vulnerability_scan_org ON vulnerability_scan(organisation_id);

-- ============================================================
-- COMPLIANCE
-- ============================================================

CREATE TABLE IF NOT EXISTS compliance_framework (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    version         VARCHAR(50),
    description     TEXT,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_compliance_framework_org ON compliance_framework(organisation_id);

CREATE TABLE IF NOT EXISTS compliance_control (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    framework_id UUID NOT NULL REFERENCES compliance_framework(id) ON DELETE CASCADE,
    control_id   VARCHAR(100),
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    category     VARCHAR(100),
    requirement  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,
    deleted_at   TIMESTAMPTZ,
    created_by   VARCHAR(255),
    modified_by  VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_compliance_control_framework ON compliance_control(framework_id);

CREATE TABLE IF NOT EXISTS control_status (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id       UUID NOT NULL REFERENCES organisation(id)       ON DELETE CASCADE,
    compliance_control_id UUID NOT NULL REFERENCES compliance_control(id) ON DELETE CASCADE,
    status                VARCHAR(30),
    evidence              TEXT,
    assessed_at           TIMESTAMPTZ,
    assessed_by_user_id   UUID REFERENCES app_user(id)                    ON DELETE SET NULL,
    next_review           DATE,
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,
    deleted_at            TIMESTAMPTZ,
    created_by            VARCHAR(255),
    modified_by           VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_control_status_org ON control_status(organisation_id);

CREATE TABLE IF NOT EXISTS bog_control (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id   UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    control_reference VARCHAR(100),
    title             VARCHAR(255) NOT NULL,
    description       TEXT,
    status            VARCHAR(30),
    evidence          TEXT,
    assessed_at       TIMESTAMPTZ,
    next_review       DATE,
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,
    deleted_at        TIMESTAMPTZ,
    created_by        VARCHAR(255),
    modified_by       VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_bog_control_org ON bog_control(organisation_id);

CREATE TABLE IF NOT EXISTS pci_saq_record (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id       UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    saq_type              VARCHAR(30),
    submitted_at          TIMESTAMPTZ,
    submitted_by_user_id  UUID REFERENCES app_user(id)              ON DELETE SET NULL,
    version               VARCHAR(20),
    compliant             BOOLEAN,
    notes                 TEXT,
    document_url          VARCHAR(500),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,
    deleted_at            TIMESTAMPTZ,
    created_by            VARCHAR(255),
    modified_by           VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_pci_saq_record_org ON pci_saq_record(organisation_id);

CREATE TABLE IF NOT EXISTS regulatory_filing (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id       UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    filing_type           VARCHAR(100),
    submitted_at          TIMESTAMPTZ,
    submitted_by_user_id  UUID REFERENCES app_user(id)              ON DELETE SET NULL,
    period_start          DATE,
    period_end            DATE,
    status                VARCHAR(30),
    notes                 TEXT,
    document_url          VARCHAR(500),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,
    deleted_at            TIMESTAMPTZ,
    created_by            VARCHAR(255),
    modified_by           VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_regulatory_filing_org ON regulatory_filing(organisation_id);

CREATE TABLE IF NOT EXISTS sla_metric (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    metric_name     VARCHAR(100),
    target_value    NUMERIC(10,4),
    actual_value    NUMERIC(10,4),
    unit            VARCHAR(50),
    period_start    DATE,
    period_end      DATE,
    met             BOOLEAN,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_sla_metric_org ON sla_metric(organisation_id);

-- ============================================================
-- NETWORK DISCOVERY
-- ============================================================

CREATE TABLE IF NOT EXISTS discovered_device (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    ip_address      VARCHAR(50),
    mac_address     VARCHAR(50),
    hostname        VARCHAR(255),
    vendor          VARCHAR(255),
    device_type     VARCHAR(100),
    first_seen      TIMESTAMPTZ,
    last_seen       TIMESTAMPTZ,
    status          VARCHAR(30),
    asset_id        UUID REFERENCES asset(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_discovered_device_org ON discovered_device(organisation_id);
