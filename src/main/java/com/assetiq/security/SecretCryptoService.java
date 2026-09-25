package com.assetiq.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM protection for application secrets persisted in the database. */
@Service
public class SecretCryptoService {

    private static final String PREFIX = "enc:v1:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] key;

    public SecretCryptoService(@Value("${app.security.data-encryption-key:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            this.key = null;
            return;
        }
        try {
            this.key = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("APP_DATA_ENCRYPTION_KEY must be valid Base64", invalid);
        }
        if (this.key.length != 32) {
            throw new IllegalStateException("APP_DATA_ENCRYPTION_KEY must decode to exactly 32 bytes");
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return plaintext;
        if (plaintext.startsWith(PREFIX)) return plaintext;
        requireKey();
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, packed, 0, nonce.length);
            System.arraycopy(ciphertext, 0, packed, nonce.length, ciphertext.length);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
        } catch (Exception failure) {
            throw new IllegalStateException("Failed to encrypt database secret", failure);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank() || !stored.startsWith(PREFIX)) {
            return stored; // backward-compatible read; the next secret update encrypts it
        }
        requireKey();
        try {
            byte[] packed = Base64.getUrlDecoder().decode(stored.substring(PREFIX.length()));
            if (packed.length < 29) throw new IllegalArgumentException("Encrypted secret is truncated");
            byte[] nonce = java.util.Arrays.copyOfRange(packed, 0, 12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(packed, 12, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception failure) {
            throw new IllegalStateException("Failed to decrypt database secret", failure);
        }
    }

    public boolean isConfigured() {
        return key != null;
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("APP_DATA_ENCRYPTION_KEY is required for protected secret operations");
        }
    }
}
