package com.assetiq.license.offline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the offline licence beans actually wire into a Spring context.
 *
 * <p>Every other test in this package constructs {@link OfflineLicenseService}
 * with {@code new}, which is the right way to test its logic and is exactly
 * why it missed a defect that took the whole application down: the class has
 * two constructors, and Spring only infers constructor injection when there is
 * one. Without {@code @Autowired} on the public constructor it looked for a
 * no-arg constructor, found none, and the context failed to refresh — so with
 * {@code APP_LICENSE_OFFLINE_ENABLED=true}, which is the self-hosted default,
 * the backend crash-looped on startup.</p>
 *
 * <p>These tests refresh a real context, so a future change to the constructor
 * set, the property binding or the conditional fails here rather than in an
 * operator's first {@code docker compose up}.</p>
 */
@DisplayName("Offline licence Spring wiring")
class OfflineLicenseWiringTest {

    @Configuration(proxyBeanMethods = false)
    @Import({OfflineLicenseProperties.class, OfflineLicenseService.class})
    static class LicenceConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(LicenceConfig.class);

    @Test
    @DisplayName("the service is created and started when the flag is on")
    void serviceStartsWhenEnabled() {
        runner.withPropertyValues("app.license.offline.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OfflineLicenseService.class);
                    // @PostConstruct ran without throwing, on a build with no
                    // baked public key and no licence key configured.
                    assertThat(context.getBean(OfflineLicenseService.class).current().status())
                            .isEqualTo(OfflineLicenseStatus.NO_PUBLIC_KEY);
                });
    }

    @Test
    @DisplayName("no bean exists at all when the flag is off — the hosted default")
    void noBeanWhenDisabled() {
        runner.withPropertyValues("app.license.offline.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(OfflineLicenseService.class);
                });
    }

    @Test
    @DisplayName("no bean exists when the property is absent entirely")
    void noBeanWhenPropertyMissing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OfflineLicenseService.class);
        });
    }

    @Test
    @DisplayName("the licence key and public key bind from properties")
    void propertiesBind() {
        runner.withPropertyValues(
                        "app.license.offline.enabled=true",
                        "app.license.offline.key=some-token",
                        "app.license.offline.public-key=not-a-real-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    OfflineLicenseProperties props = context.getBean(OfflineLicenseProperties.class);
                    assertThat(props.isEnabled()).isTrue();
                    assertThat(props.getKey()).isEqualTo("some-token");
                    assertThat(props.getPublicKey()).isEqualTo("not-a-real-key");

                    // An unreadable public key must degrade, not fail the context.
                    assertThat(context.getBean(OfflineLicenseService.class).current().status())
                            .isEqualTo(OfflineLicenseStatus.NO_PUBLIC_KEY);
                });
    }
}
