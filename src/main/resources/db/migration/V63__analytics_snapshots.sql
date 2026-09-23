-- V63: a nightly, per-tenant snapshot of the headline estate figures.
--
-- Every "trend" AssetIQ has shown so far is reconstructed: depreciation trends
-- recompute today's asset set backwards month by month, which makes an asset
-- bought three years ago appear in charts for months when it was not yet on the
-- register, and makes a disposal retroactively erase history. It also cannot
-- show the numbers that actually move — how many assets are idle, how much is
-- overdue, how many licence seats are in use — because none of those can be
-- reconstructed from the present state at all.
--
-- This table records what was true on a given day. It is additive only: rows
-- are written once by the nightly job and never updated, so a trend read from it
-- is history rather than an inference.
--
-- Money is stored in the tenant's base currency as of the snapshot date, with
-- the currency recorded alongside it and a flag saying whether every amount
-- could be converted. A tenant that changes base currency will show a step in
-- the series; that is honest, and the currency column lets the UI say so rather
-- than drawing a smooth line through two different units.

CREATE TABLE IF NOT EXISTS analytics_snapshot (
    id                        UUID         PRIMARY KEY,
    -- ON DELETE CASCADE, like every other tenant-owned table: a purge under the
    -- DPA must not fail on an analytics row, and snapshots are derived data with
    -- no independent value once the tenant is gone.
    organisation_id           UUID         NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    snapshot_date             DATE         NOT NULL,

    currency                  VARCHAR(3)   NOT NULL,
    -- FALSE when at least one amount was left out for want of an exchange rate.
    complete                  BOOLEAN      NOT NULL DEFAULT TRUE,

    asset_count               BIGINT       NOT NULL DEFAULT 0,
    active_asset_count        BIGINT       NOT NULL DEFAULT 0,
    idle_asset_count          BIGINT       NOT NULL DEFAULT 0,
    unassigned_in_use_count   BIGINT       NOT NULL DEFAULT 0,
    fully_depreciated_count   BIGINT       NOT NULL DEFAULT 0,

    total_cost                NUMERIC(19,2) NOT NULL DEFAULT 0,
    net_book_value            NUMERIC(19,2) NOT NULL DEFAULT 0,
    accumulated_depreciation  NUMERIC(19,2) NOT NULL DEFAULT 0,
    monthly_depreciation      NUMERIC(19,2) NOT NULL DEFAULT 0,

    overdue_maintenance_count BIGINT       NOT NULL DEFAULT 0,
    licence_seats_total       BIGINT       NOT NULL DEFAULT 0,
    licence_seats_used        BIGINT       NOT NULL DEFAULT 0,

    created_at                TIMESTAMPTZ,
    updated_at                TIMESTAMPTZ,
    deleted_at                TIMESTAMPTZ,
    created_by                VARCHAR(255),
    modified_by               VARCHAR(255)
);

-- One snapshot per tenant per day: a re-run replaces nothing and inserts nothing,
-- so a job that runs twice cannot double a series.
CREATE UNIQUE INDEX IF NOT EXISTS uq_analytics_snapshot_org_date
    ON analytics_snapshot (organisation_id, snapshot_date);

-- The read pattern is "last N days for one tenant, newest first".
CREATE INDEX IF NOT EXISTS idx_analytics_snapshot_org_date_desc
    ON analytics_snapshot (organisation_id, snapshot_date DESC);

-- Supporting indices for the aggregate queries the dashboards run. Each one
-- turns a tenant-wide sequential scan into a range scan; all are additive.
CREATE INDEX IF NOT EXISTS idx_asset_org_warranty_expiry
    ON asset (organisation_id, warranty_expiry_date)
    WHERE deleted_at IS NULL AND warranty_expiry_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_asset_org_insurance_expiry
    ON asset (organisation_id, insurance_policy_expiry)
    WHERE deleted_at IS NULL AND insurance_policy_expiry IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_maintenance_org_next_due
    ON maintenance_record (organisation_id, next_due_date)
    WHERE deleted_at IS NULL AND next_due_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_contract_org_end_date
    ON contract (organisation_id, end_date)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_software_license_org_expiry
    ON software_license (organisation_id, expiry_date)
    WHERE deleted_at IS NULL AND expiry_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lease_records_org_end_date
    ON lease_records (organisation_id, end_date)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_budget_org_period
    ON budget (organisation_id, period_start, period_end)
    WHERE deleted_at IS NULL;
