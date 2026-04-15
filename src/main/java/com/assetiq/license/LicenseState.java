package com.assetiq.license;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable snapshot of the current license state held in memory.
 *
 * Built by {@link LicenseService} after local RSA verification and
 * optional remote validation. Consumed by {@link LicenseGuardFilter}
 * and the {@code GET /api/v1/license/status} endpoint.
 *
 * Only ever populated in standalone mode — in cloud mode this record
 * is never created and the guard filter is never loaded.
 */
public record LicenseState(

    /** Raw status string matching the License Server vocabulary. */
    String status,          // valid | grace_period | expired | revoked | not_found | error

    /** true when write operations must be blocked. */
    boolean readOnly,

    String plan,
    Instant expiresAt,
    long daysRemaining,
    int gracePeriodDays,

    Map<String, Object> limits,
    Map<String, Object> features,

    /** Timestamp of the last successful remote validation. */
    Instant lastRemoteValidationAt,

    /** Human-readable message, populated on non-valid states. */
    String message
) {
    // ── Factory helpers ───────────────────────────────────────────────────────

    public static LicenseState valid(String plan, Instant expiresAt, long daysRemaining,
                                     int gracePeriodDays, Map<String, Object> limits,
                                     Map<String, Object> features, Instant lastRemote) {
        return new LicenseState("valid", false, plan, expiresAt, daysRemaining,
                                gracePeriodDays, limits, features, lastRemote, null);
    }

    public static LicenseState gracePeriod(String plan, Instant expiresAt, long daysRemaining,
                                            int gracePeriodDays, Map<String, Object> limits,
                                            Map<String, Object> features, Instant lastRemote,
                                            String message) {
        return new LicenseState("grace_period", true, plan, expiresAt, daysRemaining,
                                gracePeriodDays, limits, features, lastRemote, message);
    }

    public static LicenseState expired(String message) {
        return new LicenseState("expired", true, null, null, 0, 0,
                                null, null, null, message);
    }

    public static LicenseState revoked(String message) {
        return new LicenseState("revoked", true, null, null, 0, 0,
                                null, null, null, message);
    }

    public static LicenseState error(String message) {
        return new LicenseState("error", true, null, null, 0, 0,
                                null, null, null, message);
    }

    public boolean isReadOnly() { return readOnly; }
}
