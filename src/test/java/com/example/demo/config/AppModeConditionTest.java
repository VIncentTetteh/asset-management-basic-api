package com.example.demo.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the {@link ConditionalOnAppMode} annotation correctly gates
 * beans so that standalone-only components are NEVER loaded when
 * {@code app.mode=cloud} (the default).
 *
 * <p>This test is the Phase 0 "safety net": if it passes, cloud customers
 * are guaranteed to never encounter license middleware regardless of what
 * standalone code gets added to the codebase later.</p>
 */
class AppModeConditionTest {

    // ── Shared fixtures ──────────────────────────────────────────────────────

    /** A fake standalone-only bean used to verify conditional loading. */
    static class StandaloneOnlyBean {
        String identity() { return "standalone"; }
    }

    /** A fake cloud-only bean used to verify conditional loading. */
    static class CloudOnlyBean {
        String identity() { return "cloud"; }
    }

    @Configuration
    static class FixtureConfig {

        @Bean
        @ConditionalOnAppMode(AppMode.STANDALONE)
        StandaloneOnlyBean standaloneOnlyBean() {
            return new StandaloneOnlyBean();
        }

        @Bean
        @ConditionalOnAppMode(AppMode.CLOUD)
        CloudOnlyBean cloudOnlyBean() {
            return new CloudOnlyBean();
        }
    }

    // ── Cloud-mode tests (default — most important) ──────────────────────────

    @SpringBootTest(classes = {AppModeProperties.class, FixtureConfig.class})
    @TestPropertySource(properties = "app.mode=cloud")
    static class WhenModeIsCloud {

        @Autowired ApplicationContext ctx;

        @Test
        @DisplayName("standalone-only bean must NOT be registered in cloud mode")
        void standaloneOnlyBeanIsAbsent() {
            assertThatThrownBy(() -> ctx.getBean(StandaloneOnlyBean.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }

        @Test
        @DisplayName("cloud-only bean must be registered in cloud mode")
        void cloudOnlyBeanIsPresent() {
            CloudOnlyBean bean = ctx.getBean(CloudOnlyBean.class);
            assertThat(bean.identity()).isEqualTo("cloud");
        }

        @Test
        @DisplayName("AppModeProperties must report isCloud=true in cloud mode")
        void appModePropertiesReportsCloud() {
            AppModeProperties props = ctx.getBean(AppModeProperties.class);
            assertThat(props.isCloud()).isTrue();
            assertThat(props.isStandalone()).isFalse();
        }
    }

    // ── Default (no property set) must also behave as cloud ──────────────────

    @SpringBootTest(classes = {AppModeProperties.class, FixtureConfig.class})
    static class WhenModeIsDefault {

        @Autowired ApplicationContext ctx;

        @Test
        @DisplayName("standalone-only bean must NOT be registered when app.mode is absent")
        void standaloneOnlyBeanIsAbsentByDefault() {
            assertThatThrownBy(() -> ctx.getBean(StandaloneOnlyBean.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }

        @Test
        @DisplayName("AppModeProperties defaults to CLOUD when property is absent")
        void appModePropertiesDefaultsToCloud() {
            AppModeProperties props = ctx.getBean(AppModeProperties.class);
            assertThat(props.getMode()).isEqualTo(AppMode.CLOUD);
        }
    }

    // ── Standalone-mode tests ─────────────────────────────────────────────────

    @SpringBootTest(classes = {AppModeProperties.class, FixtureConfig.class})
    @TestPropertySource(properties = "app.mode=standalone")
    static class WhenModeIsStandalone {

        @Autowired ApplicationContext ctx;

        @Test
        @DisplayName("standalone-only bean must be registered in standalone mode")
        void standaloneOnlyBeanIsPresent() {
            StandaloneOnlyBean bean = ctx.getBean(StandaloneOnlyBean.class);
            assertThat(bean.identity()).isEqualTo("standalone");
        }

        @Test
        @DisplayName("cloud-only bean must NOT be registered in standalone mode")
        void cloudOnlyBeanIsAbsent() {
            assertThatThrownBy(() -> ctx.getBean(CloudOnlyBean.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }

        @Test
        @DisplayName("AppModeProperties must report isStandalone=true in standalone mode")
        void appModePropertiesReportsStandalone() {
            AppModeProperties props = ctx.getBean(AppModeProperties.class);
            assertThat(props.isStandalone()).isTrue();
            assertThat(props.isCloud()).isFalse();
        }
    }

    // ── AppMode.from() unit tests ─────────────────────────────────────────────

    @Test
    @DisplayName("AppMode.from() returns CLOUD for null input (fail-safe)")
    void fromNullReturnsCloud() {
        assertThat(AppMode.from(null)).isEqualTo(AppMode.CLOUD);
    }

    @Test
    @DisplayName("AppMode.from() returns CLOUD for unrecognised input (fail-safe)")
    void fromUnknownReturnsCloud() {
        assertThat(AppMode.from("unknown")).isEqualTo(AppMode.CLOUD);
        assertThat(AppMode.from("")).isEqualTo(AppMode.CLOUD);
        assertThat(AppMode.from("  ")).isEqualTo(AppMode.CLOUD);
    }

    @Test
    @DisplayName("AppMode.from() is case-insensitive for 'standalone'")
    void fromStandaloneIsCaseInsensitive() {
        assertThat(AppMode.from("standalone")).isEqualTo(AppMode.STANDALONE);
        assertThat(AppMode.from("STANDALONE")).isEqualTo(AppMode.STANDALONE);
        assertThat(AppMode.from("Standalone")).isEqualTo(AppMode.STANDALONE);
    }

    @Test
    @DisplayName("AppMode.from() is case-insensitive for 'cloud'")
    void fromCloudIsCaseInsensitive() {
        assertThat(AppMode.from("cloud")).isEqualTo(AppMode.CLOUD);
        assertThat(AppMode.from("CLOUD")).isEqualTo(AppMode.CLOUD);
        assertThat(AppMode.from("Cloud")).isEqualTo(AppMode.CLOUD);
    }
}
