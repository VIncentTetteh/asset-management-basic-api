package com.assetiq.controllers.v1;

import com.assetiq.enums.UserStatus;
import com.assetiq.models.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The bodies the authentication endpoints answer with when a sign-in cannot proceed.
 *
 * <h3>Why this exists</h3>
 * The house error envelope produced by {@link GlobalExceptionHandler} carries the
 * human-readable text under {@code message}, and every client reads that key. The
 * hand-rolled responses in {@code AuthController} and {@code MfaController} put their
 * text under {@code error} instead, so a blocked user saw only the bare HTTP status
 * ("Forbidden") and had no idea what to do. Each helper here emits <em>both</em> keys
 * with the same text: {@code message} is the one to read, {@code error} is kept
 * because these are public response shapes with possibly unknown consumers, and can be
 * dropped in a later major version. Same treatment {@code /auth/refresh} already got.
 *
 * <h3>Contract</h3>
 * Every body carries:
 * <ul>
 *   <li>{@code message} — actionable English, safe to show the user as-is;</li>
 *   <li>{@code error} — the identical text, legacy key;</li>
 *   <li>{@code errorCode} — stable machine-readable discriminator, same field name the
 *       {@link GlobalExceptionHandler} envelope uses, so one client branch covers both.</li>
 * </ul>
 * plus, per case, the flags a client needs to offer the right action (a resend button,
 * an organisation picker, a countdown) without string-matching English.
 *
 * <h3>Information leakage</h3>
 * {@link #invalidCredentials()} is a constant: an unknown address and a wrong password
 * produce byte-identical bodies. The account-state answers ({@link #accountNotActive},
 * {@link #emailVerificationRequired}) are only ever returned <em>after</em> the password
 * has been verified — see the comment at the verification gate in {@code AuthController}
 * — so they tell a caller nothing they did not already know.
 */
final class AuthResponses {

    private AuthResponses() {
    }

    // ── Error codes (public API surface — do not rename) ─────────────────────
    static final String CODE_INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    static final String CODE_EMAIL_VERIFICATION_REQUIRED = "EMAIL_VERIFICATION_REQUIRED";
    static final String CODE_ACCOUNT_NOT_ACTIVE = "ACCOUNT_NOT_ACTIVE";
    static final String CODE_ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    static final String CODE_ORGANISATION_REQUIRED = "ORGANISATION_REQUIRED";
    static final String CODE_MFA_CHALLENGE_INVALID = "MFA_CHALLENGE_INVALID";
    static final String CODE_MFA_CODE_INVALID = "MFA_CODE_INVALID";
    static final String CODE_UNAUTHENTICATED = "UNAUTHENTICATED";
    static final String CODE_BAD_REQUEST = "BAD_REQUEST";
    static final String CODE_NOT_FOUND = "NOT_FOUND";

    /** Where a client sends a blocked user to get a fresh verification link. */
    static final String RESEND_VERIFICATION_PATH = "/api/v1/auth/resend-verification";

    /**
     * Builds a body carrying {@code message}, the legacy {@code error} alias, an
     * {@code errorCode}, and any extra key/value pairs supplied in flat pairs.
     */
    static Map<String, Object> body(String message, String errorCode, Object... extras) {
        if (extras.length % 2 != 0) {
            throw new IllegalArgumentException("extras must be key/value pairs");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("error", message);
        body.put("errorCode", errorCode);
        for (int i = 0; i < extras.length; i += 2) {
            body.put(String.valueOf(extras[i]), extras[i + 1]);
        }
        return body;
    }

    /** Convenience for the hand-rolled non-login failures that only need text + code. */
    static ResponseEntity<Map<String, Object>> of(HttpStatus status, String message, String errorCode,
                                                  Object... extras) {
        return ResponseEntity.status(status).body(body(message, errorCode, extras));
    }

    /**
     * The single answer for "we are not signing you in and we will not say why".
     * Constant by construction: an unregistered address and a wrong password must be
     * indistinguishable.
     */
    static ResponseEntity<Map<String, Object>> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(body("Invalid email or password", CODE_INVALID_CREDENTIALS));
    }

    /**
     * The account exists and the password was right, but the address is unverified.
     * The flag is what a client branches on to show a "resend link" button.
     */
    static ResponseEntity<Map<String, Object>> emailVerificationRequired() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(
                "Please verify your email address before signing in. Check your inbox for the "
                        + "verification link — we can send a new one if it has expired.",
                CODE_EMAIL_VERIFICATION_REQUIRED,
                "emailVerificationRequired", true,
                "resendVerificationPath", RESEND_VERIFICATION_PATH));
    }

    /**
     * The password was right but the account is not ACTIVE. The old text was
     * "User account is inactive", which told the user nothing about what to do; each
     * status now names who can restore access. {@code accountStatus} keeps the state
     * machine-readable so a client need not parse the sentence.
     */
    static ResponseEntity<Map<String, Object>> accountNotActive(User user) {
        UserStatus status = user.getStatus();
        String message = switch (status) {
            case SUSPENDED -> "Your account has been suspended. Ask an administrator in your "
                    + "organisation to restore your access.";
            case TERMINATED -> "This account has been closed. Ask an administrator in your "
                    + "organisation to reopen it if you still need access.";
            default -> "Your account is not active yet. Ask an administrator in your organisation "
                    + "to activate it, then sign in again.";
        };
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(
                message,
                CODE_ACCOUNT_NOT_ACTIVE,
                "accountStatus", status.name()));
    }

    /**
     * Too many failed attempts. The message names a way out that does not require
     * waiting — a password reset clears the lockout (see {@code AuthController#resetPassword})
     * — and {@code retryAfterSeconds} lets a client run a countdown instead of parsing
     * an instant out of English.
     */
    static ResponseEntity<Map<String, Object>> lockedOut(User user) {
        Instant until = user.getLockedUntil();
        long retryAfterSeconds = until == null
                ? 0
                : Math.max(0, Duration.between(Instant.now(), until).getSeconds());
        // Prefix preserved verbatim: existing clients and tests match on it.
        String message = "Account temporarily locked due to too many failed attempts. "
                + "Try again after " + until
                + ", or reset your password now to regain access immediately.";
        return ResponseEntity.status(HttpStatus.LOCKED).body(body(
                message,
                CODE_ACCOUNT_LOCKED,
                "accountLocked", true,
                "lockedUntil", until == null ? null : until.toString(),
                "retryAfterSeconds", retryAfterSeconds));
    }
}
