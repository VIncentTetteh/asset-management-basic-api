package com.assetiq.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Seals a replacement refresh token so that only the holder of the token it replaced
 * can read it back.
 *
 * <p>The refresh session table stores hashes, never tokens, and that must stay true:
 * a database dump must not yield anything a thief could present. But the replay grace
 * window needs the server to hand back the <em>same</em> replacement token a moment
 * later, rather than minting a second one and forking the family.
 *
 * <p>The way out is to key the envelope on material the server does not keep. The
 * replacement is encrypted with AES-256-GCM under a key derived from the <em>raw</em>
 * token being consumed; the row keeps only that token's SHA-256. The server alone
 * therefore cannot open the envelope. A caller that presents the consumed token can —
 * and that caller already held a token good enough to rotate, so the envelope grants
 * it nothing it did not have seconds earlier.
 */
public final class RefreshReplaySeal {

    private static final String PREFIX = "rpl:v1:";
    private static final byte[] DOMAIN = "assetiq-refresh-replay-v1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshReplaySeal() {}

    /** Seals {@code replacement} under a key derived from {@code rawConsumedToken}. */
    public static String seal(String rawConsumedToken, String replacement) {
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keyFor(rawConsumedToken), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(replacement.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, packed, 0, nonce.length);
            System.arraycopy(ciphertext, 0, packed, nonce.length, ciphertext.length);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
        } catch (Exception failure) {
            throw new IllegalStateException("Failed to seal refresh replacement", failure);
        }
    }

    /**
     * Opens an envelope, or returns null when it cannot be opened for any reason —
     * absent, written by an older format, tampered with, or sealed under a different
     * token. Callers treat null as "no replay available" and fall back to reuse
     * detection, so a damaged envelope fails closed rather than granting anything.
     */
    public static String unseal(String rawConsumedToken, String sealed) {
        if (rawConsumedToken == null || sealed == null || !sealed.startsWith(PREFIX)) return null;
        try {
            byte[] packed = Base64.getUrlDecoder().decode(sealed.substring(PREFIX.length()));
            if (packed.length < 29) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keyFor(rawConsumedToken),
                    new GCMParameterSpec(128, Arrays.copyOfRange(packed, 0, 12)));
            byte[] plaintext = cipher.doFinal(Arrays.copyOfRange(packed, 12, packed.length));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception unopenable) {
            return null;
        }
    }

    private static SecretKeySpec keyFor(String rawConsumedToken) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(DOMAIN);
        digest.update((byte) 0);
        digest.update(rawConsumedToken.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest.digest(), "AES");
    }
}
