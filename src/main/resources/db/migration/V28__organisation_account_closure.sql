-- V28__organisation_account_closure.sql
--
-- Adds tenant-initiated account closure, which the product had no way to do.
-- There was no delete-account endpoint and no data-export endpoint anywhere in
-- the backend, so the only way to leave was to email support and hope. That is
-- an awkward position for any SaaS and a particularly bad one for AssetIQ, which
-- sells Ghana DPA Act 843 and GDPR compliance — including a DSAR register with
-- ERASURE and PORTABILITY request types it could not honour for its own tenants.
--
-- Closure is a two-step process rather than an immediate wipe:
--   1. The tenant confirms closure. deleted_at is set (access stops at once) and
--      purge_after is stamped 30 days out.
--   2. AccountPurgeJob hard-deletes the organisation once purge_after passes.
--
-- The window exists because accidental and malicious deletions are both real, and
-- neither is recoverable once the rows are gone. Every organisation_id foreign key
-- in the schema is ON DELETE CASCADE, so the eventual purge is a single DELETE on
-- organisation and the database removes the rest — there is no hand-maintained
-- ordered teardown to drift out of date as tables are added.

ALTER TABLE organisation
    ADD COLUMN IF NOT EXISTS purge_after TIMESTAMP;

COMMENT ON COLUMN organisation.purge_after IS
    'When a closed account becomes eligible for permanent deletion. NULL for live accounts.';

-- The purge job scans for due accounts daily; keep it off a sequential scan.
CREATE INDEX IF NOT EXISTS idx_organisation_purge_after
    ON organisation (purge_after)
    WHERE purge_after IS NOT NULL;
