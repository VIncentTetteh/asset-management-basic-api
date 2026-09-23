package com.assetiq.services.ai;

/**
 * The caller, or the caller's organisation, has used its share of the assistant
 * for the current window. Mapped to HTTP 429 with a {@code Retry-After} header.
 */
public class AiQuotaExceededException extends RuntimeException {

    private final int retryAfterSeconds;

    public AiQuotaExceededException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
