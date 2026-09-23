package com.assetiq.license.offline;

import com.assetiq.enums.BillingPlanTier;

import java.time.Instant;

/**
 * The result of evaluating a self-hosted offline licence key.
 *
 * <p>Always non-null and always carries a {@link #tier()} that is safe to enforce:
 * an unlicensed, expired or tampered installation resolves to
 * {@link BillingPlanTier#FREEMIUM} rather than to an error. {@link #message()} is
 * written to be shown to an administrator verbatim.</p>
 *
 * @param status        why this installation has the tier it has
 * @param tier          the tier to enforce right now — never null
 * @param organisation  the organisation the key was issued to, or null when unlicensed
 * @param seats         seats the key grants, or null when unlicensed / unlimited
 * @param expiresAt     when the key stops granting its tier, or null when unlicensed
 * @param message       operator-facing explanation, never punitive
 */
public record OfflineLicense(
        OfflineLicenseStatus status,
        BillingPlanTier tier,
        String organisation,
        Integer seats,
        Instant expiresAt,
        String message
) {

    /**
     * The fallback every non-VALID path resolves to.
     *
     * <p>Deliberately phrased as a statement of what still works. A self-hosted
     * installation whose key lapsed keeps all of its data and keeps running; it
     * simply enforces free-tier quotas until a current key is supplied.</p>
     */
    public static OfflineLicense freeTier(OfflineLicenseStatus status, String message) {
        return new OfflineLicense(status, BillingPlanTier.FREEMIUM, null, null, null, message);
    }

    /** A verified, in-date key. */
    public static OfflineLicense licensed(BillingPlanTier tier,
                                          String organisation,
                                          Integer seats,
                                          Instant expiresAt) {
        return new OfflineLicense(
                OfflineLicenseStatus.VALID,
                tier,
                organisation,
                seats,
                expiresAt,
                "Licensed to " + organisation + " — " + tier + " tier"
                        + (seats == null ? "" : ", " + seats + " seats")
                        + ", valid until " + expiresAt + ".");
    }

    /** {@code true} when the licence grants a paid tier. */
    public boolean isLicensed() {
        return status.isEntitled();
    }
}
