-- Closing a maintenance ticket used to guess where the asset should go back to:
-- IN_USE when it had an assigned user, otherwise IN_STOCK. An asset that is
-- checked out but has no assigned user therefore came back as IN_STOCK while it
-- was still in someone's hands. The status the asset held when the ticket opened
-- is now recorded on the ticket and restored when it closes.
--
-- Expand-only: one nullable column, no backfill. Tickets opened before this
-- migration have NULL and fall back to the old assigned-user / active-checkout
-- rule, so nothing breaks and no data is touched.
--
-- Pre-check (expects 0 rows; the column must not already exist with a different type):
--   SELECT data_type, character_maximum_length
--     FROM information_schema.columns
--    WHERE table_name = 'maintenance_record'
--      AND column_name = 'asset_status_before'
--      AND NOT (data_type = 'character varying' AND character_maximum_length = 30);
ALTER TABLE maintenance_record
    ADD COLUMN IF NOT EXISTS asset_status_before VARCHAR(30);
