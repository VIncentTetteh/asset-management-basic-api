package com.assetiq.security.sso;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;

/**
 * Proof Key for Code Exchange (RFC 7636) for the mobile SSO handoff.
 *
 * <p>The SSO callback hands the app a one-time exchange code through a custom URL
 * scheme. Any app on the device can register the same scheme, so whoever wins the
 * race can redeem the code and walk away with a session. PKCE closes that: the
 * app keeps a random verifier to itself and only ever publishes its SHA-256
 * hash, so a stolen code is worthless without the verifier that never left the
 * device.
 *
 * <p>Only {@code S256} is accepted. {@code plain} publishes the verifier as the
 * challenge and therefore proves nothing at all.
 */
public final class Pkce {

    public static final String S256 = "S256";

    /** RFC 7636 §4.1: the verifier is 43–128 characters of unreserved ASCII. */
    private static final int MIN_VERIFIER_LENGTH = 43;
    private static final int MAX_VERIFIER_LENGTH = 128;

    private Pkce() {
    }

    /** True for {@code S256} (case-insensitively), and for an omitted method, which means S256. */
    public static boolean isSupportedMethod(String method) {
        return method == null || method.isBlank() || S256.equalsIgnoreCase(method.trim());
    }

    /** The challenge a verifier produces: base64url(SHA-256(verifier)), unpadded. */
    public static String challengeFor(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 is mandatory in every Java SE implementation.
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /**
     * Whether a verifier proves possession of a challenge.
     *
     * <p>Compared with a constant-time equality so a near-miss cannot be walked
     * character by character, and normalised for padding because clients differ on
     * whether they strip the {@code =}.
     */
    public static boolean matches(String codeChallenge, String codeVerifier) {
        if (codeChallenge == null || codeChallenge.isBlank() || codeVerifier == null) {
            return false;
        }
        String verifier = codeVerifier.trim();
        if (verifier.length() < MIN_VERIFIER_LENGTH || verifier.length() > MAX_VERIFIER_LENGTH) {
            return false;
        }
        byte[] expected = normalise(codeChallenge).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = normalise(challengeFor(verifier)).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    /** Accepts a challenge sent with padding, or in the base64 (rather than base64url) alphabet. */
    private static String normalise(String challenge) {
        return challenge.trim()
                .replace('+', '-')
                .replace('/', '_')
                .replaceAll("=+$", "");
    }

    /** A challenge is only usable if it is non-blank and looks like base64url. */
    public static boolean isWellFormedChallenge(String codeChallenge) {
        if (codeChallenge == null) {
            return false;
        }
        String value = normalise(codeChallenge);
        return !value.isEmpty()
                && value.length() <= 128
                && value.toLowerCase(Locale.ROOT).matches("[a-z0-9\\-_]+");
    }
}
