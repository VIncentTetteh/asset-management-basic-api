-- Mobile Home "recently updated" list: the ten newest live assets of one
-- tenant. Without this the query sorts every live asset the tenant holds on
-- each Home load; with it the read stops after ten index entries. Matches the
-- query's ORDER BY updated_at DESC NULLS LAST. Additive only.
CREATE INDEX IF NOT EXISTS idx_asset_org_updated_at_desc
    ON asset (organisation_id, updated_at DESC NULLS LAST)
    WHERE deleted_at IS NULL;
