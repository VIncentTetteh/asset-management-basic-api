package com.assetiq.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Paystack webhook HMAC-SHA512 signature validation.
 *
 * Paystack's verification algorithm:
 *   expected = hex( HMAC-SHA512(secretKey, rawRequestBody) )
 *   valid    = expected.equalsIgnoreCase(X-Paystack-Signature header)
 *
 * These tests exercise the algorithm directly (no Spring context needed).
 * If a dedicated WebhookSignatureValidator bean is added in future, wire it
 * in here instead.
 */
@DisplayName("Paystack Webhook Signature Verification")
class PaystackWebhookSignatureTest {

    private static final String SECRET  = "sk_test_abc123verysecretkey";
    private static final String PAYLOAD = "{\"event\":\"charge.success\",\"data\":{\"amount\":9900}}";

    @Test
    @DisplayName("valid signature passes verification")
    void validSignature_passes() throws Exception {
        String sig = hmacSha512Hex(SECRET, PAYLOAD);
        assertThat(verify(SECRET, PAYLOAD, sig)).isTrue();
    }

    @Test
    @DisplayName("tampered payload fails verification")
    void tamperedPayload_fails() throws Exception {
        String sig     = hmacSha512Hex(SECRET, PAYLOAD);
        String tampered = PAYLOAD.replace("9900", "1");
        assertThat(verify(SECRET, tampered, sig)).isFalse();
    }

    @Test
    @DisplayName("wrong secret fails verification")
    void wrongSecret_fails() throws Exception {
        String sig = hmacSha512Hex(SECRET, PAYLOAD);
        assertThat(verify("wrong_secret", PAYLOAD, sig)).isFalse();
    }

    @Test
    @DisplayName("empty signature string fails verification")
    void emptySignature_fails() {
        assertThat(verify(SECRET, PAYLOAD, "")).isFalse();
    }

    @Test
    @DisplayName("null signature fails verification")
    void nullSignature_fails() {
        assertThat(verify(SECRET, PAYLOAD, null)).isFalse();
    }

    @Test
    @DisplayName("signature comparison is case-insensitive (hex case variance)")
    void caseInsensitiveComparison_passes() throws Exception {
        String sig = hmacSha512Hex(SECRET, PAYLOAD).toUpperCase();
        assertThat(verify(SECRET, PAYLOAD, sig)).isTrue();
    }

    // ---- Algorithm helpers ----

    /**
     * Replicates the server-side HMAC-SHA512 verification.
     * Production code should delegate to a dedicated validator bean.
     */
    private static boolean verify(String secret, String payload, String signature) {
        if (signature == null || signature.isBlank()) return false;
        try {
            return hmacSha512Hex(secret, payload).equalsIgnoreCase(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static String hmacSha512Hex(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
