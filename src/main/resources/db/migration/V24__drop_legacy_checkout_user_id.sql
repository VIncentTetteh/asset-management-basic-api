-- V24__drop_legacy_checkout_user_id.sql
-- Live bug, found while seeding checkout data end-to-end: every checkout
-- attempt (both user- and employee-recipient flows) fails on a fresh
-- database with:
--
--   ERROR: null value in column "user_id" of relation "checkout_records"
--   violates not-null constraint
--
-- Root cause: checkout_records.user_id is a leftover from the V7 baseline
-- (back when the table was named checkout_record, singular, and modeled a
-- direct user_id NOT NULL FK). V13 renamed the table to match the current
-- @Table("checkout_records"), and Hibernate's ddl-auto=update has since
-- added the columns the CheckoutRecord entity actually uses today
-- (checked_out_by_id, employee_id, checked_in_by_id, etc.) — but ddl-auto
-- never drops or relaxes a column it no longer recognizes, so the original
-- user_id NOT NULL constraint has been silently sitting there ever since,
-- with nothing in the codebase writing to it (grep confirms no entity field
-- maps to it and no other migration references it beyond its own index).
--
-- Dropping the column also drops its dependent FK and index automatically.

ALTER TABLE checkout_records
    DROP COLUMN IF EXISTS user_id;
