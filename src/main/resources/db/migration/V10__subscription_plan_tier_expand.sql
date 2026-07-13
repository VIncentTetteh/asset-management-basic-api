-- V10__subscription_plan_tier_expand.sql
-- P1-3 follow-up: When BillingPlanTier was expanded from {FREEMIUM, BASIC,
-- PREMIUM} to the Ghana-first ladder {FREEMIUM, STARTER, GROWTH, BUSINESS,
-- ENTERPRISE, BASIC*, PREMIUM*} (* retained for backward-compat), the
-- pre-existing Hibernate-generated CHECK constraint on subscription_plan.tier
-- started rejecting inserts of the new values:
--
--   ERROR: new row for relation "subscription_plan" violates check constraint
--   "subscription_plan_tier_check"
--
-- This migration drops that stale constraint and re-creates it with the full
-- ladder, so BillingPlanSeeder can persist the Starter/Growth/Business/Enterprise
-- plans on boot.

-- ── Fresh-database guard ─────────────────────────────────────────────────────
-- The V7 baseline predates these columns (they were Hibernate-generated on
-- evolved databases), so ensure they exist before touching their constraints.
-- FlywayRepairConfig repairs checksums before migrate, so amending this
-- migration is safe for databases that already applied it.
ALTER TABLE subscription_plan
    ADD COLUMN IF NOT EXISTS tier     VARCHAR(30),
    ADD COLUMN IF NOT EXISTS interval VARCHAR(20);

-- ── subscription_plan.tier ────────────────────────────────────────────────────
ALTER TABLE subscription_plan
    DROP CONSTRAINT IF EXISTS subscription_plan_tier_check;

ALTER TABLE subscription_plan
    ADD CONSTRAINT subscription_plan_tier_check
    CHECK (tier IN (
        'FREEMIUM',
        'STARTER',
        'GROWTH',
        'BUSINESS',
        'ENTERPRISE',
        'BASIC',     -- legacy, @Deprecated
        'PREMIUM'    -- legacy, @Deprecated
    ));

-- ── subscription_plan.interval ────────────────────────────────────────────────
-- Same class of issue: the interval column is an @Enumerated(EnumType.STRING)
-- backed by SubscriptionInterval. We defensively rebuild the CHECK for it as
-- well so future enum additions don't block the BillingPlanSeeder. This is a
-- no-op on databases where the constraint doesn't exist.
ALTER TABLE subscription_plan
    DROP CONSTRAINT IF EXISTS subscription_plan_interval_check;

ALTER TABLE subscription_plan
    ADD CONSTRAINT subscription_plan_interval_check
    CHECK (interval IN ('MONTHLY', 'QUARTERLY', 'ANNUALLY', 'YEARLY', 'ONE_TIME'));

-- ── Safety: ensure seeded codes are unique ───────────────────────────────────
-- BillingPlanSeeder does an "upsert by code", so the code column should be
-- uniquely indexed. If a previous deploy forgot this index, add it now.
CREATE UNIQUE INDEX IF NOT EXISTS idx_subscription_plan_code
    ON subscription_plan (code)
    WHERE deleted_at IS NULL;
