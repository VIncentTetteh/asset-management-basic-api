package com.assetiq.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

@Service
public class WebhookSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureValidator.class);
    private final String paystackSecret;

    public WebhookSignatureValidator(@Value("${paystack.secret.key}") String paystackSecret) {
        this.paystackSecret = paystackSecret;
    }

    /**
     * Validate Paystack webhook signature.
     * Paystack sends X-Paystack-Signature = SHA512(request_body, secret).
     */
    public boolean isValidPaystackSignature(String requestBody, String signature) {
        if (requestBody == null || signature == null) {
            log.warn("[WEBHOOK] Invalid signature validation: body or signature is null");
            return false;
        }

        try {
            String expectedSignature = computeHmacHex(requestBody, paystackSecret);
            String normalizedSignature = signature.trim().toLowerCase(Locale.ROOT);
            boolean isValid = constantTimeEquals(expectedSignature, normalizedSignature);
            
            if (!isValid) {
                log.warn("[WEBHOOK] Signature validation failed");
            } else {
                log.debug("[WEBHOOK] Signature validation passed");
            }
            
            return isValid;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("[WEBHOOK] Signature computation failed", e);
            return false;
        }
    }

    /**
     * Compute HMAC-SHA512 of data with secret.
     */
    private String computeHmacHex(String data, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return toHexLower(digest);
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        int result = 0;
        int len = Math.max(aBytes.length, bBytes.length);

        for (int i = 0; i < len; i++) {
            byte aByte = i < aBytes.length ? aBytes[i] : 0;
            byte bByte = i < bBytes.length ? bBytes[i] : 0;
            result |= aByte ^ bByte;
        }

        return result == 0;
    }

    private String toHexLower(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = digits[v >>> 4];
            hex[i * 2 + 1] = digits[v & 0x0F];
        }
        return new String(hex);
    }
}
