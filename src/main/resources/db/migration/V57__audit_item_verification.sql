-- V57: make audit_item a real count sheet.
--
-- audit_item existed in the schema and as an orphaned entity, but nothing ever
-- wrote to it: there was no way to generate the sheet, no way to record that an
-- asset had been sighted, and no way to say what was wrong with one. The web
-- client showed a "Verified" seal on an audit that was backed by nothing at all.
--
-- Expand-only: new nullable columns plus one backfill of the new status column
-- from the discrepancy flag that was already there. No column is dropped, no
-- type is changed, and nothing that reads the table today stops working.
--
-- Pre-check before deploying. Two queries:
--
-- 1. The partial unique index below (one live item per asset per audit). Live
--    duplicates must be resolved by hand first — the migration does not delete
--    or rewrite data:
--      SELECT audit_id, asset_id, count(*)
--        FROM audit_item
--       WHERE deleted_at IS NULL AND audit_id IS NOT NULL
--       GROUP BY 1, 2 HAVING count(*) > 1;
--
-- 2. Rows the organisation backfill cannot reach, because they predate audit_id
--    (V31) and only carry the legacy asset_audit_id. They stay organisation-less
--    and are therefore invisible to every tenant-scoped query, which is the safe
--    outcome — but it is worth knowing how many there are:
--      SELECT count(*) FROM audit_item WHERE audit_id IS NULL;

ALTER TABLE audit_item ADD COLUMN IF NOT EXISTS organisation_id    UUID REFERENCES organisation(id) ON DELETE CASCADE;
ALTER TABLE audit_item ADD COLUMN IF NOT EXISTS status             VARCHAR(20);
ALTER TABLE audit_item ADD COLUMN IF NOT EXISTS discrepancy_type   VARCHAR(30);
ALTER TABLE audit_item ADD COLUMN IF NOT EXISTS discrepancy_reason TEXT;
ALTER TABLE audit_item ADD COLUMN IF NOT EXISTS verified_at        TIMESTAMPTZ;
ALTER TABLE audit_item ADD COLUMN IF NOT EXISTS verified_by_id     UUID REFERENCES app_user(id) ON DELETE SET NULL;
ALTER TABLE audit_item ADD COLUMN IF NOT EXISTS updated_at         TIMESTAMPTZ;
ALTER TABLE audit_item ADD COLUMN IF NOT EXISTS deleted_at         TIMESTAMPTZ;
ALTER TABLE audit_item ADD COLUMN IF NOT EXISTS created_by         VARCHAR(255);
ALTER TABLE audit_item ADD COLUMN IF NOT EXISTS modified_by        VARCHAR(255);

-- Existing rows get the organisation of the audit they belong to, so they are
-- visible to their own tenant and to nobody else.
UPDATE audit_item i
   SET organisation_id = a.organisation_id
  FROM asset_audit a
 WHERE i.organisation_id IS NULL
   AND a.id = COALESCE(i.audit_id, i.asset_audit_id);

-- A row that was already flagged reads as a discrepancy; everything else is
-- still to be counted. Nothing is invented: the flag is the only signal the old
-- rows carry.
UPDATE audit_item
   SET status = CASE WHEN discrepancy_flag THEN 'DISCREPANCY' ELSE 'PENDING' END
 WHERE status IS NULL;

CREATE INDEX IF NOT EXISTS idx_audit_item_audit_id ON audit_item(audit_id);
CREATE INDEX IF NOT EXISTS idx_audit_item_org      ON audit_item(organisation_id);

-- One live item per asset per audit: generating the sheet twice must top it up,
-- not double it. Partial over live rows, like every other natural key here (V46).
CREATE UNIQUE INDEX IF NOT EXISTS uq_audit_item_audit_asset_live
    ON audit_item (audit_id, asset_id)
    WHERE deleted_at IS NULL AND audit_id IS NOT NULL;
