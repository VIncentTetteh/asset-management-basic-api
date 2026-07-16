-- Add discount_percent to support annual plans with a promotional discount.
-- Nullable so existing monthly plans remain unaffected.
ALTER TABLE subscription_plan
    ADD COLUMN IF NOT EXISTS discount_percent DECIMAL(5, 2);
