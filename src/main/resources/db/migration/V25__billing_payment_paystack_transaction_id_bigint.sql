-- billing_payment.paystack_transaction_id was declared VARCHAR(255) in V7__baseline.sql,
-- but BillingPayment.paystackTransactionId maps it as Long (BillingServiceImpl stores
-- Paystack's numeric webhook "id" field via data.path("id").asLong()). Null out any
-- legacy non-numeric values defensively before the type change, matching the pattern
-- used in V23 for organisation_subscription.plan_id.
UPDATE billing_payment
SET paystack_transaction_id = NULL
WHERE paystack_transaction_id !~ '^[0-9]+$';

ALTER TABLE billing_payment
    ALTER COLUMN paystack_transaction_id TYPE BIGINT USING paystack_transaction_id::bigint;
