-- V27__subscription_past_due_since.sql
--
-- Gives the dunning process a clock to work from.
--
-- Background: BillingServiceImpl.handlePaystackWebhook already flips a subscription
-- to PAST_DUE when Paystack reports invoice.payment_failed. Nothing anywhere read
-- that status — a grep across the whole codebase returned exactly two hits, the enum
-- declaration and that single write. A customer whose card expired therefore kept
-- full paid access indefinitely, was never told, and never appeared in any report.
--
-- Recovering the grace clock from updated_at is not viable: that column moves on any
-- write to the row (plan change, auto-renew toggle, Paystack code refresh), so the
-- countdown would silently reset. This is an explicit, single-purpose timestamp:
-- when the account first entered PAST_DUE, cleared when it recovers or is downgraded.

ALTER TABLE organisation_subscription
    ADD COLUMN IF NOT EXISTS past_due_since TIMESTAMP;

-- Any subscription already sitting in PAST_DUE predates the dunning job and has no
-- recorded start. Treat now as day zero rather than leaving it NULL, so those accounts
-- enter the reminder sequence from the top instead of being downgraded immediately on
-- the job's first run.
UPDATE organisation_subscription
SET past_due_since = NOW()
WHERE status = 'PAST_DUE'
  AND past_due_since IS NULL;

-- The job scans by status on every run; without this it is a full table scan.
CREATE INDEX IF NOT EXISTS idx_org_subscription_status_past_due
    ON organisation_subscription (status, past_due_since)
    WHERE deleted_at IS NULL;
