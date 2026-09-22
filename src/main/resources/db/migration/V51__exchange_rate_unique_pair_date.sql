-- V51: one live exchange rate per currency pair and effective date per organisation.
--
-- The entity declared UNIQUE(base_currency, target_currency, effective_date,
-- organisation_id) as a JPA @UniqueConstraint only; production runs
-- ddl-auto=validate, so the database accepted duplicates and a conversion on
-- that date picked one of them at random. Same pattern as V46: a partial unique
-- index over live rows (soft-deleted rates never block a new one).
--
-- Pre-check: existing live duplicates make this migration RAISE and roll back;
-- it never deletes or rewrites data. Resolve them by hand (soft-delete all but
-- the intended rate) and redeploy. To check a snapshot beforehand:
--   SELECT organisation_id, base_currency, target_currency, effective_date, count(*)
--     FROM exchange_rates WHERE deleted_at IS NULL AND effective_date IS NOT NULL
--    GROUP BY 1, 2, 3, 4 HAVING count(*) > 1;
DO $$
DECLARE
    dup_groups bigint;
BEGIN
    SELECT count(*) INTO dup_groups FROM (
        SELECT 1 FROM exchange_rates
         WHERE deleted_at IS NULL AND effective_date IS NOT NULL
         GROUP BY organisation_id, base_currency, target_currency, effective_date
        HAVING count(*) > 1) d;
    IF dup_groups > 0 THEN
        RAISE EXCEPTION 'V51: % live duplicate exchange-rate group(s) (organisation, pair, effective date). Resolve them (no data is changed by this migration) and redeploy.',
            dup_groups;
    END IF;
END;
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_exchange_rates_org_pair_date_live
    ON exchange_rates (organisation_id, base_currency, target_currency, effective_date)
    WHERE deleted_at IS NULL AND effective_date IS NOT NULL;
