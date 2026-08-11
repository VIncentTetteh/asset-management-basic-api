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
 * <p>{@code app.storage.s3.enabled} defaults to {@code false} and
 * {@code OrgAwareStorageService} falls back to an in-heap {@code ConcurrentHashMap} holding
 * whole file bodies. A single long-lived instance behaves perfectly, so nothing in testing
 * or a demo reveals the problem; it appears in production as reports vanishing across a
 * redeploy, 404s that depend on which replica answered, and unbounded heap growth. That
 * combination — invisible in every environment short of a scaled production one — is why
 * this fails startup rather than logging a warning nobody reads.
 *
 * <p>Follows {@code StartupSecurityValidatorDdlAutoTest}: secret validation is skipped so
 * only the storage branch is exercised.
 */
@DisplayName("Startup durable-storage guard")
class StartupDurableStorageGuardTest {

    private StartupSecurityValidator newValidator(String appMode, boolean s3Enabled, String... activeProfiles) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(activeProfiles);
        StartupSecurityValidator v = new StartupSecurityValidator(env);
        ReflectionTestUtils.setField(v, "ddlAuto", "validate");
        ReflectionTestUtils.setField(v, "skipValidation", true);
        ReflectionTestUtils.setField(v, "jwtSecret", "ignored");
        ReflectionTestUtils.setField(v, "paystackSecretKey", "sk_test_ignored");
        ReflectionTestUtils.setField(v, "appMode", appMode);
        ReflectionTestUtils.setField(v, "s3Enabled", s3Enabled);
        return v;
    }

    @Test
    @DisplayName("refuses to boot a cloud deployment with in-memory storage")
    void rejectsInMemoryStorageInCloudMode() {
        StartupSecurityValidator v = newValidator("cloud", false, "prod");

        assertThatThrownBy(() -> v.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.storage.s3.enabled=false in cloud mode")
                .hasMessageContaining("APP_STORAGE_S3_ENABLED=true");
    }

    @Test
    @DisplayName("refuses when no profile is active, since that is not a dev environment")
    void rejectsWhenNoProfileActive() {
        StartupSecurityValidator v = newValidator("cloud", false);

        assertThatThrownBy(() -> v.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("allows cloud mode once S3 is enabled")
    void allowsCloudModeWithS3() {
        StartupSecurityValidator v = newValidator("cloud", true, "prod");

        assertThatCode(() -> v.run(mock(ApplicationArguments.class))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("allows standalone mode, which runs one instance against a mounted volume")
    void allowsStandaloneMode() {
        // Self-hosted deployments are single-instance by design and back their storage with
        // a host volume, so the multi-replica and restart concerns do not apply.
        StartupSecurityValidator v = newValidator("standalone", false, "prod");

        assertThatCode(() -> v.run(mock(ApplicationArguments.class))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("permits in-memory storage under a dev profile")
    void permitsInMemoryStorageInDev() {
        StartupSecurityValidator v = newValidator("cloud", false, "dev");

        assertThatCode(() -> v.run(mock(ApplicationArguments.class))).doesNotThrowAnyException();
    }
}
