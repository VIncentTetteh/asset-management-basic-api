package com.assetiq.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * One-time credentials that are emailed to someone and later presented back.
 *
 * <p>Two rules this exists to keep in one place:
 * <ol>
 *   <li>The token is 256 bits from {@link SecureRandom}. Nothing about it is
 *       derived from the thing it grants access to — not the row id, not the
 *       address, not the clock — so it cannot be guessed from anything an
 *       attacker can see or enumerate.</li>
 *   <li>Only {@link #sha256Hex(String)} of it is ever persisted. A database dump
 *       therefore yields no usable links, and lookups still hit an index.</li>
 * </ol>
 *
 * <p>Comparison is by hash equality on a unique column, so there is no
 * secret-dependent branch here to time.
 */
public final class SecureTokens {

    /** 32 bytes: the same strength as the signup-verification links already in use. */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    /** A fresh URL-safe token. Never log the return value. */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The lowercase hex SHA-256 digest that is safe to store and to index. */
    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
