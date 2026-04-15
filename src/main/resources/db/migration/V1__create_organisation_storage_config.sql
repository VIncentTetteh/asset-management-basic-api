-- ============================================================
-- V1: organisation_storage_config
--
-- Per-organisation S3 storage settings.
--
-- Strategy: prefix-based isolation (Option C).
-- The application uses its own IAM role — no credentials
-- are stored here.  Isolation is enforced by scoping every
-- S3 object key under {prefix}/{orgId}/…
-- ============================================================

CREATE TABLE IF NOT EXISTS organisation_storage_config (
    id                  UUID         NOT NULL PRIMARY KEY,

    -- Owning organisation (1:1)
    organisation_id     UUID         NOT NULL UNIQUE
                            REFERENCES organisation(id) ON DELETE CASCADE,

    -- Master toggle — when false the org uses in-memory storage
    s3_enabled          BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Optional bucket override; when NULL the global app bucket is used
    bucket_name         VARCHAR(255),

    -- Object-key prefixes
    report_prefix       VARCHAR(200) NOT NULL DEFAULT 'reports',
    import_prefix       VARCHAR(200) NOT NULL DEFAULT 'imports',

    -- Presigned URL TTL in minutes (1–10080 = 7 days)
    presign_minutes     INTEGER      NOT NULL DEFAULT 15
                            CHECK (presign_minutes BETWEEN 1 AND 10080),

    -- Audit columns (managed by Spring Data / Hibernate)
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMP WITH TIME ZONE,
    created_by          VARCHAR(255),
    modified_by         VARCHAR(255)
);

-- Fast lookup by org ID
CREATE INDEX IF NOT EXISTS idx_org_storage_active
    ON organisation_storage_config (organisation_id);

COMMENT ON TABLE  organisation_storage_config              IS 'Per-organisation S3 storage settings (prefix-based isolation — no credentials stored)';
COMMENT ON COLUMN organisation_storage_config.s3_enabled   IS 'When false, org falls back to in-memory storage';
COMMENT ON COLUMN organisation_storage_config.bucket_name  IS 'Optional bucket override; NULL means use app.storage.s3.bucket';
