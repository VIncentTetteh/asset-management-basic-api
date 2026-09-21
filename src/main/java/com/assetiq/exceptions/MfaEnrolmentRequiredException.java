package com.assetiq.exceptions;

/**
 * A fresh-MFA action (or step-up) was attempted by a user who has not enrolled
 * an authenticator, so no amount of retrying can succeed until they do.
 *
 * <p>Rendered as 428 Precondition Required {@code MFA_ENROLMENT_REQUIRED}.
 */
public class MfaEnrolmentRequiredException extends RuntimeException {
    public MfaEnrolmentRequiredException(String message) {
        super(message);
    }
}
