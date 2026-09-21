-- Legacy V7 columns that no entity maps any more but that are still NOT NULL
-- without a default. Hibernate never writes them, so every insert into these
-- tables failed on PostgreSQL with a not-null violation (surfaced to users as a
-- misleading 409 "A record with this value already exists"): scheduling an
-- audit, and creating a security policy, ICS asset, compliance control or BoG
-- control. The H2 test profile builds its schema from the entities, so it never
-- saw these columns; ValidatesAgainstFlywaySchemaTest now checks for them.
--
-- Expand-only: the constraints are relaxed, nothing is dropped (V24 dropped
-- checkout_records.user_id for the same defect; here the data is kept for any
-- reader of the old columns).
ALTER TABLE asset_audit        ALTER COLUMN asset_id       DROP NOT NULL;
ALTER TABLE audit_item         ALTER COLUMN asset_audit_id DROP NOT NULL;
ALTER TABLE security_policy    ALTER COLUMN name           DROP NOT NULL;
ALTER TABLE ics_asset          ALTER COLUMN name           DROP NOT NULL;
ALTER TABLE compliance_control ALTER COLUMN framework_id   DROP NOT NULL;
ALTER TABLE compliance_control ALTER COLUMN title          DROP NOT NULL;
ALTER TABLE bog_control        ALTER COLUMN title          DROP NOT NULL;
