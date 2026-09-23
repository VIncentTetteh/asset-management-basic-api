package com.assetiq.services.ai;

/**
 * The model provider could not answer. Carries the reason so the chat service
 * can degrade with a message a user can act on, rather than a 500.
 */
public class LlmUnavailableException extends RuntimeException {

    public enum Reason {
        /** No key or endpoint configured for the selected provider in this environment. */
        NOT_CONFIGURED,
        /** The provider returned 429 — its quota, not ours. */
        PROVIDER_THROTTLED,
        /** Timeout, connection failure, 5xx. */
        UNREACHABLE,
        /** The provider rejected the request (4xx other than 429). */
        REJECTED
    }

    private final Reason reason;

    public LlmUnavailableException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public LlmUnavailableException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
