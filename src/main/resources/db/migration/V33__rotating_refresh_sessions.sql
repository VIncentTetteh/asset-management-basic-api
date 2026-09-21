CREATE TABLE refresh_session (
    id                     UUID PRIMARY KEY,
    token_hash             VARCHAR(64) NOT NULL UNIQUE,
    family_id              UUID NOT NULL,
    user_id                UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    expires_at             TIMESTAMPTZ NOT NULL,
    revoked_at             TIMESTAMPTZ,
    replaced_by_token_hash VARCHAR(64),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ,
    deleted_at             TIMESTAMPTZ,
    created_by             VARCHAR(255),
    modified_by            VARCHAR(255)
);

CREATE INDEX idx_refresh_session_family ON refresh_session(family_id);
CREATE INDEX idx_refresh_session_user_active ON refresh_session(user_id, revoked_at, expires_at);
