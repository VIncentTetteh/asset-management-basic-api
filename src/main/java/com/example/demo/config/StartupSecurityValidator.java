package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Validates critical security configuration at startup.
 * Prevents the application from starting with insecure defaults in production.
 * Skipped in dev profile via app.startup.skip-secret-validation=true.
 */
@Component
public class StartupSecurityValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSecurityValidator.class);

    private static final String DEFAULT_JWT_SECRET =
            "this-default-secret-must-be-at-least-32-characters-long-change-in-production";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${paystack.secret.key:}")
    private String paystackSecretKey;

    @Value("${app.startup.skip-secret-validation:false}")
    private boolean skipValidation;

    @Override
    public void run(ApplicationArguments args) {
        if (skipValidation) {
            log.warn("[SECURITY] Startup secret validation is DISABLED. This must NOT be used in production.");
            return;
        }

        validateJwtSecret();
        validatePaystackKey();

        log.info("[SECURITY] Startup security validation passed.");
    }

    private void validateJwtSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                "[SECURITY STARTUP FAILURE] APP_JWT_SECRET environment variable is not set. " +
                "The application cannot start without a secure JWT secret.");
        }
        if (jwtSecret.equals(DEFAULT_JWT_SECRET)) {
            throw new IllegalStateException(
                "[SECURITY STARTUP FAILURE] APP_JWT_SECRET is set to the default insecure value. " +
                "Set a cryptographically random secret of at least 32 characters before deploying.");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException(
                "[SECURITY STARTUP FAILURE] APP_JWT_SECRET must be at least 32 characters long. " +
                "Current length: " + jwtSecret.length());
        }
    }

    private void validatePaystackKey() {
        if (paystackSecretKey == null || paystackSecretKey.isBlank()) {
            throw new IllegalStateException(
                "[SECURITY STARTUP FAILURE] PAYSTACK_SECRET_KEY environment variable is not set. " +
                "Billing functionality requires a valid Paystack secret key.");
        }
        if (!paystackSecretKey.startsWith("sk_live_") && !paystackSecretKey.startsWith("sk_test_")) {
            log.warn("[SECURITY] PAYSTACK_SECRET_KEY does not look like a valid Paystack key (expected sk_live_* or sk_test_*).");
        }
    }
}
