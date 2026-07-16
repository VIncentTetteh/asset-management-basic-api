package com.assetiq.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the P0-3 ddl-auto guard on StartupSecurityValidator.
 * We bypass secret validation and only exercise the schema-mutation branch.
 */
class StartupSecurityValidatorDdlAutoTest {

    private StartupSecurityValidator newValidator(String ddlAuto, String[] activeProfiles) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(activeProfiles);
        StartupSecurityValidator v = new StartupSecurityValidator(env);
        ReflectionTestUtils.setField(v, "ddlAuto", ddlAuto);
        ReflectionTestUtils.setField(v, "skipValidation", true); // skip secret checks
        ReflectionTestUtils.setField(v, "jwtSecret", "ignored");
        ReflectionTestUtils.setField(v, "paystackSecretKey", "sk_test_ignored");
        return v;
    }

    @Test
    void allows_validate_in_any_profile() {
        StartupSecurityValidator v = newValidator("validate", new String[]{"prod"});
        v.run(mock(ApplicationArguments.class)); // no throw
        assertThat(true).isTrue();
    }

    @Test
    void allows_none_in_any_profile() {
        StartupSecurityValidator v = newValidator("none", new String[]{"prod"});
        v.run(mock(ApplicationArguments.class));
        assertThat(true).isTrue();
    }

    @Test
    void rejects_update_in_prod_profile() {
        StartupSecurityValidator v = newValidator("update", new String[]{"prod"});
        assertThatThrownBy(() -> v.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ddl-auto='update' is NOT allowed outside a dev profile");
    }

    @Test
    void rejects_update_when_no_profile_active() {
        StartupSecurityValidator v = newValidator("update", new String[]{});
        assertThatThrownBy(() -> v.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Flyway must own schema migrations");
    }

    @Test
    void rejects_create_drop_in_prod() {
        StartupSecurityValidator v = newValidator("create-drop", new String[]{"prod", "standalone"});
        assertThatThrownBy(() -> v.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void permits_update_in_dev_profile() {
        StartupSecurityValidator v = newValidator("update", new String[]{"dev"});
        v.run(mock(ApplicationArguments.class)); // no throw
    }

    @Test
    void permits_update_in_test_profile() {
        StartupSecurityValidator v = newValidator("update", new String[]{"test"});
        v.run(mock(ApplicationArguments.class));
    }

    @Test
    void is_case_insensitive() {
        StartupSecurityValidator v = newValidator("UPDATE", new String[]{"prod"});
        assertThatThrownBy(() -> v.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class);
    }
}
