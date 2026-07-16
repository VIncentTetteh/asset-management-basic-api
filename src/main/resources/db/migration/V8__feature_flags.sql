-- V8__feature_flags.sql
-- P0-8: Feature-flag registry.
--
-- Enables dark-launching work across every later phase without branching
-- deploys. Flags have a global default and optional per-organisation overrides,
-- so we can roll out e.g. "ghana-momo-checkout" to pilot tenants first.

CREATE TABLE IF NOT EXISTS feature_flag (
    id               UUID        PRIMARY KEY,
    flag_key         VARCHAR(120) NOT NULL UNIQUE,
    description      VARCHAR(500),
    enabled_globally BOOLEAN      NOT NULL DEFAULT FALSE,
    rollout_percentage SMALLINT   NOT NULL DEFAULT 0
        CHECK (rollout_percentage BETWEEN 0 AND 100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_feature_flag_key ON feature_flag(flag_key);

-- Per-tenant overrides. NULL in `enabled` means "inherit the global default".
CREATE TABLE IF NOT EXISTS feature_flag_organisation (
    id               UUID        PRIMARY KEY,
    feature_flag_id  UUID        NOT NULL REFERENCES feature_flag(id) ON DELETE CASCADE,
    organisation_id  UUID        NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    enabled          BOOLEAN     NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_flag_org UNIQUE (feature_flag_id, organisation_id)
);

CREATE INDEX IF NOT EXISTS idx_ffo_org_flag
    ON feature_flag_organisation(organisation_id, feature_flag_id);

-- Seed the flags Phase 1+ will be gated by. All OFF by default. Toggle
-- via admin UI (Phase 1) or directly via UPDATE in the meantime.
INSERT INTO feature_flag (id, flag_key, description, enabled_globally)
VALUES
    (gen_random_uuid(), 'billing.ghs-default-currency',
     'Route new organisations to GHS pricing by default.', FALSE),
    (gen_random_uuid(), 'billing.paystack-momo-channels',
     'Enable Paystack mobile-money / USSD channels on checkout.', FALSE),
    (gen_random_uuid(), 'compliance.ghana-dpa-consent',
     'Surface Ghana Data Protection Act consent prompts + DSAR flows.', FALSE),
    (gen_random_uuid(), 'audit.anti-tamper-hash-chain',
     'Hash-chain every audit_event row (P3-4).', FALSE),
    (gen_random_uuid(), 'ops.read-only-maintenance-mode',
     'Return 503 on all mutating endpoints — used during upgrades.', FALSE)
ON CONFLICT (flag_key) DO NOTHING;
