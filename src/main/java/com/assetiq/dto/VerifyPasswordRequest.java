package com.assetiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/v1/auth/verify-password and DELETE /api/v1/users/me: the
 * caller's own password, re-entered to gate a destructive action.
 *
 * <p>The value is never logged, never echoed, and never stored. The upper bound
 * is a denial-of-service guard, not a password rule: bcrypt's cost is paid on
 * whatever it is handed.
 */
public record VerifyPasswordRequest(
        @NotBlank(message = "Password is required")
        @Size(max = 200, message = "Password is too long")
        String password
) {
    @Override
    public String toString() {
        // Records print every component by default, which would put the password
        // into any log line or exception message that touches this object.
        return "VerifyPasswordRequest[password=***]";
    }
}
