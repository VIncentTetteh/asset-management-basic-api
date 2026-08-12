-- V26__user_email_verification.sql
--
-- Adds signup email verification, which did not exist anywhere in the product.
-- Until now anyone could register a tenant with an address they did not control:
-- there was no email_verified column, no verification token, and no flow. For a
-- self-serve signup that is both an impersonation vector and an invisible support
-- problem — a customer who mistypes their address gets a working account they can
-- never recover, and nobody finds out.
--
-- Mirrors the existing password-reset columns on the same table (reset_password_token
-- / _expiry / _used, added in the V7 baseline) so the two token flows look alike:
-- the raw token is emailed to the user, and only its SHA-256 hash is ever stored,
-- so a database breach does not hand out usable tokens.
--
-- BACKFILL, and this is the part that matters: every existing user is marked
-- verified as of this migration. Enforcement is at login, so leaving them NULL
-- would lock out every account that already exists — the demo tenant, the smoke
-- tenants, and every real user — the moment enforcement is switched on. New
-- registrations after this point get NULL and must verify.

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS email_verified_at                TIMESTAMP,
    ADD COLUMN IF NOT EXISTS email_verification_token         VARCHAR(100),
    ADD COLUMN IF NOT EXISTS email_verification_token_expiry  TIMESTAMP;

-- Grandfather everyone who predates verification.
UPDATE app_user
SET email_verified_at = NOW()
WHERE email_verified_at IS NULL;

-- The verification lookup is by token hash alone (the user is not yet authenticated
-- and has no tenant context), so it needs its own index. Partial: only unconsumed
-- tokens are ever looked up, and the column is NULL for the overwhelming majority
-- of rows once verification completes.
CREATE INDEX IF NOT EXISTS idx_app_user_email_verification_token
    ON app_user (email_verification_token)
    WHERE email_verification_token IS NOT NULL;
