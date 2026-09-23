package com.assetiq.exceptions;

/**
 * A caller has exhausted an application-level allowance and should back off.
 *
 * <p>Distinct from the transport rate limiter in {@code RateLimitingInterceptor}:
 * that one counts requests per client key and fails open when Redis is away.
 * This one is a business rule counted in the database — how many invitations one
 * organisation may send in an hour, for instance — so it holds whether or not
 * Redis is up, and survives a restart.
 */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
