-- V21: Drop stale notification_type_check constraint.
-- The NotificationType Java enum is the authoritative list of valid types.
-- Keeping a duplicate DB-level constraint just causes breakage when new types are added.
ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_type_check;
