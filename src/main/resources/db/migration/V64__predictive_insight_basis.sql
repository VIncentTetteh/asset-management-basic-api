-- V64: replace a fabricated confidence score with the evidence it was standing in for.
--
-- Every insight carried a "confidence" between 0.60 and 0.99. None of it was
-- measured. Four of the six rules used a literal (0.99 for a warranty date, 0.90
-- for a maintenance date, 0.85 for asset aging, 0.80 for idleness) and the fifth
-- used an arithmetic expression over a count. There is no model, no training
-- data and no validation behind any of them, so there is no answer to a customer
-- asking "confident based on what?" — on a screen where every other figure is
-- traceable to a record.
--
-- Two of these rules are not predictions at all: a warranty expiring on a stored
-- date is arithmetic, and it needs no confidence. The rest are heuristics, and
-- what a user actually wants from a heuristic is what it counted, so they can
-- judge the rule rather than a decimal.
--
-- So: basis holds the evidence in words ("3 maintenance events in the last 90
-- days; condition POOR"), and confidence is nulled out. The column stays for now
-- rather than being dropped, so a client still reading it gets null rather than a
-- 500 — it is served as null by the API from this release and can be dropped once
-- no client reads it. Expand-only, per the migration policy.
--
-- Nulling the existing values loses nothing: every row here is derived and is
-- regenerated from the assets and maintenance records by the nightly refresh.

ALTER TABLE predictive_insight ADD COLUMN IF NOT EXISTS basis TEXT;

ALTER TABLE predictive_insight ALTER COLUMN confidence DROP NOT NULL;

UPDATE predictive_insight SET confidence = NULL WHERE confidence IS NOT NULL;

-- The same correction, one layer up: analytics_snapshot.idle_asset_count was
-- named for a measure AssetIQ cannot take. It counts assets in stock that nobody
-- is recorded as having SEEN — a scan, a checkout or check-in, or an audit
-- verification — which is not the same as unused, and is no longer computed from
-- record edits. Migrations here are expand-only, so the column keeps its name and
-- carries the correction as a comment; the API field is notSeenAssetCount.
COMMENT ON COLUMN analytics_snapshot.idle_asset_count IS
    'Assets in stock or reserved with no recorded sighting (scan, checkout or audit) for 180 days. Not a measure of usage: AssetIQ records no usage telemetry.';
