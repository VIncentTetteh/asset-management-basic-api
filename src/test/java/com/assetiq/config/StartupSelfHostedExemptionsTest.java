package com.assetiq.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Startup checks that must not fire on a self-hosted installation.
 *
 * <p>Two of {@code StartupSecurityValidator}'s guards encode assumptions that
 * are true of the vendor-hosted SaaS and false of an installation the customer
 * runs themselves:</p>
 *
 * <ul>
 *   <li><strong>A Paystack secret key is mandatory.</strong> A self-hosted
 *       customer has no Paystack account — entitlement comes from their offline
 *       licence key and the vendor invoices out of band. Demanding one would
 *       force every operator to invent a fake value to get the process to
 *       start, which is worse than not asking.</li>
 *   <li><strong>S3 must be enabled.</strong> Correct for the hosted multi-replica
 *       deployment; for a single-instance self-hosted evaluation it would refuse
 *       to boot over a trade-off the operator is entitled to make.</li>
 * </ul>
 *
 * <p>The flag that exempts them is {@code app.license.offline.enabled}, not only
 * {@code APP_MODE}. That matters: setting {@code APP_MODE=standalone} also
 * switches on the legacy licence path that calls a vendor licence server, so a
 * deployment that wants the offline licence must be able to declare itself
 * self-hosted without opting into the call-home it was designed to avoid.</p>
 */
@DisplayName("Startup self-hosted exemptions")
class StartupSelfHostedExemptionsTest {

    private StartupSecurityValidator validator(String appMode,
                                               boolean offlineLicence,
                                               String paystackKey,
                                               boolean s3Enabled,
                                               boolean skipSecretValidation) {
        return validator(appMode, offlineLicence, paystackKey, s3Enabled,
                         false, skipSecretValidation);
    }

    private StartupSecurityValidator validator(String appMode,
                                               boolean offlineLicence,
                                               String paystackKey,
                                               boolean s3Enabled,
                                               boolean filesystemEnabled,
                                               boolean skipSecretValidation) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[] {"prod"});

        StartupSecurityValidator v = new StartupSecurityValidator(env);
        ReflectionTestUtils.setField(v, "ddlAuto", "validate");
        ReflectionTestUtils.setField(v, "skipValidation", skipSecretValidation);
        // A real 32-byte secret and key, so the JWT and encryption checks pass
        // and only the branch under test can fail.
        ReflectionTestUtils.setField(v, "jwtSecret",
                "9f4c1a7e3b8d5062af91c4e7b3d80a5629f14c8b7e3a0d5f62b9c4178e3a0d5f");
        ReflectionTestUtils.setField(v, "dataEncryptionKey",
                java.util.Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(v, "paystackSecretKey", paystackKey);
        ReflectionTestUtils.setField(v, "appMode", appMode);
        ReflectionTestUtils.setField(v, "offlineLicenceEnabled", offlineLicence);
        ReflectionTestUtils.setField(v, "s3Enabled", s3Enabled);
        ReflectionTestUtils.setField(v, "filesystemStorageEnabled", filesystemEnabled);
        return v;
    }

    private void run(StartupSecurityValidator v) {
        v.run(mock(ApplicationArguments.class));
    }

    @Nested
    @DisplayName("Paystack key")
    class Paystack {

        @Test
        @DisplayName("is still mandatory for the hosted deployment")
        void mandatoryWhenHosted() {
            StartupSecurityValidator v = validator("cloud", false, "", true, false);

            assertThatThrownBy(() -> run(v))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PAYSTACK_SECRET_KEY");
        }

        @Test
        @DisplayName("is not required once the offline licence is enabled")
        void notRequiredWithOfflineLicence() {
            StartupSecurityValidator v = validator("cloud", true, "", true, false);

            assertThatCode(() -> run(v)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("is not required in the legacy standalone mode either")
        void notRequiredInStandaloneMode() {
            StartupSecurityValidator v = validator("standalone", false, "", true, false);

            assertThatCode(() -> run(v)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Durable storage")
    class DurableStorage {

        @Test
        @DisplayName("is still mandatory for the hosted deployment")
        void mandatoryWhenHosted() {
            StartupSecurityValidator v = validator("cloud", false, "sk_test_x", false, true);

            assertThatThrownBy(() -> run(v))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No durable file storage is configured");
        }

        @Test
        @DisplayName("is mandatory for a self-hosted install too — the filesystem backend satisfies it")
        void mandatoryWithOfflineLicence() {
            // This used to be an exemption: a self-hosted install was allowed to boot
            // onto the heap map because it is single-instance, which only rules out
            // the cross-replica 404s. The restart data loss applied to it just the
            // same, and there was no local-disk backend to point it at. There is now,
            // so the exemption is gone and the answer is a configuration, not a
            // documented trade-off.
            StartupSecurityValidator withoutBackend =
                    validator("cloud", true, "sk_test_x", false, false, true);
            assertThatThrownBy(() -> run(withoutBackend))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No durable file storage is configured");

            StartupSecurityValidator withFilesystem =
                    validator("cloud", true, "sk_test_x", false, true, true);
            assertThatCode(() -> run(withFilesystem)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("names both the hosted and the self-hosted fix in its failure message")
        void failureMessageNamesBothFixes() {
            StartupSecurityValidator v = validator("cloud", false, "sk_test_x", false, true);

            assertThatThrownBy(() -> run(v))
                    .hasMessageContaining("APP_STORAGE_S3_ENABLED=true")
                    .hasMessageContaining("APP_STORAGE_FILESYSTEM_ENABLED=true");
        }
    }

    @Test
    @DisplayName("the offline licence never relaxes the schema-mutation guard")
    void ddlGuardIsNotRelaxedBySelfHosting() {
        // Flyway owns the schema everywhere. A self-hosted operator letting
        // Hibernate rewrite their own tables is the same data-loss risk it is
        // in the hosted deployment, so this guard has no self-hosted exemption.
        StartupSecurityValidator v = validator("cloud", true, "", true, true);
        ReflectionTestUtils.setField(v, "ddlAuto", "update");

        assertThatThrownBy(() -> run(v))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ddl-auto='update' is NOT allowed");
    }
}
