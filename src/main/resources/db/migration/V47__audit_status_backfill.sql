-- Audits saved before status was enforced can have a NULL status, which the web
-- app could not render. Treat them as PLANNED, the default for a new audit.
-- Expand-only: data backfill, no schema change.
UPDATE asset_audit SET status = 'PLANNED' WHERE status IS NULL;
