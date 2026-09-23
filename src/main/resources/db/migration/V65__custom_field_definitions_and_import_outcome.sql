-- V65: two additions, both in service of an import that accepts a customer's real
-- spreadsheet instead of sending them back to Excel to fix it.
--
-- 1. custom_field_definition
--
-- Until now a custom field existed only as a row in asset_custom_field: a name and a
-- value hung off one asset, with nothing anywhere saying that the tenant has a field
-- called "Warranty Provider" at all. That is workable when a human types one in on one
-- asset. It is not workable when a spreadsheet column becomes a field across 3000 rows,
-- because there is then no place to record that the column existed, what shape its
-- values were, or that the tenant now has this field — and no way to show the tenant
-- what their import just added to their schema.
--
-- So the definition is its own row, per organisation, per entity type, deduplicated
-- case-insensitively on a normalised key. The per-asset values stay where they are;
-- this table is what makes them a field rather than a scattering of strings.
--
-- Creating one is a schema change for the tenant, which is why the import bounds how
-- many a single file may create and reports every one by name.
--
-- 2. asset_import_job result columns
--
-- The job row stored four integers and a blob of errors, which is how a result screen
-- ended up showing a green tick over "imported 0 rows" while listing more failures than
-- there were rows. The outcome is now recorded explicitly, the failure count is stored
-- separately from the count of rows that were merely not written, and the notes -- the
-- values the importer could not translate and left blank -- are kept alongside the
-- errors instead of being thrown away. Expand-only: every column is nullable or
-- defaulted, and nothing existing is altered or dropped.

CREATE TABLE IF NOT EXISTS custom_field_definition (
    id                UUID          PRIMARY KEY,
    -- ON DELETE CASCADE, like every other tenant-owned table: a purge under the DPA
    -- must not fail on a field definition.
    organisation_id   UUID          NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,

    -- Which record type the field belongs to, as an ImportEntityType name. Only ASSETS
    -- has storage for values today; the column is here so adding a second type is a
    -- handler change rather than a migration.
    entity_type       VARCHAR(40)   NOT NULL DEFAULT 'ASSETS',

    -- The name as the user wrote it, sanitised and length-capped, which is what the
    -- asset_custom_field rows are keyed by.
    field_name        VARCHAR(100)  NOT NULL,

    -- The comparison form: lower case, punctuation and whitespace removed. Two columns
    -- headed "Cost Centre" and "cost_centre" are one field, not two.
    field_key         VARCHAR(100)  NOT NULL,

    -- STRING, INTEGER, DECIMAL, DATE or BOOLEAN, inferred from the values sampled at
    -- import. Advisory: values are still stored as text, so a bad inference costs a
    -- rendering hint and never a row.
    data_type         VARCHAR(20)   NOT NULL DEFAULT 'STRING',

    -- IMPORT or MANUAL: where the definition came from, so a tenant reviewing their
    -- fields can tell which ones a spreadsheet introduced.
    source            VARCHAR(20)   NOT NULL DEFAULT 'IMPORT',

    created_at        TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ,
    deleted_at        TIMESTAMPTZ,
    created_by        VARCHAR(255),
    modified_by       VARCHAR(255)
);

-- One live definition per name per type per tenant. Partial, so a soft-deleted
-- definition does not block the same name being defined again.
CREATE UNIQUE INDEX IF NOT EXISTS uk_custom_field_definition_key
    ON custom_field_definition (organisation_id, entity_type, field_key)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_custom_field_definition_org
    ON custom_field_definition (organisation_id, entity_type);

ALTER TABLE asset_import_job
    ADD COLUMN IF NOT EXISTS failed_rows        INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS outcome            VARCHAR(30),
    ADD COLUMN IF NOT EXISTS notes_json         TEXT,
    ADD COLUMN IF NOT EXISTS created_json       TEXT,
    ADD COLUMN IF NOT EXISTS stopped_reason     VARCHAR(2000);
