-- Rename the feature flag key column away from reserved identifiers.
-- Supports databases that may have either the original V8 `key` column or the
-- baseline-era `name` column.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'feature_flag' AND column_name = 'key'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'feature_flag' AND column_name = 'flag_key'
    ) THEN
        ALTER TABLE feature_flag RENAME COLUMN key TO flag_key;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'feature_flag' AND column_name = 'name'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'feature_flag' AND column_name = 'flag_key'
    ) THEN
        ALTER TABLE feature_flag RENAME COLUMN name TO flag_key;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'feature_flag' AND column_name = 'enabled'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'feature_flag' AND column_name = 'enabled_globally'
    ) THEN
        ALTER TABLE feature_flag RENAME COLUMN enabled TO enabled_globally;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_feature_flag_key ON feature_flag(flag_key);
