-- V20: Shared document attachment table for receipts, evidence, policy docs, etc.
CREATE TABLE IF NOT EXISTS document_attachments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     VARCHAR(50)  NOT NULL,
    entity_id       UUID         NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    storage_key     TEXT         NOT NULL,
    file_size       BIGINT       NOT NULL,
    uploaded_by_id  UUID         REFERENCES app_user(id) ON DELETE SET NULL,
    organisation_id UUID         NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_doc_attachments_entity    ON document_attachments(entity_type, entity_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_doc_attachments_org       ON document_attachments(organisation_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_doc_attachments_uploader  ON document_attachments(uploaded_by_id);
