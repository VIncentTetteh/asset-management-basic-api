package com.assetiq.exceptions;

/** A submitted TOTP code did not verify. Rendered as 401 {@code MFA_CODE_INVALID}. */
public class MfaCodeInvalidException extends RuntimeException {
    public MfaCodeInvalidException(String message) {
        super(message);
    }
}
