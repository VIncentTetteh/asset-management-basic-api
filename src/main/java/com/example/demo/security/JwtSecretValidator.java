package com.example.demo.security;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class JwtSecretValidator {

    private static final int MIN_BYTES = 32;  // 256 bits for HS256

    /**
     * Validate JWT secret has sufficient entropy.
     */
    public static void validateSecretEntropy(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be null or blank");
        }

        byte[] bytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        if (bytes.length < MIN_BYTES) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least " + MIN_BYTES + " bytes. Current: " + bytes.length);
        }

        // Check for low entropy (e.g., all same character or simple patterns)
        int uniqueBytes = 0;
        boolean[] seen = new boolean[256];
        for (byte b : bytes) {
            int val = b & 0xFF;
            if (!seen[val]) {
                seen[val] = true;
                uniqueBytes++;
            }
        }
        if (uniqueBytes < 8) {
            throw new IllegalArgumentException(
                    "JWT secret has insufficient entropy. Only " + uniqueBytes +
                    " unique byte values found. Use cryptographically random string.");
        }

        // Warn if secret is ASCII-only (lower entropy than binary)
        boolean isAsciiOnly = true;
        for (byte b : bytes) {
            if (b < 32 || b >= 127) {
                isAsciiOnly = false;
                break;
            }
        }
        if (isAsciiOnly) {
            double estimatedBits = 6.5 * bytes.length;  // ~6.5 bits per ASCII char
            if (estimatedBits < 128) {
                throw new IllegalArgumentException(
                        "JWT secret is ASCII-only with insufficient entropy (~" +
                        (int) estimatedBits + " bits vs 256 required)");
            }
        }
    }

    /**
     * Generate a cryptographically secure JWT secret.
     * Returns Base64-encoded string of `bytes` random bytes.
     */
    public static String generateSecureSecret(int bytes) {
        if (bytes < MIN_BYTES) {
            throw new IllegalArgumentException("Must generate at least " + MIN_BYTES + " bytes");
        }

        SecureRandom random = new SecureRandom();
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return Base64.getEncoder().encodeToString(buffer);
    }

    /**
     * Main method for CLI usage.
     * Run: java -cp target/demo-0.0.1-SNAPSHOT.jar com.example.demo.security.JwtSecretValidator
     */
    public static void main(String[] args) {
        System.out.println("=== JWT Secret Generator ===\n");

        int numSecrets = args.length > 0 ? Integer.parseInt(args[0]) : 1;

        for (int i = 0; i < numSecrets; i++) {
            String secret = generateSecureSecret(32);
            System.out.println("Secret #" + (i + 1) + ":");
            System.out.println("  Value: " + secret);
            System.out.println("  Length: " + secret.length() + " characters (32 bytes)");
            System.out.println();
        }

        System.out.println("To use in environment:");
        System.out.println("  export APP_JWT_SECRET=" + generateSecureSecret(32));
        System.out.println("\nOr in Docker:");
        System.out.println("  docker run -e APP_JWT_SECRET=" + generateSecureSecret(32) + " ...");
    }
}


