-- Two-step (maker-checker) disposals.
--
-- A disposal used to take effect the moment it was recorded: the recorder was
-- stored as the "approver" and the asset flipped to DISPOSED, and the sale value
-- stayed editable afterwards. A disposal is now requested (PENDING_APPROVAL),
-- then approved by a different user, which is when the asset is disposed; the
-- sale value, currency, method and date are locked from then on.
--
-- Expand-only. Every existing row already disposed its asset, so it is marked
-- APPROVED and keeps its approver; the requester is unknown for those rows.
ALTER TABLE disposal_record
    ADD COLUMN IF NOT EXISTS status          VARCHAR(20),
    ADD COLUMN IF NOT EXISTS requested_by_id UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS approved_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejected_by_id  UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS rejected_at     TIMESTAMPTZ;

UPDATE disposal_record SET status = 'APPROVED' WHERE status IS NULL;

CREATE INDEX IF NOT EXISTS idx_disposal_record_status ON disposal_record (organisation_id, status);
