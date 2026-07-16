-- V12: Add processed_at timestamp to idempotency_record for TTL-based cleanup.
ALTER TABLE idempotency_record
    ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_idempotency_processed_at ON idempotency_record(processed_at)
    WHERE processed_at IS NOT NULL;
