-- A rejected (or withdrawn) disposal records why, alongside who and when (V44).
-- Expand-only: a nullable column; existing rejected rows keep no reason.
ALTER TABLE disposal_record
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
