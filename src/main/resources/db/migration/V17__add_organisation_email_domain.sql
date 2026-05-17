ALTER TABLE organisation ADD COLUMN email_domain VARCHAR(255);
CREATE UNIQUE INDEX idx_organisation_email_domain
    ON organisation(email_domain)
    WHERE email_domain IS NOT NULL AND deleted_at IS NULL;
