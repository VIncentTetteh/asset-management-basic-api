package com.assetiq.enums;

import java.util.Locale;
import java.util.Map;

/**
 * Deployment environment of a cloud asset. Stored as the enum name in the
 * {@code cloud_asset.environment} column (VARCHAR), so older free-text values
 * keep loading; every write goes through {@link #normalise(String)}.
 */
public enum CloudEnvironment {
    PROD,
    STAGING,
    DEV,
    TEST,
    OTHER;

    /** Common spellings, including the values providers put in environment tags. */
    private static final Map<String, CloudEnvironment> ALIASES = Map.ofEntries(
            Map.entry("PROD", PROD), Map.entry("PRODUCTION", PROD), Map.entry("PRD", PROD), Map.entry("LIVE", PROD),
            Map.entry("STAGING", STAGING), Map.entry("STAGE", STAGING), Map.entry("STG", STAGING),
            Map.entry("PREPROD", STAGING), Map.entry("PRE-PROD", STAGING), Map.entry("PRE_PROD", STAGING),
            Map.entry("DEV", DEV), Map.entry("DEVELOPMENT", DEV), Map.entry("DEVEL", DEV),
            Map.entry("TEST", TEST), Map.entry("TESTING", TEST), Map.entry("QA", TEST), Map.entry("UAT", TEST),
            Map.entry("SIT", TEST),
            Map.entry("OTHER", OTHER));

    /**
     * Maps free text (a form value or a provider tag such as "production") onto the
     * enum. Blank means "not set" (null); anything unrecognised is OTHER, never a
     * silent PROD.
     */
    public static CloudEnvironment normalise(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return ALIASES.getOrDefault(key, OTHER);
    }

    /** {@link #normalise(String)} as the stored column value. */
    public static String normaliseToName(String raw) {
        CloudEnvironment env = normalise(raw);
        return env == null ? null : env.name();
    }
}
