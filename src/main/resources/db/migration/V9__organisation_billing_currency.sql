-- V9__organisation_billing_currency.sql
-- P1-1: Every organisation now owns an authoritative billing currency.
--
-- New orgs default to GHS (AssetIQ is a Ghana-first product). Legacy orgs are
-- back-filled by a country heuristic; anything unrecognised falls through to
-- GHS because that's the most likely correct answer for our current customer
-- base and it's trivially re-mappable via the settings UI.

ALTER TABLE organisation
    ADD COLUMN IF NOT EXISTS billing_currency VARCHAR(3) NOT NULL DEFAULT 'GHS';

-- Backfill from the existing `country` free-text column. The heuristic is
-- intentionally conservative — we list the ISO-alpha-2 codes AND the common
-- long-form names. Anything else keeps the GHS default.
UPDATE organisation SET billing_currency = 'NGN'
 WHERE billing_currency = 'GHS'
   AND (UPPER(TRIM(country)) IN ('NG', 'NIGERIA'));

UPDATE organisation SET billing_currency = 'KES'
 WHERE billing_currency = 'GHS'
   AND (UPPER(TRIM(country)) IN ('KE', 'KENYA'));

UPDATE organisation SET billing_currency = 'ZAR'
 WHERE billing_currency = 'GHS'
   AND (UPPER(TRIM(country)) IN ('ZA', 'SOUTH AFRICA'));

UPDATE organisation SET billing_currency = 'USD'
 WHERE billing_currency = 'GHS'
   AND (UPPER(TRIM(country)) IN ('US', 'USA', 'UNITED STATES', 'UNITED STATES OF AMERICA'));

UPDATE organisation SET billing_currency = 'EUR'
 WHERE billing_currency = 'GHS'
   AND (UPPER(TRIM(country)) IN (
        'DE','GERMANY','FR','FRANCE','IT','ITALY','ES','SPAIN','NL','NETHERLANDS',
        'BE','BELGIUM','IE','IRELAND','PT','PORTUGAL','AT','AUSTRIA','FI','FINLAND'));

UPDATE organisation SET billing_currency = 'GBP'
 WHERE billing_currency = 'GHS'
   AND (UPPER(TRIM(country)) IN ('GB', 'UK', 'UNITED KINGDOM'));

CREATE INDEX IF NOT EXISTS idx_org_billing_currency
    ON organisation (billing_currency);
