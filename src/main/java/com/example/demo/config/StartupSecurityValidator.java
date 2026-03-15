package com.example.demo.config;

import com.example.demo.security.JwtSecretValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Validates critical security configuration at startup.
 * Prevents the application from starting with insecure defaults in production.
 * Skipped in dev profile via app.startup.skip-secret-validation=true.
 */
@Component
public class StartupSecurityValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSecurityValidator.class);

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

        log.info("[SECURITY] ✓ Startup security validation passed.");
    }

    private void validateJwtSecret() {
        try {
            JwtSecretValidator.validateSecretEntropy(jwtSecret);
            log.info("[SECURITY] ✓ JWT secret validation passed");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "[SECURITY STARTUP FAILURE] JWT secret validation failed.\n" +
                "Error: " + e.getMessage() + "\n" +
                "Generate a new secret with:\n" +
                "  java -cp target/demo-0.0.1-SNAPSHOT.jar " +
                "com.example.demo.security.JwtSecretValidator");
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
        } else {
            log.info("[SECURITY] ✓ Paystack key validation passed");
        }
    }
}
