-- V18: pg_trgm indexes for ILIKE full-text search on assets
-- pg_trgm is bundled with standard PostgreSQL; on RDS requires rds_superuser.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- GIN trigram indexes — make ILIKE '%term%' an index scan instead of a table scan
CREATE INDEX IF NOT EXISTS idx_asset_name_trgm
    ON asset USING gin (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_asset_tag_trgm
    ON asset USING gin (asset_tag gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_asset_serial_trgm
    ON asset USING gin (serial_number gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_asset_manufacturer_trgm
    ON asset USING gin (manufacturer gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_asset_model_trgm
    ON asset USING gin (model gin_trgm_ops);

-- Composite B-tree indexes for the common filtered+paginated query paths
CREATE INDEX IF NOT EXISTS idx_asset_org_status_deleted
    ON asset (organisation_id, status, deleted_at);

CREATE INDEX IF NOT EXISTS idx_asset_org_dept_deleted
    ON asset (organisation_id, department_id, deleted_at);

CREATE INDEX IF NOT EXISTS idx_asset_org_cat_deleted
    ON asset (organisation_id, category_id, deleted_at);
