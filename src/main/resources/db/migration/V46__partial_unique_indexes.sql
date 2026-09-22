-- V46: natural keys are unique among live (not soft-deleted) rows.
--
-- Two problems, one fix:
--
-- 1. Several natural keys were declared only as JPA @UniqueConstraint. Production
--    runs ddl-auto=validate, which never creates constraints, so the database
--    accepted duplicate PO numbers, cloud resources, cost records, discovered
--    devices and compliance references.
-- 2. The keys the V7 baseline did create (asset tag / serial, supplier email /
--    tax id / registration number) counted soft-deleted rows, so a tag or email
--    could never be reused after a delete, and the failure read as a duplicate.
--
-- Each key becomes a partial unique index WHERE deleted_at IS NULL. Postgres treats
-- NULLs as distinct, so optional keys (serial number, supplier email, ...) stay
-- optional.
--
-- Pre-check: every index is preceded by a duplicate check that RAISEs with the
-- table and key when live duplicates exist. The migration then fails loudly and
-- rolls back (Flyway runs it in one transaction); it never deletes or rewrites
-- data. Resolve the duplicates by hand and redeploy. The same query, run
-- beforehand against a snapshot, tells you whether a deploy will pass:
--   SELECT organisation_id, po_number, count(*) FROM purchase_order
--    WHERE deleted_at IS NULL AND po_number IS NOT NULL GROUP BY 1, 2 HAVING count(*) > 1;
--
-- Replacing the V7 full constraints (asset, supplier): the old constraint is
-- dropped and its partial replacement created in this same transaction, so
-- uniqueness among live rows is never unenforced. Nothing is dropped from the
-- data, and every live row that satisfied the old constraint satisfies the new
-- index. This is the only non-additive step, and it only relaxes uniqueness for
-- soft-deleted rows.
--
-- Organisation name (case-insensitive, excluding closed tenants) is deliberately
-- not here: which tenants count as closed needs a product decision first.

CREATE OR REPLACE FUNCTION pg_temp.assert_no_live_duplicates(tbl text, cols text, extra_filter text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    dup_groups bigint;
BEGIN
    EXECUTE format(
        'SELECT count(*) FROM (SELECT 1 FROM %I WHERE deleted_at IS NULL %s GROUP BY %s HAVING count(*) > 1) d',
        tbl, COALESCE(extra_filter, ''), cols)
    INTO dup_groups;
    IF dup_groups > 0 THEN
        RAISE EXCEPTION 'V46: % live duplicate group(s) in %(%). Resolve them (no data is changed by this migration) and redeploy.',
            dup_groups, tbl, cols;
    END IF;
END;
$$;

-- ── Purchase orders: PO number per organisation ──────────────────────────────
SELECT pg_temp.assert_no_live_duplicates('purchase_order', 'organisation_id, po_number', 'AND po_number IS NOT NULL');
CREATE UNIQUE INDEX IF NOT EXISTS uq_purchase_order_org_number_live
    ON purchase_order (organisation_id, po_number)
    WHERE deleted_at IS NULL AND po_number IS NOT NULL;

-- ── Cloud assets: one row per provider resource per organisation ─────────────
SELECT pg_temp.assert_no_live_duplicates('cloud_asset', 'organisation_id, provider, resource_id', 'AND resource_id IS NOT NULL');
CREATE UNIQUE INDEX IF NOT EXISTS uq_cloud_asset_org_resource_live
    ON cloud_asset (organisation_id, provider, resource_id)
    WHERE deleted_at IS NULL AND resource_id IS NOT NULL;

-- ── Cloud cost records: one row per asset, month and service ─────────────────
SELECT pg_temp.assert_no_live_duplicates('cloud_cost_record', 'cloud_asset_id, billing_month, service_name',
    'AND cloud_asset_id IS NOT NULL AND billing_month IS NOT NULL AND service_name IS NOT NULL');
CREATE UNIQUE INDEX IF NOT EXISTS uq_cloud_cost_record_asset_month_service_live
    ON cloud_cost_record (cloud_asset_id, billing_month, service_name)
    WHERE deleted_at IS NULL;

-- ── Discovered devices: one row per IP address per organisation ──────────────
SELECT pg_temp.assert_no_live_duplicates('discovered_device', 'organisation_id, ip_address', 'AND ip_address IS NOT NULL');
CREATE UNIQUE INDEX IF NOT EXISTS uq_discovered_device_org_ip_live
    ON discovered_device (organisation_id, ip_address)
    WHERE deleted_at IS NULL AND ip_address IS NOT NULL;

-- ── Compliance natural keys (entity uniqueConstraints) ───────────────────────
SELECT pg_temp.assert_no_live_duplicates('bog_control', 'organisation_id, directive_ref', 'AND directive_ref IS NOT NULL');
CREATE UNIQUE INDEX IF NOT EXISTS uq_bog_control_org_ref_live
    ON bog_control (organisation_id, directive_ref)
    WHERE deleted_at IS NULL AND directive_ref IS NOT NULL;

SELECT pg_temp.assert_no_live_duplicates('pci_saq_record', 'organisation_id, requirement_number', 'AND requirement_number IS NOT NULL');
CREATE UNIQUE INDEX IF NOT EXISTS uq_pci_saq_org_requirement_live
    ON pci_saq_record (organisation_id, requirement_number)
    WHERE deleted_at IS NULL AND requirement_number IS NOT NULL;

SELECT pg_temp.assert_no_live_duplicates('sla_metric', 'organisation_id, year, month', 'AND year IS NOT NULL AND month IS NOT NULL');
CREATE UNIQUE INDEX IF NOT EXISTS uq_sla_metric_org_period_live
    ON sla_metric (organisation_id, year, month)
    WHERE deleted_at IS NULL AND year IS NOT NULL AND month IS NOT NULL;

-- ── Assets: tag and serial reusable after a soft delete ──────────────────────
SELECT pg_temp.assert_no_live_duplicates('asset', 'organisation_id, asset_tag', 'AND asset_tag IS NOT NULL');
SELECT pg_temp.assert_no_live_duplicates('asset', 'organisation_id, serial_number', 'AND serial_number IS NOT NULL');
ALTER TABLE asset DROP CONSTRAINT IF EXISTS uk_asset_tag_per_organisation;
DROP INDEX IF EXISTS uk_asset_tag_per_organisation;
ALTER TABLE asset DROP CONSTRAINT IF EXISTS uk_serial_number_per_organisation;
DROP INDEX IF EXISTS uk_serial_number_per_organisation;
CREATE UNIQUE INDEX IF NOT EXISTS uq_asset_org_tag_live
    ON asset (organisation_id, asset_tag)
    WHERE deleted_at IS NULL AND asset_tag IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_asset_org_serial_live
    ON asset (organisation_id, serial_number)
    WHERE deleted_at IS NULL AND serial_number IS NOT NULL;

-- ── Suppliers: email, tax id and registration number reusable after delete ───
SELECT pg_temp.assert_no_live_duplicates('supplier', 'organisation_id, email', 'AND email IS NOT NULL');
SELECT pg_temp.assert_no_live_duplicates('supplier', 'organisation_id, tax_id', 'AND tax_id IS NOT NULL');
SELECT pg_temp.assert_no_live_duplicates('supplier', 'organisation_id, registration_number', 'AND registration_number IS NOT NULL');
ALTER TABLE supplier DROP CONSTRAINT IF EXISTS uk_supplier_email_per_org;
DROP INDEX IF EXISTS uk_supplier_email_per_org;
ALTER TABLE supplier DROP CONSTRAINT IF EXISTS uk_supplier_taxid_per_org;
DROP INDEX IF EXISTS uk_supplier_taxid_per_org;
ALTER TABLE supplier DROP CONSTRAINT IF EXISTS uk_supplier_regnum_per_org;
DROP INDEX IF EXISTS uk_supplier_regnum_per_org;
CREATE UNIQUE INDEX IF NOT EXISTS uq_supplier_org_email_live
    ON supplier (organisation_id, email)
    WHERE deleted_at IS NULL AND email IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_supplier_org_tax_id_live
    ON supplier (organisation_id, tax_id)
    WHERE deleted_at IS NULL AND tax_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_supplier_org_regnum_live
    ON supplier (organisation_id, registration_number)
    WHERE deleted_at IS NULL AND registration_number IS NOT NULL;
