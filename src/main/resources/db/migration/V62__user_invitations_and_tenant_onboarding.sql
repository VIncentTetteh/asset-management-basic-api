-- V62: colleague invitations, and the dismissal flag for the first-run checklist.
--
-- Expand-only: two new objects and one nullable column. Nothing is dropped or
-- rewritten, so an older instance keeps running against this schema.
--
-- Why a table rather than reusing app_user: an invitation is not an account.
-- Creating the user up front (the only path that existed before this) means a
-- half-real person occupies a seat, appears in the directory, and can be picked
-- as an asset custodian before anyone has proved they own the mailbox. The row
-- below holds intent only; the account is created at acceptance.
--
-- The token is never stored. Only its SHA-256 hash is, exactly as
-- EmailVerificationService does for signup links: a dump of this table yields
-- nothing an attacker can present at the accept endpoint.

-- Every foreign key here cascades or nulls on delete, because closing an account
-- issues one DELETE against organisation and lets the database take the tenant's
-- ~90 tables with it (see AccountLifecycleService and AccountPurgeCascadeTest).
-- A new table with a plain NO ACTION reference would make a tenant undeletable,
-- and the failure would only surface thirty days after someone closed their
-- account. An invitation is meaningless without its organisation or its role, so
-- those cascade; the optional references become NULL rather than destroying an
-- invitation because a department was reorganised.
CREATE TABLE IF NOT EXISTS user_invitation (
    id                  UUID PRIMARY KEY,
    organisation_id     UUID NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    email               VARCHAR(255) NOT NULL,
    role_id             UUID NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    department_id       UUID REFERENCES department (id) ON DELETE SET NULL,
    first_name          VARCHAR(100),
    last_name           VARCHAR(100),
    job_title           VARCHAR(150),
    note                VARCHAR(500),

    token_hash          VARCHAR(64) NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,

    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invited_by_user_id  UUID REFERENCES app_user (id) ON DELETE SET NULL,
    accepted_at         TIMESTAMPTZ,
    accepted_user_id    UUID REFERENCES app_user (id) ON DELETE SET NULL,
    revoked_at          TIMESTAMPTZ,
    revoked_by          VARCHAR(255),

    last_sent_at        TIMESTAMPTZ,
    send_count          INTEGER NOT NULL DEFAULT 1,
    email_delivered     BOOLEAN NOT NULL DEFAULT FALSE,

    created_at          TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    modified_by         VARCHAR(255)
);

-- Redeeming a link is a lookup by hash; it must be an index, not a scan, and the
-- hash must be unique or two invitations could share a credential.
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_invitation_token_hash
    ON user_invitation (token_hash);

-- At most one live invitation per address per tenant. Inviting the same
-- colleague twice re-issues the existing row instead of creating a second one,
-- and this index is what makes that true under concurrency rather than by
-- convention. Accepted, revoked and soft-deleted rows are history and may
-- accumulate: the same person can leave and be re-invited later.
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_invitation_pending_email
    ON user_invitation (organisation_id, lower(email))
    WHERE status = 'PENDING' AND deleted_at IS NULL;

-- The admin listing and the per-tenant send-rate window both filter on these.
CREATE INDEX IF NOT EXISTS ix_user_invitation_org_created
    ON user_invitation (organisation_id, created_at DESC);

-- Whether this tenant has put the first-run checklist away. NULL means still
-- showing. Stored on the organisation, not the user, because the checklist
-- describes the company's setup, not one person's progress through it.
ALTER TABLE organisation
    ADD COLUMN IF NOT EXISTS onboarding_dismissed_at TIMESTAMPTZ;
