-- V59: a mapping-driven import for every record type, not just assets.
--
-- Three changes, all expand-only. Nothing is dropped, no column changes type, and
-- everything that reads these tables today keeps working.
--
-- 1. asset_import_job learns what it is importing.
--    The job machinery (queue, async worker, status polling, stuck-job recovery) is
--    reused rather than duplicated per entity type, so the row needs to say which
--    handler to run and with what mapping and options. Existing rows are backfilled
--    to 'ASSETS', which is what every job so far has been, and the column is left
--    nullable-with-default rather than NOT NULL so a rollback to the previous build
--    can still insert.
--
-- 2. import_staged_upload: files uploaded to the wizard but not yet committed.
--    Analyse, preview and commit are separate requests that may land on separate
--    instances, so the bytes go to the storage service and this table records where.
--    expires_at is load-bearing: StagedUploadCleanupScheduler deletes the stored
--    object and the row together once the window passes. A staging area that only
--    grows is a slow-motion outage.
--
-- 3. import_mapping_preset: named, per-organisation saved mappings.
--    Stored as field name -> column header text, never column index, so a preset
--    survives the source system adding a column.
--
-- Both new tables carry organisation_id NOT NULL with ON DELETE CASCADE, matching
-- every other tenant-owned table here, and every service read is scoped by it.
--
-- Pre-check before deploying: none required. The backfill touches only rows whose
-- entity_type is NULL, which is all of them, and sets a value they already meant.

ALTER TABLE asset_import_job ADD COLUMN IF NOT EXISTS entity_type  VARCHAR(40) DEFAULT 'ASSETS';
ALTER TABLE asset_import_job ADD COLUMN IF NOT EXISTS mapping_json TEXT;
ALTER TABLE asset_import_job ADD COLUMN IF NOT EXISTS options_json TEXT;
ALTER TABLE asset_import_job ADD COLUMN IF NOT EXISTS updated_rows INTEGER NOT NULL DEFAULT 0;

UPDATE asset_import_job SET entity_type = 'ASSETS' WHERE entity_type IS NULL;

CREATE TABLE IF NOT EXISTS import_staged_upload (
    id              UUID PRIMARY KEY,
    organisation_id UUID        NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    entity_type     VARCHAR(40) NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,
    filename        VARCHAR(300) NOT NULL,
    content_type    VARCHAR(200),
    row_count       INTEGER     NOT NULL DEFAULT 0,
    column_count    INTEGER     NOT NULL DEFAULT 0,
    columns_json    TEXT,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_import_staged_upload_org
    ON import_staged_upload(organisation_id);
CREATE INDEX IF NOT EXISTS idx_import_staged_upload_expires
    ON import_staged_upload(expires_at)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS import_mapping_preset (
    id              UUID PRIMARY KEY,
    organisation_id UUID        NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    entity_type     VARCHAR(40) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    mapping_json    TEXT        NOT NULL,
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    modified_by     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_import_mapping_preset_org_type
    ON import_mapping_preset(organisation_id, entity_type);

-- One live preset per (tenant, type, name), case-insensitively: saving over a preset
-- must replace it, not quietly create a second one with the same label. Partial over
-- live rows, like every other natural key here (V46).
CREATE UNIQUE INDEX IF NOT EXISTS uq_import_mapping_preset_live
    ON import_mapping_preset (organisation_id, entity_type, lower(name))
    WHERE deleted_at IS NULL;
