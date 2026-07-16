package com.assetiq.security;

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
     * CLI utility — generates a cryptographically secure JWT secret and prints it
     * <em>only to stderr</em> so that container log aggregators (which typically
     * capture stdout) do not inadvertently record the secret.
     *
     * Usage:
     *   java -cp target/assetIQ-0.0.1-SNAPSHOT.jar com.assetiq.security.JwtSecretValidator [count]
     *
     * IMPORTANT: Run this interactively in a trusted terminal. Never pipe the
     * output into a log file or CI artifact.  Treat the generated value as a
     * secret credential and store it in your secrets manager (AWS Secrets Manager,
     * HashiCorp Vault, etc.) immediately.
     */
    public static void main(String[] args) {
        // Use stderr intentionally — stdout is frequently captured by container
        // runtimes (docker logs, kubectl logs) and CI pipelines.
        java.io.PrintStream out = System.err;

        out.println("=== JWT Secret Generator ===");
        out.println("NOTE: Output goes to stderr to avoid capture by log aggregators.\n");

        int numSecrets = args.length > 0 ? Integer.parseInt(args[0]) : 1;

        for (int i = 0; i < numSecrets; i++) {
            String secret = generateSecureSecret(32);
            out.println("Secret #" + (i + 1) + ":");
            out.println("  Value:  " + secret);
            out.println("  Length: " + secret.length() + " chars (" + (secret.length() * 6 / 8) + " bytes entropy)");
            out.println();
        }

        out.println("Store in your secrets manager, then set:");
        out.println("  export APP_JWT_SECRET=<generated value above>");
        out.println();
        out.println("NEVER commit the value to source control or paste it into CI logs.");
    }
}


