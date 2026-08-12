-- Align currency column types with what the JPA entities actually declare.
--
-- The baseline created every currency column as CHAR(3), which Postgres reports as
-- `bpchar`. Hibernate maps a String to VARCHAR, so with ddl-auto=validate the
-- application refuses to start:
--
--   Schema-validation: wrong column type encountered in column [currency] in
--   table [asset]; found [bpchar (Types#CHAR)], but expecting [varchar(255)]
--
-- This had never been caught because nothing in the project boots the application
-- against a Flyway-built Postgres schema. The test profile runs H2 with
-- ddl-auto=create-drop, which generates the schema *from* the entities, so the two
-- cannot disagree there; and the CI migration-integrity job applies the migrations
-- without ever starting Spring. The mismatch was invisible until the first real
-- deployment, where it crash-looped the container.
-- ValidatesAgainstFlywaySchemaTest now closes that gap permanently.
--
-- The lengths are not uniform because the entities are not uniform. Each column is
-- set to exactly what its own mapping expects, rather than to one tidy value that
-- would leave a different subset failing:
--
--   length = 3   Organisation.billingCurrency, LeaseRecord, Expense, ExchangeRate
--   length = 10  SubscriptionPlan, BillingPayment, CloudAsset, CloudCostRecord
--   unspecified  Asset, PurchaseOrder, Contract, Budget, SoftwareLicense -> 255
--
-- CHAR(3) also carried a real data bug independent of validation: CHAR is
-- blank-padded, so a shorter value read back padded ('GH ') and compared unequal
-- to 'GH'. VARCHAR does not pad.
--
-- Table names here were taken from a migrated database rather than read off the
-- CREATE TABLE statements: three of them are plural (expenses, lease_records,
-- exchange_rates) where the entity name is singular, and guessing produced a
-- migration that failed on a table that does not exist.
--
-- No USING clause is needed: Postgres casts bpchar to varchar implicitly, and the
-- stored values are already 3-character ISO-4217 codes.

-- ── length 3 ─────────────────────────────────────────────────────────────────
ALTER TABLE organisation   ALTER COLUMN billing_currency TYPE VARCHAR(3);
ALTER TABLE lease_records  ALTER COLUMN currency         TYPE VARCHAR(3);
ALTER TABLE expenses       ALTER COLUMN currency         TYPE VARCHAR(3);
ALTER TABLE exchange_rates ALTER COLUMN base_currency    TYPE VARCHAR(3);
ALTER TABLE exchange_rates ALTER COLUMN target_currency  TYPE VARCHAR(3);

-- ── length 10 ────────────────────────────────────────────────────────────────
ALTER TABLE subscription_plan ALTER COLUMN currency TYPE VARCHAR(10);
ALTER TABLE billing_payment   ALTER COLUMN currency TYPE VARCHAR(10);
ALTER TABLE cloud_asset       ALTER COLUMN currency TYPE VARCHAR(10);
ALTER TABLE cloud_cost_record ALTER COLUMN currency TYPE VARCHAR(10);

-- ── unspecified, so Hibernate's default of 255 ───────────────────────────────
ALTER TABLE asset            ALTER COLUMN currency TYPE VARCHAR(255);
ALTER TABLE purchase_order   ALTER COLUMN currency TYPE VARCHAR(255);
ALTER TABLE contract         ALTER COLUMN currency TYPE VARCHAR(255);
ALTER TABLE budget           ALTER COLUMN currency TYPE VARCHAR(255);
ALTER TABLE software_license ALTER COLUMN currency TYPE VARCHAR(255);
