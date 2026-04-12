package com.example.demo.config;

/**
 * Deployment mode for the application.
 *
 * <ul>
 *   <li>{@link #CLOUD} – vendor-hosted SaaS. All license enforcement is disabled.
 *       This is the default and must never break existing cloud behaviour.</li>
 *   <li>{@link #STANDALONE} – customer self-hosted (on-premise). A valid license key
 *       is required. The {@code LicenseGuardFilter} and related beans are only
 *       instantiated in this mode.</li>
 * </ul>
 *
 * Controlled by the {@code APP_MODE} environment variable (or {@code app.mode} property).
 * Defaults to {@code cloud} so that the existing deployment is unaffected.
 */
public enum AppMode {
    CLOUD,
    STANDALONE;

    /**
     * Parse a string value (case-insensitive) to an {@link AppMode}.
     * Returns {@link #CLOUD} for any unrecognised value to fail safe.
     */
    public static AppMode from(String value) {
        if (value == null) return CLOUD;
        return switch (value.trim().toLowerCase()) {
            case "standalone" -> STANDALONE;
            default -> CLOUD;
        };
    }
}
