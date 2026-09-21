-- Scheduled plan changes and renewal bookkeeping.
--
-- A downgrade takes effect at the end of the period the customer already paid for,
-- so the target plan has to be remembered until then. Upgrades apply immediately and
-- never use these columns.
ALTER TABLE organisation_subscription
    ADD COLUMN IF NOT EXISTS scheduled_plan_id UUID REFERENCES subscription_plan(id),
    ADD COLUMN IF NOT EXISTS scheduled_change_at TIMESTAMPTZ;

-- Period-end sweep: every ACTIVE subscription whose period has ended.
CREATE INDEX IF NOT EXISTS idx_org_subscription_status_period_end
    ON organisation_subscription (status, current_period_end);

-- Renewal charges arrive by webhook with a gateway-generated reference; the gateway
-- transaction id is what makes recording them idempotent.
CREATE UNIQUE INDEX IF NOT EXISTS uk_billing_payment_paystack_transaction
    ON billing_payment (paystack_transaction_id)
    WHERE paystack_transaction_id IS NOT NULL;
