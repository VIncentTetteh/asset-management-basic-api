package com.assetiq.validation;

import java.nio.charset.StandardCharsets;

/**
 * The one password policy, applied by {@link ValidPassword} to every place a
 * password is chosen: tenant registration, {@code /auth/register}, password
 * reset, admin-created users, accepting an invitation, and self-service
 * password change. The web app
 * mirrors these numbers in {@code src/lib/field-limits.ts}.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    /**
     * BCrypt reads at most 72 bytes; anything after is silently ignored, so a
     * longer password would give a false sense of strength. Counted in UTF-8 bytes.
     */
    public static final int MAX_BYTES = 72;

    public static final String MESSAGE = "Password must be at least " + MIN_LENGTH
            + " characters and at most " + MAX_BYTES + " bytes (72 plain characters)";

    private PasswordPolicy() {
    }

    /** Null is left to {@code @NotBlank}; otherwise the length rules above. */
    public static boolean isValid(String password) {
        if (password == null) return true;
        return password.length() >= MIN_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
