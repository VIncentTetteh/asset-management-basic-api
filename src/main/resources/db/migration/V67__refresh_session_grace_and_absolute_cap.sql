-- Rotating refresh sessions: a short replay grace window, and an absolute
-- family cap alongside the existing idle timeout.
--
-- Expand-only: three nullable columns and one index. Nothing is dropped or
-- rewritten, so a rollback to the previous image keeps working — it simply
-- ignores the new columns and behaves exactly as it does today.
--
-- replacement_envelope holds the replacement refresh token encrypted under a
-- key derived from the *presented raw token*, which the server never stores.
-- The server therefore cannot reconstruct a refresh token from the database
-- alone: only a caller that already holds the consumed token can unseal it,
-- and that caller could have rotated it anyway. See RefreshSessionService.

ALTER TABLE refresh_session ADD COLUMN IF NOT EXISTS consumed_at TIMESTAMPTZ;
ALTER TABLE refresh_session ADD COLUMN IF NOT EXISTS replacement_envelope TEXT;
ALTER TABLE refresh_session ADD COLUMN IF NOT EXISTS family_expires_at TIMESTAMPTZ;

-- Rows written before this migration were consumed by a rotation exactly when
-- they recorded a replacement; their revoked_at is that moment.
UPDATE refresh_session
   SET consumed_at = revoked_at
 WHERE replaced_by_token_hash IS NOT NULL
   AND consumed_at IS NULL;

-- Existing families keep the cap they already effectively had: their own
-- expiry. No live session is extended by the migration itself.
UPDATE refresh_session
   SET family_expires_at = expires_at
 WHERE family_expires_at IS NULL;

-- The grace path looks the replacement up by hash; that column is already
-- unique-indexed. This one serves the sweep of consumable rows per family.
CREATE INDEX IF NOT EXISTS idx_refresh_session_family_consumed
    ON refresh_session (family_id, consumed_at);
