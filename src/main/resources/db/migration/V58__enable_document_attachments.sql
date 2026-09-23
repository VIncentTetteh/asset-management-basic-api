-- V58__enable_document_attachments.sql
--
-- Turns 'commercial.document-attachments' ON globally, superseding the
-- containment V34__contain_unscanned_attachments.sql put in place.
--
-- V34 forced the flag to enabled_globally = FALSE, rollout_percentage = 0
-- because the upload path accepted whatever Content-Type the client declared.
-- With the flag off, DocumentController is invisible: every upload 404s, so the
-- document field on contracts, software licences, expenses, disposal records
-- and every compliance evidence record has no way to hold an actual file.
--
-- V34 is applied on staging and its checksum must not change, so this is a new
-- row-level UPDATE rather than an edit to it. Nothing is added or dropped.
--
-- What changed since V34:
--   * UploadValidator enforces a content-type allow-list that excludes SVG,
--     HTML, XHTML and XML -- the script-bearing formats that made an uploaded
--     file a stored-XSS vector -- and verifies the leading bytes against the
--     declared type, so a renamed payload is rejected rather than stored.
--   * A 25 MB ceiling, enforced in the validator and by Spring's multipart
--     limits, on the bytes rather than on the client's declared length.
--   * Downloads are served Content-Disposition: attachment with
--     X-Content-Type-Options: nosniff and a locked-down CSP, so an uploaded file
--     cannot be rendered in the application's origin.
--   * Filenames are sanitised and never determine the storage path; every path
--     segment, including the tenant prefix, is generated.
--   * Files go to a durable, per-tenant-prefixed backend, and every document
--     endpoint resolves the row by (id, organisation) before touching it.
--
-- What has NOT changed, and what V34 was also waiting on:
--   * There is no malware scanning. The controls above bound what an uploaded
--     file can be interpreted as, not what it contains: a structurally valid
--     PDF or XLSX carrying a malicious payload is accepted and stored. The seam
--     for adding scanning is the FileContentScanner interface, which every
--     upload path already runs through.
--   * There is no quarantine step and no scan audit evidence, because there is
--     no scan.
-- A deployment under an obligation to scan uploads should put a scanning
-- gateway in front of AssetIQ, or keep this feature off for its tenants with a
-- row in feature_flag_organisation, which takes precedence over this global
-- default.
--
-- To reverse without a migration:
--   UPDATE feature_flag SET enabled_globally = FALSE, rollout_percentage = 0
--    WHERE flag_key = 'commercial.document-attachments';
-- To disable for one tenant only:
--   INSERT INTO feature_flag_organisation (id, feature_flag_id, organisation_id, enabled)
--   SELECT gen_random_uuid(), f.id, '<org-uuid>', FALSE
--     FROM feature_flag f WHERE f.flag_key = 'commercial.document-attachments';

INSERT INTO feature_flag (id, flag_key, description, enabled_globally, rollout_percentage)
VALUES (
    gen_random_uuid(),
    'commercial.document-attachments',
    'Document attachments. Uploads are bounded by a content-type allow-list with '
    || 'byte-signature verification (no SVG/HTML), a 25 MB cap and filename '
    || 'sanitisation; downloads are served as non-sniffable attachments. '
    || 'Files are NOT scanned for malware.',
    TRUE,
    100
)
ON CONFLICT (flag_key) DO UPDATE
SET description        = EXCLUDED.description,
    enabled_globally   = TRUE,
    rollout_percentage = 100,
    updated_at         = now();
