-- V6: Add structured RBAC / security classification columns to audit_event
--
-- event_type  : high-level category (API_REQUEST, AUTH_SUCCESS, ROLE_PERMISSIONS_CHANGED, …)
-- target_id   : UUID of the affected entity (role, user) for RBAC change events
-- old_value   : before-state snapshot (permissions list, role name, etc.)
-- new_value   : after-state snapshot
--
-- Existing rows are back-filled to API_REQUEST so the NOT NULL constraint holds
-- without touching data (API_REQUEST is the correct default for all historical rows).

ALTER TABLE audit_event
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(50)   NOT NULL DEFAULT 'API_REQUEST',
    ADD COLUMN IF NOT EXISTS target_id  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS old_value  VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS new_value  VARCHAR(1000);

-- Indexes for efficient filtering by event type and target
CREATE INDEX IF NOT EXISTS idx_audit_event_event_type ON audit_event (event_type);
CREATE INDEX IF NOT EXISTS idx_audit_event_target_id  ON audit_event (target_id);
