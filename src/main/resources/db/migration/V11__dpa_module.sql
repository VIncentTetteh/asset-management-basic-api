-- V11: Ghana Data Protection Act 2012 compliance module
-- Adds consent_records and dsar_requests tables; extends organisation with DPA officer fields.

ALTER TABLE organisation
    ADD COLUMN IF NOT EXISTS dpo_name        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dpo_email       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS data_residency_region VARCHAR(30) DEFAULT 'GH';

CREATE TABLE IF NOT EXISTS consent_record (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    purpose             VARCHAR(100) NOT NULL,
    granted             BOOLEAN NOT NULL,
    granted_at          TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    ip_address          VARCHAR(50),
    user_agent          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    modified_by         VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_consent_record_org ON consent_record(organisation_id);
CREATE INDEX IF NOT EXISTS idx_consent_record_user ON consent_record(user_id);
CREATE INDEX IF NOT EXISTS idx_consent_record_org_user_purpose ON consent_record(organisation_id, user_id, purpose);

CREATE TABLE IF NOT EXISTS dsar_request (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     UUID NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    requester_user_id   UUID REFERENCES app_user(id) ON DELETE SET NULL,
    requester_email     VARCHAR(255) NOT NULL,
    request_type        VARCHAR(30) NOT NULL,  -- ACCESS, RECTIFICATION, ERASURE, PORTABILITY, OBJECTION
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',  -- PENDING, IN_PROGRESS, COMPLETED, REJECTED
    submitted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    due_at              TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    assigned_to_user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    notes               TEXT,
    response_summary    TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    modified_by         VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_dsar_request_org ON dsar_request(organisation_id);
CREATE INDEX IF NOT EXISTS idx_dsar_request_status ON dsar_request(status);
