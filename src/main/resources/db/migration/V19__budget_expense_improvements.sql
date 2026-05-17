-- V19: Budget committed-amount tracking + expense schema alignment
--
-- budget table:
--   + committed_amount  NUMERIC(15,2) NOT NULL DEFAULT 0  (in-flight approved spend)
--   + alert_threshold_pct INTEGER NOT NULL DEFAULT 80     (notify at this % utilized)
--   + last_adjustment_note TEXT                            (reason for manual /adjustment)
--   + description TEXT                                     (entity has it; baseline missed it)
--   rename allocated_amount → total_amount (if still exists)
--
-- expenses table (columns exist on dev via ddl-auto=update; prod needs explicit migration):
--   rename user_id          → submitted_by_id
--   rename approved_by_user_id → approved_by_id
--   rename budget_id        → linked_budget_id
--   rename asset_id         → linked_asset_id
--   + title VARCHAR(255)
--   + rejection_reason TEXT
--   + department_id UUID FK → department
--   + approved_at TIMESTAMPTZ

-- ── budget ────────────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='budget' AND column_name='allocated_amount') THEN
        ALTER TABLE budget RENAME COLUMN allocated_amount TO total_amount;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='budget' AND column_name='total_amount') THEN
        ALTER TABLE budget ADD COLUMN total_amount NUMERIC(15,2) NOT NULL DEFAULT 0;
    END IF;
END $$;

ALTER TABLE budget ADD COLUMN IF NOT EXISTS description          TEXT;
ALTER TABLE budget ADD COLUMN IF NOT EXISTS committed_amount     NUMERIC(15,2) NOT NULL DEFAULT 0;
ALTER TABLE budget ADD COLUMN IF NOT EXISTS alert_threshold_pct  INTEGER NOT NULL DEFAULT 80;
ALTER TABLE budget ADD COLUMN IF NOT EXISTS last_adjustment_note TEXT;

-- ── expenses — safe renames ───────────────────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='expenses' AND column_name='user_id') THEN
        ALTER TABLE expenses RENAME COLUMN user_id TO submitted_by_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='expenses' AND column_name='approved_by_user_id') THEN
        ALTER TABLE expenses RENAME COLUMN approved_by_user_id TO approved_by_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='expenses' AND column_name='budget_id') THEN
        ALTER TABLE expenses RENAME COLUMN budget_id TO linked_budget_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='expenses' AND column_name='asset_id') THEN
        ALTER TABLE expenses RENAME COLUMN asset_id TO linked_asset_id;
    END IF;
END $$;

ALTER TABLE expenses ADD COLUMN IF NOT EXISTS expense_date     DATE;
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS title            VARCHAR(255);
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS department_id    UUID REFERENCES department(id) ON DELETE SET NULL;
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS approved_at      TIMESTAMPTZ;

UPDATE expenses SET title = LEFT(description, 255) WHERE title IS NULL AND description IS NOT NULL;

-- ── Indexes ───────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_expenses_linked_budget ON expenses(linked_budget_id) WHERE linked_budget_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_expenses_status_org    ON expenses(organisation_id, status, deleted_at);
CREATE INDEX IF NOT EXISTS idx_expenses_submitted_by  ON expenses(submitted_by_id);
CREATE INDEX IF NOT EXISTS idx_expenses_expense_date  ON expenses(organisation_id, expense_date);
CREATE INDEX IF NOT EXISTS idx_budget_org_status      ON budget(organisation_id, status, deleted_at);
