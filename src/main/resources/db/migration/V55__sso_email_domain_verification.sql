-- SSO discovery routed on an organisation's email_domain with nothing proving the
-- organisation owned it: any tenant could claim gmail.com (or a competitor's
-- domain) and capture the SSO discovery of everyone who typed an address there.
-- A domain now has to be verified — a DNS TXT record carrying its token, or an
-- operator's approval — before discovery will route on it.
--
-- Expand-only: two nullable columns, no backfill. Every existing domain starts
-- unverified, so discovery stops routing on it until its owner verifies it; the
-- domain itself, and every SSO setting, is left exactly as it is.
--
-- Pre-check (lists the domains that will need verifying; no rows means nothing changes):
--   SELECT o.id, o.name, o.email_domain
--     FROM organisation o
--     JOIN org_sso_config c ON c.organisation_id = o.id AND c.enabled = TRUE
--    WHERE o.deleted_at IS NULL AND o.email_domain IS NOT NULL;
ALTER TABLE organisation
    ADD COLUMN IF NOT EXISTS email_domain_verified_at TIMESTAMP;
ALTER TABLE organisation
    ADD COLUMN IF NOT EXISTS email_domain_token VARCHAR(64);
