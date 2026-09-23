package com.assetiq.config;

import com.assetiq.security.JwtSecretValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Validates critical security and data-integrity configuration at startup.
 * Prevents the application from starting with insecure defaults in production.
 * Skipped in dev profile via app.startup.skip-secret-validation=true.
 */
@Component
public class StartupSecurityValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSecurityValidator.class);

    /** Profiles under which we tolerate Hibernate mutating the schema. */
    private static final Set<String> DDL_MUTATION_ALLOWED_PROFILES =
            Set.of("dev", "test", "local");

    /** Hibernate ddl-auto values that MUTATE the schema. */
    private static final Set<String> DDL_MUTATING_VALUES =
            Set.of("create", "create-drop", "update");

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${paystack.secret.key:}")
    private String paystackSecretKey;

    @Value("${app.security.data-encryption-key:}")
    private String dataEncryptionKey;

    @Value("${app.startup.skip-secret-validation:false}")
    private boolean skipValidation;

    @Value("${spring.jpa.hibernate.ddl-auto:validate}")
    private String ddlAuto;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.mode:cloud}")
    private String appMode;

    /** Self-hosted SKU. See {@link #validatePaystackKey()}. */
    @Value("${app.license.offline.enabled:false}")
    private boolean offlineLicenceEnabled;

    @Value("${app.storage.s3.enabled:false}")
    private boolean s3Enabled;

    @Value("${app.storage.filesystem.enabled:false}")
    private boolean filesystemStorageEnabled;

    /** Explicit, deliberate opt-in to the heap-backed storage. Tests and dev only. */
    @Value("${app.storage.in-memory.enabled:false}")
    private boolean inMemoryStorageEnabled;

    private final Environment environment;

    public StartupSecurityValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Schema-mutation check runs ALWAYS, even when secret validation is skipped.
        // Never allow Hibernate to silently mutate a production schema.
        validateDdlAuto();

        // Durability check also runs always: losing generated files is a data-loss
        // class of failure, not a security-configuration one.
        validateDurableStorage();
        validateInMemoryStorageOptIn();

        if (skipValidation) {
            log.warn("[SECURITY] Startup secret validation is DISABLED. This must NOT be used in production.");
            return;
        }

        validateJwtSecret();
        validateDataEncryptionKey();
        validatePaystackKey();

        // Warn if email is disabled — forgot-password and DSAR acknowledgements will silently fail
        if (!emailEnabled && "cloud".equalsIgnoreCase(appMode)) {
            log.warn("⚠ [STARTUP] Email is disabled (app.email.enabled=false). " +
                     "Password reset, user invitations, and DSAR acknowledgement emails will NOT be sent. " +
                     "Set APP_EMAIL_ENABLED=true and configure SMTP_HOST/SMTP_USERNAME/SMTP_PASSWORD.");
        }

        log.info("[SECURITY] ✓ Startup security validation passed.");
    }

    /**
     * Enforces that Flyway owns schema changes in every non-dev environment.
     * P0-3: refuse to boot with ddl-auto=update (or create / create-drop) unless
     * the active profile is explicitly dev/test/local.
     */
    private void validateDdlAuto() {
        String value = ddlAuto == null ? "" : ddlAuto.trim().toLowerCase(Locale.ROOT);
        if (!DDL_MUTATING_VALUES.contains(value)) {
            log.info("[SECURITY] ✓ Hibernate ddl-auto='{}' (non-mutating)", value);
            return;
        }

        boolean devProfileActive = Arrays.stream(environment.getActiveProfiles())
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(DDL_MUTATION_ALLOWED_PROFILES::contains);

        if (!devProfileActive) {
            throw new IllegalStateException(
                "[SECURITY STARTUP FAILURE] Hibernate ddl-auto='" + value + "' is NOT allowed outside a dev profile.\n" +
                "Flyway must own schema migrations in staging and production.\n" +
                "Fix: set SPRING_JPA_HIBERNATE_DDL_AUTO=validate (the default) or activate profile 'dev'.\n" +
                "Current active profiles: " + Arrays.toString(environment.getActiveProfiles()));
        }
        log.warn("[SECURITY] Hibernate ddl-auto='{}' permitted because active profile is dev. Never ship this.", value);
    }

    /**
     * Refuses to boot any deployment whose uploaded files would not survive a restart.
     *
     * <p>There are two durable backends. {@code app.storage.s3.enabled} is the
     * default for the hosted deployment: it is the only one that is correct
     * behind more than one replica, because a local directory is per-pod.
     * {@code app.storage.filesystem.enabled} is the default for a self-hosted
     * installation, where the base directory is a mounted volume.</p>
     *
     * <p>{@code InMemoryFileStorageService} is neither. It is a
     * {@code ConcurrentHashMap} holding whole file bodies in the JVM heap, and
     * using it in a deployment that stores real data is three faults at once:
     * every uploaded document, generated report and import is lost on restart or
     * redeploy; a second replica returns 404 for a file the first one produced,
     * because the map is per-process; and the map is never evicted, so it is an
     * unbounded heap-growth path to OOM.</p>
     *
     * <p>None of that surfaces in testing — a single instance that is never
     * restarted behaves correctly — which is exactly why this is a startup check
     * rather than a note in a runbook. Dev, test and local profiles are exempt,
     * matching {@link #validateDdlAuto()}.</p>
     */
    private void validateDurableStorage() {
        if (s3Enabled) {
            if (filesystemStorageEnabled) {
                log.info("[STORAGE] S3 and filesystem storage are both enabled. "
                         + "S3 takes precedence; the filesystem backend serves organisations "
                         + "whose storage config opts out of S3.");
            } else {
                log.info("[STORAGE] ✓ Durable storage: S3 (app.storage.s3.enabled=true)");
            }
            return;
        }

        if (filesystemStorageEnabled) {
            if (isHostedDeployment()) {
                log.warn("[STORAGE] Hosted deployment is using filesystem storage rather than S3. "
                         + "app.storage.filesystem.base-dir MUST be a volume shared by every "
                         + "replica and included in backups, or files will 404 depending on "
                         + "which replica answers. S3 is the supported hosted configuration: "
                         + "set APP_STORAGE_S3_ENABLED=true with APP_STORAGE_S3_BUCKET.");
            } else {
                log.info("[STORAGE] ✓ Durable storage: filesystem "
                         + "(app.storage.filesystem.enabled=true)");
            }
            return;
        }

        boolean devProfileActive = Arrays.stream(environment.getActiveProfiles())
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(DDL_MUTATION_ALLOWED_PROFILES::contains);

        if (devProfileActive) {
            log.warn("[STORAGE] No durable file storage is configured. Permitted because the "
                     + "active profile is dev/test/local. Uploaded files will not survive a "
                     + "restart. Never ship this.");
            return;
        }

        throw new IllegalStateException(
            "[STARTUP FAILURE] No durable file storage is configured.\n"
            + "Both app.storage.s3.enabled and app.storage.filesystem.enabled are false, so "
            + "uploaded documents, generated reports and import files would be held in this "
            + "JVM's heap: lost on every restart, invisible to other replicas, and never evicted.\n"
            + "Fix (hosted): set APP_STORAGE_S3_ENABLED=true with APP_STORAGE_S3_BUCKET and "
            + "APP_STORAGE_S3_REGION.\n"
            + "Fix (self-hosted): set APP_STORAGE_FILESYSTEM_ENABLED=true with "
            + "APP_STORAGE_FILESYSTEM_BASE_DIR pointing at a persistent volume.\n"
            + "Current active profiles: " + Arrays.toString(environment.getActiveProfiles()));
    }

    /**
     * Refuses to boot a real deployment that has opted into the heap-backed
     * storage. The opt-in exists for the test suite and for a throwaway local
     * run; anywhere else it is a data-loss switch, so it is only honoured under
     * a dev, test or local profile.
     */
    private void validateInMemoryStorageOptIn() {
        if (!inMemoryStorageEnabled) {
            return;
        }
        boolean devProfileActive = Arrays.stream(environment.getActiveProfiles())
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(DDL_MUTATION_ALLOWED_PROFILES::contains);
        if (devProfileActive) {
            log.warn("[STORAGE] In-memory file storage is enabled under a dev/test profile. "
                     + "Uploaded files live in this JVM's heap and are lost on restart.");
            return;
        }
        throw new IllegalStateException(
            "[STARTUP FAILURE] app.storage.in-memory.enabled=true outside a dev/test/local profile.\n"
            + "In-memory storage holds whole file bodies in the JVM heap: every uploaded document "
            + "is lost on restart, invisible to other replicas, and never evicted. It exists for "
            + "the test suite only.\n"
            + "Fix: set APP_STORAGE_IN_MEMORY_ENABLED=false and configure a durable backend — "
            + "APP_STORAGE_S3_ENABLED=true (hosted) or APP_STORAGE_FILESYSTEM_ENABLED=true "
            + "(self-hosted).\n"
            + "Current active profiles: " + Arrays.toString(environment.getActiveProfiles()));
    }

    /**
     * {@code true} for the vendor-hosted SaaS, {@code false} for an installation
     * the customer runs themselves.
     *
     * <p>Two signals mean self-hosted, and either is sufficient. {@code APP_MODE}
     * is the older one, but setting it also switches on the legacy licence path
     * that calls a vendor licence server, so the offline-licence flag has to
     * stand on its own: a self-hosted deployment must be able to declare itself
     * without opting into a call-home it was specifically designed to avoid.</p>
     *
     * <p>What hangs on this: a self-hosted installation has no Paystack account
     * and, by design, stores uploads on a mounted volume rather than S3. Applying
     * the hosted checks to it would refuse to boot over two things that are
     * correct for it.</p>
     */
    private boolean isHostedDeployment() {
        return "cloud".equalsIgnoreCase(appMode) && !offlineLicenceEnabled;
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
                "com.assetiq.security.JwtSecretValidator");
        }
    }

    private void validatePaystackKey() {
        // A self-hosted installation has no Paystack account and no hosted
        // billing: entitlement comes from its offline licence key, and the
        // vendor collects payment out of band. Demanding a Paystack secret
        // there would force every operator to invent a fake one to boot, which
        // is worse than not asking. Cloud deployments stay strict.
        if (!isHostedDeployment()) {
            log.info("[SECURITY] Paystack key not required in this deployment mode " +
                     "(appMode={}, offlineLicence={})", appMode, offlineLicenceEnabled);
            return;
        }

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

    private void validateDataEncryptionKey() {
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(dataEncryptionKey == null ? "" : dataEncryptionKey);
            if (decoded.length != 32) throw new IllegalArgumentException("key length");
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException(
                    "[SECURITY STARTUP FAILURE] APP_DATA_ENCRYPTION_KEY must be Base64 for exactly 32 random bytes. " +
                    "Generate with: openssl rand -base64 32");
        }
        log.info("[SECURITY] ✓ data-encryption key validation passed");
    }
}
