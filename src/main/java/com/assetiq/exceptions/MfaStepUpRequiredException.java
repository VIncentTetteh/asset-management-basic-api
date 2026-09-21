package com.assetiq.exceptions;

/**
 * The caller is signed in and has MFA enrolled, but the access token carries no
 * recent {@code mfaAuthenticatedAt} proof for a {@code @RequireFreshMfa} action.
 *
 * <p>Rendered as 401 {@code MFA_STEP_UP_REQUIRED} so the client can prompt for an
 * authenticator code and call {@code POST /api/v1/mfa/step-up}, rather than a
 * generic 403 that reads as "you are not allowed to do this".
 */
public class MfaStepUpRequiredException extends RuntimeException {
    public MfaStepUpRequiredException(String message) {
        super(message);
    }
}
