-- =============================================================================
-- V13: Schema corrections
--
-- 1. Rename five tables whose names in the V7 baseline (singular) do not
--    match the JPA @Table annotations on the corresponding entities (plural).
--    Hibernate ddl-auto=validate will throw on startup until these match.
--
-- 2. Add account-lockout columns to app_user for brute-force protection.
--
-- 3. Ensure beforeMigrate-created columns exist on pre-existing databases
--    that didn't get them from the callback (i.e., databases that had their
--    schema created before this callback was introduced).
-- =============================================================================

-- ── 1. TABLE RENAMES (singular → plural) ──────────────────────────────────────
-- Each rename is guarded so it only executes if the old name exists, making
-- this migration re-runnable against databases that may already have the
-- corrected names (e.g., from beforeMigrate.sql on a fresh install).

DO $$
BEGIN
    -- checkout_record → checkout_records
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'checkout_record') THEN
        ALTER TABLE checkout_record RENAME TO checkout_records;
    END IF;

    -- exchange_rate → exchange_rates
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'exchange_rate') THEN
        ALTER TABLE exchange_rate RENAME TO exchange_rates;
    END IF;

    -- expense → expenses
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'expense') THEN
        ALTER TABLE expense RENAME TO expenses;
    END IF;

    -- lease_record → lease_records
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'lease_record') THEN
        ALTER TABLE lease_record RENAME TO lease_records;
    END IF;

    -- qr_revision → qr_revisions
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'qr_revision') THEN
        ALTER TABLE qr_revision RENAME TO qr_revisions;
    END IF;
END $$;

-- ── 2. ACCOUNT-LOCKOUT COLUMNS on app_user ───────────────────────────────────
-- Tracks consecutive failed login attempts and a temporary lockout expiry.
-- Added here so existing databases get the columns even if beforeMigrate.sql
-- already handled fresh installs.

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until          TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_app_user_locked_until
    ON app_user(locked_until)
    WHERE locked_until IS NOT NULL;

-- ── 3. DPA columns on organisation (belt-and-suspenders) ─────────────────────
-- V11 added these; beforeMigrate also adds them. Guard with IF NOT EXISTS so
-- old DBs that ran V11 but not beforeMigrate don't fail.
ALTER TABLE organisation
    ADD COLUMN IF NOT EXISTS dpo_name              VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dpo_email             VARCHAR(255),
    ADD COLUMN IF NOT EXISTS data_residency_region VARCHAR(10) NOT NULL DEFAULT 'GH';

-- ── 4. AUDIT-EVENT columns (belt-and-suspenders for V6 columns) ───────────────
-- V6 added these via ALTER; the beforeMigrate callback also declares them.
-- Idempotent guards ensure no double-add errors.
ALTER TABLE audit_event
    ADD COLUMN IF NOT EXISTS actor_email      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS actor_id         UUID,
    ADD COLUMN IF NOT EXISTS handler          VARCHAR(255),
    ADD COLUMN IF NOT EXISTS response_status  INTEGER,
    ADD COLUMN IF NOT EXISTS success          BOOLEAN,
    ADD COLUMN IF NOT EXISTS message          TEXT,
    ADD COLUMN IF NOT EXISTS request_id       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS client_ip        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS user_agent       TEXT,
    ADD COLUMN IF NOT EXISTS response_time_ms BIGINT,
    ADD COLUMN IF NOT EXISTS event_type       VARCHAR(50) NOT NULL DEFAULT 'API_REQUEST',
    ADD COLUMN IF NOT EXISTS target_id        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS old_value        VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS new_value        VARCHAR(1000);
