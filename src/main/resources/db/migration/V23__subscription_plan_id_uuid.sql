-- V23__subscription_plan_id_uuid.sql
-- Same class of drift as V10: the V7 baseline predates SubscriptionPlan
-- becoming a proper UUID-keyed table. organisation_subscription.plan_id and
-- billing_payment.plan_id were left as VARCHAR(100) (an earlier design
-- stored the plan *code*, e.g. "BASIC", directly). The current entity model
-- (OrganisationSubscription.plan / BillingPayment.plan) is a real
-- @ManyToOne(SubscriptionPlan), which Hibernate maps against
-- subscription_plan.id (UUID) — every query that joins through it fails at
-- runtime with:
--
--   ERROR: operator does not exist: uuid = character varying
--
-- This blocks UsageLimitServiceImpl.assertCanCreateAsset (and therefore
-- every asset-creation request), since it joins organisation_subscription
-- to subscription_plan before allowing the write.
--
-- Any pre-existing plan_id value is expected to already be a UUID string
-- (Hibernate writes plan.getId().toString() into the varchar column today),
-- but we defensively null out anything that isn't, rather than let the
-- migration fail outright on a database carrying stale plan-code values
-- from before the FK relationship existed.

UPDATE organisation_subscription
SET plan_id = NULL
WHERE plan_id IS NOT NULL
  AND plan_id !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';

UPDATE billing_payment
SET plan_id = NULL
WHERE plan_id IS NOT NULL
  AND plan_id !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';

ALTER TABLE organisation_subscription
    ALTER COLUMN plan_id TYPE UUID USING plan_id::uuid;

ALTER TABLE billing_payment
    ALTER COLUMN plan_id TYPE UUID USING plan_id::uuid;
