-- V15: Alter webhook_delivery.response_body to TEXT
-- Author: GitHub Copilot
-- Date: 2026-04-30
-- Purpose: Ensure webhook_delivery.response_body can store large response payloads

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'webhook_delivery' AND column_name = 'response_body'
    ) THEN
        ALTER TABLE webhook_delivery ALTER COLUMN response_body TYPE TEXT;
    END IF;
END $$;

