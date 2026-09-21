-- Who completed an asset transfer (moved the asset into the destination
-- department). Approval already records approved_by_id; completion is a separate,
-- fresh-MFA action and was not attributed. Nullable, no backfill: transfers
-- completed before this migration have no recorded completer.
ALTER TABLE asset_transfer
    ADD COLUMN IF NOT EXISTS completed_by_id UUID REFERENCES app_user(id);

CREATE INDEX IF NOT EXISTS idx_asset_transfer_completed_by
    ON asset_transfer (completed_by_id);
