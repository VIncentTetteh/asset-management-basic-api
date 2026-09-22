package com.assetiq.models;

import java.util.Locale;
import java.util.UUID;

/**
 * The one generator of user employee ids ({@code EMP-} plus 10 random hex
 * characters), used by tenant registration, {@code /auth/register} and the Users
 * screen, and as the {@link User} default so the NOT NULL mapping always holds.
 */
public final class EmployeeIds {

    static final String PREFIX = "EMP-";
    private static final int RANDOM_CHARS = 10;

    private EmployeeIds() {
    }

    public static String generate() {
        return PREFIX + UUID.randomUUID().toString().replace("-", "")
                .substring(0, RANDOM_CHARS).toUpperCase(Locale.ROOT);
    }

    /** The supplied id trimmed, or a generated one when it is blank. */
    public static String orGenerate(String supplied) {
        return supplied == null || supplied.isBlank() ? generate() : supplied.trim();
    }
}
