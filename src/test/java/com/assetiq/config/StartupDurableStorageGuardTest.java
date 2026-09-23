package com.assetiq.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the durable-storage check on {@code StartupSecurityValidator}.
 *
 * <p>There are two durable backends — S3 for the hosted deployment, the filesystem
 * for a self-hosted one — and one that is not durable at all:
 * {@code InMemoryFileStorageService}, a {@code ConcurrentHashMap} holding whole file
 * bodies in the heap. A single long-lived instance on the heap map behaves perfectly,
 * so nothing in testing or a demo reveals the problem; it appears in production as
 * documents vanishing across a redeploy, 404s that depend on which replica answered,
 * and unbounded heap growth. That combination — invisible in every environment short
 * of a scaled production one — is why this fails startup rather than logging a warning
 * nobody reads.
 *
 * <p>Follows {@code StartupSecurityValidatorDdlAutoTest}: secret validation is skipped so
 * only the storage branch is exercised.
 */
@DisplayName("Startup durable-storage guard")
class StartupDurableStorageGuardTest {

    private StartupSecurityValidator newValidator(String appMode,
                                                  boolean s3Enabled,
                                                  boolean filesystemEnabled,
                                                  boolean inMemoryEnabled,
                                                  String... activeProfiles) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(activeProfiles);
        StartupSecurityValidator v = new StartupSecurityValidator(env);
        ReflectionTestUtils.setField(v, "ddlAuto", "validate");
        ReflectionTestUtils.setField(v, "skipValidation", true);
        ReflectionTestUtils.setField(v, "jwtSecret", "ignored");
        ReflectionTestUtils.setField(v, "paystackSecretKey", "sk_test_ignored");
        ReflectionTestUtils.setField(v, "appMode", appMode);
        ReflectionTestUtils.setField(v, "s3Enabled", s3Enabled);
        ReflectionTestUtils.setField(v, "filesystemStorageEnabled", filesystemEnabled);
        ReflectionTestUtils.setField(v, "inMemoryStorageEnabled", inMemoryEnabled);
        return v;
    }

    private void run(StartupSecurityValidator v) {
        v.run(mock(ApplicationArguments.class));
    }

    // ── No durable backend at all ─────────────────────────────────────────────

    @Test
    @DisplayName("refuses to boot a cloud deployment with no durable backend")
    void rejectsNoDurableBackendInCloudMode() {
        StartupSecurityValidator v = newValidator("cloud", false, false, false, "prod");

        assertThatThrownBy(() -> run(v))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No durable file storage is configured")
                .hasMessageContaining("APP_STORAGE_S3_ENABLED=true")
                .hasMessageContaining("APP_STORAGE_FILESYSTEM_ENABLED=true");
    }

    @Test
    @DisplayName("refuses to boot a self-hosted deployment with no durable backend either")
    void rejectsNoDurableBackendInStandaloneMode() {
        // Previously exempt: a self-hosted install was allowed to boot onto the heap
        // map because it is single-instance, which only rules out the cross-replica
        // 404s. The restart data loss and unbounded growth applied to it just as much,
        // and it is the deployment shape least likely to have someone watching the logs.
        StartupSecurityValidator v = newValidator("standalone", false, false, false, "prod");

        assertThatThrownBy(() -> run(v))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No durable file storage is configured");
    }

    @Test
    @DisplayName("refuses when no profile is active, since that is not a dev environment")
    void rejectsWhenNoProfileActive() {
        StartupSecurityValidator v = newValidator("cloud", false, false, false);

        assertThatThrownBy(() -> run(v)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("refuses an offline-licence deployment with no durable backend")
    void rejectsOfflineLicenceWithNoDurableBackend() {
        StartupSecurityValidator v = newValidator("cloud", false, false, false, "prod");
        ReflectionTestUtils.setField(v, "offlineLicenceEnabled", true);

        assertThatThrownBy(() -> run(v))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No durable file storage is configured");
    }

    // ── Durable backends ──────────────────────────────────────────────────────

    @Test
    @DisplayName("allows cloud mode with S3 — the hosted default")
    void allowsCloudModeWithS3() {
        StartupSecurityValidator v = newValidator("cloud", true, false, false, "prod");

        assertThatCode(() -> run(v)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("allows self-hosted with the filesystem backend — the self-hosted default")
    void allowsStandaloneWithFilesystem() {
        StartupSecurityValidator v = newValidator("standalone", false, true, false, "prod");

        assertThatCode(() -> run(v)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("allows an offline-licence deployment with the filesystem backend")
    void allowsOfflineLicenceWithFilesystem() {
        StartupSecurityValidator v = newValidator("cloud", false, true, false, "prod");
        ReflectionTestUtils.setField(v, "offlineLicenceEnabled", true);

        assertThatCode(() -> run(v)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("allows a hosted deployment on the filesystem backend, with a warning")
    void allowsHostedWithFilesystem() {
        // Not the supported hosted shape — a local directory is per-pod — but an
        // operator who has mounted a shared volume has made a deliberate choice,
        // and refusing to boot would be worse than saying so in the log.
        StartupSecurityValidator v = newValidator("cloud", false, true, false, "prod");

        assertThatCode(() -> run(v)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("allows both backends enabled at once")
    void allowsBothBackends() {
        StartupSecurityValidator v = newValidator("cloud", true, true, false, "prod");

        assertThatCode(() -> run(v)).doesNotThrowAnyException();
    }

    // ── In-memory opt-in ──────────────────────────────────────────────────────

    @Test
    @DisplayName("refuses to boot with in-memory storage enabled outside a dev profile")
    void rejectsInMemoryOptInOutsideDev() {
        // Even alongside a durable backend: the opt-in is a data-loss switch and has
        // no business being set in a real deployment.
        StartupSecurityValidator v = newValidator("cloud", true, false, true, "prod");

        assertThatThrownBy(() -> run(v))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.storage.in-memory.enabled=true")
                .hasMessageContaining("APP_STORAGE_IN_MEMORY_ENABLED=false");
    }

    @Test
    @DisplayName("refuses in-memory storage in a self-hosted deployment too")
    void rejectsInMemoryOptInStandalone() {
        StartupSecurityValidator v = newValidator("standalone", false, true, true, "prod");

        assertThatThrownBy(() -> run(v))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.storage.in-memory.enabled=true");
    }

    @Test
    @DisplayName("permits in-memory storage under a dev profile")
    void permitsInMemoryStorageInDev() {
        StartupSecurityValidator v = newValidator("cloud", false, false, true, "dev");

        assertThatCode(() -> run(v)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("permits in-memory storage under the test profile, which is what it exists for")
    void permitsInMemoryStorageInTest() {
        StartupSecurityValidator v = newValidator("cloud", false, false, true, "test");

        assertThatCode(() -> run(v)).doesNotThrowAnyException();
    }
}
