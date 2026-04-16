-- V5__user_role_composition.sql
-- Phase 2 / B-5: Add many-to-many user ↔ role support via a new join table.
--
-- The existing role_id FK on app_user is retained as the "primary role" used
-- for JWT claims and backward-compatible single-role queries.  The new
-- user_roles table is the authoritative source for permission resolution.

-- 1. Create the join table.
CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT pk_user_roles  PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user     FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role     FOREIGN KEY (role_id) REFERENCES role(id)     ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ur_user_id ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_ur_role_id ON user_roles(role_id);

-- 2. Backfill: every existing user who already has a primary role gets that
--    role added to the join table so the permission cache returns the same
--    result as before without requiring re-login or data re-entry.
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, u.role_id
FROM   app_user u
WHERE  u.role_id IS NOT NULL
ON CONFLICT DO NOTHING;
