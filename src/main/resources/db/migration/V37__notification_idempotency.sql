ALTER TABLE notification
    ADD COLUMN IF NOT EXISTS deduplication_key VARCHAR(255);

ALTER TABLE notification
    DROP CONSTRAINT IF EXISTS uq_notification_dedup;

ALTER TABLE notification
    ADD CONSTRAINT uq_notification_dedup
        UNIQUE (organisation_id, user_id, deduplication_key);

CREATE INDEX IF NOT EXISTS idx_notification_deduplication_key
    ON notification (deduplication_key)
    WHERE deduplication_key IS NOT NULL;
