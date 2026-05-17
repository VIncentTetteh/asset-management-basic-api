package com.assetiq.services;

/**
 * P0-8: Thrown by the {@code FeatureFlagAspect} when a caller hits a method
 * annotated with {@link FeatureFlagGate} whose flag is OFF for the current
 * tenant. Handled by the global {@code @ControllerAdvice}; see
 * {@code GlobalExceptionHandler}.
 */
public class FeatureDisabledException extends RuntimeException {

    private final String flagKey;
    private final boolean notImplemented;

    public FeatureDisabledException(String flagKey, boolean notImplemented) {
        super("Feature '" + flagKey + "' is disabled for this tenant.");
        this.flagKey = flagKey;
        this.notImplemented = notImplemented;
    }

    public String getFlagKey() {
        return flagKey;
    }

    public boolean isNotImplemented() {
        return notImplemented;
    }
}
