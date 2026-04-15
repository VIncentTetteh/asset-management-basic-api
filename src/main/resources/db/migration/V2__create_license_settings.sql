-- License settings table (standalone mode only).
-- Single-row table (id always = 1) that persists:
--   - The active license key token
--   - The last successful remote validation timestamp (for grace period tracking)
--   - A cached copy of the last License Server response (for debugging)
--
-- In cloud mode this table is created but never written to.

CREATE TABLE IF NOT EXISTS license_settings (
    id                          BIGINT       PRIMARY KEY DEFAULT 1,
    key_token                   TEXT,
    last_remote_validation_at   TIMESTAMP WITH TIME ZONE,
    remote_validation_cache     TEXT,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT license_settings_single_row CHECK (id = 1)
);

COMMENT ON TABLE  license_settings                        IS 'Single-row table holding standalone license key and remote validation state.';
COMMENT ON COLUMN license_settings.key_token              IS 'RSA-signed JWT license key entered by the admin.';
COMMENT ON COLUMN license_settings.last_remote_validation_at IS 'Timestamp of last successful call to the License Server — used for grace period tracking.';
COMMENT ON COLUMN license_settings.remote_validation_cache   IS 'Raw JSON response body from last License Server call — for debugging.';
