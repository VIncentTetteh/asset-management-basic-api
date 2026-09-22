-- A rejected purchase order records why, alongside who and when (V39), so the
-- requester learns the reason. Expand-only: a nullable column; existing
-- rejected orders keep no reason.
--
-- expected_delivery has existed since the V7 baseline but was never mapped; the
-- guard below only matters for a database that predates the baseline.
ALTER TABLE purchase_order
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE purchase_order
    ADD COLUMN IF NOT EXISTS expected_delivery DATE;
