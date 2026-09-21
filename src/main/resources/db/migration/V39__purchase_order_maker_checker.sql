ALTER TABLE purchase_order
    ADD COLUMN IF NOT EXISTS requested_by_id UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS rejected_by_id UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_purchase_order_requested_by
    ON purchase_order (requested_by_id);

CREATE INDEX IF NOT EXISTS idx_purchase_order_rejected_by
    ON purchase_order (rejected_by_id);
