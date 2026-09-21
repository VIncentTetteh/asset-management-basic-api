-- Ciphertext is longer than its plaintext. Widen protected fields before the
-- idempotent application backfill encrypts legacy records.
ALTER TABLE app_user ALTER COLUMN mfa_secret TYPE VARCHAR(512) USING mfa_secret::varchar;
ALTER TABLE webhook ALTER COLUMN secret TYPE VARCHAR(512) USING secret::varchar;
ALTER TABLE billing_payment ALTER COLUMN paystack_authorization_code TYPE VARCHAR(512)
    USING paystack_authorization_code::varchar;
ALTER TABLE billing_payment ALTER COLUMN paystack_email_token TYPE VARCHAR(512)
    USING paystack_email_token::varchar;
ALTER TABLE organisation_subscription ALTER COLUMN paystack_email_token TYPE VARCHAR(512)
    USING paystack_email_token::varchar;

-- Raw gateway bodies duplicate customer PII and reusable credentials. Normalized
-- transaction IDs/status/amount/channel remain available for reconciliation.
UPDATE billing_payment SET raw_gateway_payload = NULL WHERE raw_gateway_payload IS NOT NULL;
