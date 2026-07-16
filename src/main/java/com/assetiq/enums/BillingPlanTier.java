package com.assetiq.enums;

/**
 * Subscription plan tier ladder.
 *
 * <p>The public ladder is FREEMIUM → BASIC → BUSINESS → ENTERPRISE. Historical
 * tiers are retained so old DB rows and tenant records load cleanly.
 */
public enum BillingPlanTier {
    FREEMIUM,
    BASIC,
    BUSINESS,
    ENTERPRISE,
    // Legacy — kept for backwards-compatible deserialisation of existing rows.
    @Deprecated
    STARTER,
    @Deprecated
    GROWTH,
    @Deprecated
    PREMIUM
}
