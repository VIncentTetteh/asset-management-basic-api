-- Cloud asset environments were free text ("production", "Prod", "dev"...). The
-- application now stores the CloudEnvironment enum name (PROD, STAGING, DEV,
-- TEST, OTHER); normalise existing rows with the same aliases as
-- CloudEnvironment.normalise. Blank becomes NULL ("not set"). Data only.
UPDATE cloud_asset
SET environment = CASE
        WHEN environment IS NULL OR btrim(environment) = '' THEN NULL
        WHEN upper(btrim(environment)) IN ('PROD', 'PRODUCTION', 'PRD', 'LIVE') THEN 'PROD'
        WHEN upper(btrim(environment)) IN ('STAGING', 'STAGE', 'STG', 'PREPROD', 'PRE-PROD', 'PRE_PROD') THEN 'STAGING'
        WHEN upper(btrim(environment)) IN ('DEV', 'DEVELOPMENT', 'DEVEL') THEN 'DEV'
        WHEN upper(btrim(environment)) IN ('TEST', 'TESTING', 'QA', 'UAT', 'SIT') THEN 'TEST'
        ELSE 'OTHER'
    END
WHERE environment IS DISTINCT FROM CASE
        WHEN environment IS NULL OR btrim(environment) = '' THEN NULL
        WHEN upper(btrim(environment)) IN ('PROD', 'PRODUCTION', 'PRD', 'LIVE') THEN 'PROD'
        WHEN upper(btrim(environment)) IN ('STAGING', 'STAGE', 'STG', 'PREPROD', 'PRE-PROD', 'PRE_PROD') THEN 'STAGING'
        WHEN upper(btrim(environment)) IN ('DEV', 'DEVELOPMENT', 'DEVEL') THEN 'DEV'
        WHEN upper(btrim(environment)) IN ('TEST', 'TESTING', 'QA', 'UAT', 'SIT') THEN 'TEST'
        ELSE 'OTHER'
    END;
