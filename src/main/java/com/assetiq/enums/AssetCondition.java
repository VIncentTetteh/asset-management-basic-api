package com.assetiq.enums;

/**
 * Physical condition of an asset. Persisted as a string in {@code asset.condition}
 * (VARCHAR(20), no CHECK constraint), so adding a value needs no migration.
 */
public enum AssetCondition {
    NEW,
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    DAMAGED,
    SCRAP
}

