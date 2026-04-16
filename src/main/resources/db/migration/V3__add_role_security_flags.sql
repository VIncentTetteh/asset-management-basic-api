-- V3: Add security flags to the role table
-- system_role    : marks built-in roles that cannot be modified or deleted by org admins
-- grant_all_permissions : when TRUE the role receives every permission without needing to
--                         enumerate them — replaces the fragile name-matching admin detection
ALTER TABLE role
    ADD COLUMN IF NOT EXISTS system_role            BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS grant_all_permissions  BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: any existing role whose name matches the old admin-detection heuristic
-- (ADMIN, *_ADMIN) should be treated as grant-all and marked as a system role.
UPDATE role
SET    system_role           = TRUE,
       grant_all_permissions = TRUE
WHERE  UPPER(TRIM(name)) IN ('ADMIN', 'ORG_ADMIN', 'SYSTEM ADMINISTRATOR')
   OR  UPPER(TRIM(name)) LIKE '%\_ADMIN' ESCAPE '\';
