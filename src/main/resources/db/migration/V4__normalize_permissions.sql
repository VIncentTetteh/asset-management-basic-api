-- V4__normalize_permissions.sql
-- Phase 2 / B-1: Normalise role permissions from a JSON text column to a
-- relational join table, eliminating all JSON parsing in application code.
--
-- Handles both storage formats produced by the old frontend:
--   • Array  :  ["PERM1","PERM2"]
--   • Object :  {"PERM1":true,"PERM2":true}

-- 1. Create the new join table.
CREATE TABLE IF NOT EXISTS role_permission (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    role_id     UUID         NOT NULL,
    permission  VARCHAR(100) NOT NULL,
    CONSTRAINT pk_role_permission     PRIMARY KEY (id),
    CONSTRAINT fk_rp_role             FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    CONSTRAINT uq_rp_role_permission  UNIQUE (role_id, permission)
);

CREATE INDEX IF NOT EXISTS idx_rp_role_id ON role_permission(role_id);

-- 2. Migrate array-format JSON: ["PERM1","PERM2"]
INSERT INTO role_permission (role_id, permission)
SELECT r.id,
       elem.value
FROM   role r,
       jsonb_array_elements_text(r.permissions::jsonb) AS elem(value)
WHERE  r.permissions IS NOT NULL
  AND  trim(r.permissions) <> ''
  AND  trim(r.permissions) LIKE '[%'
ON CONFLICT (role_id, permission) DO NOTHING;

-- 3. Migrate object-format JSON: {"PERM1":true,"PERM2":true}
INSERT INTO role_permission (role_id, permission)
SELECT r.id,
       kv.key
FROM   role r,
       jsonb_each(r.permissions::jsonb) AS kv(key, value)
WHERE  r.permissions IS NOT NULL
  AND  trim(r.permissions) <> ''
  AND  trim(r.permissions) LIKE '{%'
  AND  kv.value::text = 'true'
ON CONFLICT (role_id, permission) DO NOTHING;

-- 4. Drop the now-redundant JSON column.
--    All permission data now lives exclusively in role_permission.
ALTER TABLE role DROP COLUMN IF EXISTS permissions;
