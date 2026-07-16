package com.assetiq.services;

import com.assetiq.models.FeatureFlag;
import com.assetiq.models.FeatureFlagOrganisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.FeatureFlagOrganisationRepository;
import com.assetiq.repositories.FeatureFlagRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for P0-8 {@link FeatureFlagService}. We mock both repositories so
 * the tests are hermetic and millisecond-fast.
 */
@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    private static final String KEY = "billing.ghs-default-currency";

    @Mock
    FeatureFlagRepository flagRepository;

    @Mock
    FeatureFlagOrganisationRepository overrideRepository;

    @InjectMocks
    FeatureFlagService service;

    @AfterEach
    void tearDownContext() {
        TenantContext.clear();
    }

    @BeforeEach
    void clearContext() {
        TenantContext.clear();
    }

    private FeatureFlag globalFlag(boolean enabled, int percentage) {
        FeatureFlag flag = new FeatureFlag();
        flag.setKey(KEY);
        flag.setEnabledGlobally(enabled);
        flag.setRolloutPercentage((short) percentage);
        return flag;
    }

    @Test
    void unknown_flag_returns_false() {
        when(flagRepository.findByKey(KEY)).thenReturn(Optional.empty());
        assertThat(service.isEnabled(KEY)).isFalse();
    }

    @Test
    void global_on_returns_true_even_without_tenant() {
        when(flagRepository.findByKey(KEY)).thenReturn(Optional.of(globalFlag(true, 0)));
        assertThat(service.isEnabled(KEY)).isTrue();
    }

    @Test
    void per_tenant_override_beats_global_default() {
        UUID orgId = UUID.randomUUID();
        TenantContext.setOrganisationId(orgId);

        FeatureFlag flag = globalFlag(true, 0); // global ON
        when(flagRepository.findByKey(KEY)).thenReturn(Optional.of(flag));

        FeatureFlagOrganisation override = new FeatureFlagOrganisation();
        override.setFeatureFlag(flag);
        override.setEnabled(false); // tenant opts out
        when(overrideRepository.findByFeatureFlagKeyAndOrganisationId(eq(KEY), any(UUID.class)))
                .thenReturn(Optional.of(override));

        assertThat(service.isEnabled(KEY)).isFalse();
    }

    @Test
    void per_tenant_override_can_force_on() {
        UUID orgId = UUID.randomUUID();
        TenantContext.setOrganisationId(orgId);

        FeatureFlag flag = globalFlag(false, 0);
        when(flagRepository.findByKey(KEY)).thenReturn(Optional.of(flag));

        FeatureFlagOrganisation override = new FeatureFlagOrganisation();
        override.setFeatureFlag(flag);
        override.setEnabled(true);
        when(overrideRepository.findByFeatureFlagKeyAndOrganisationId(eq(KEY), any(UUID.class)))
                .thenReturn(Optional.of(override));

        assertThat(service.isEnabled(KEY)).isTrue();
    }

    @Test
    void rollout_100_percent_returns_true() {
        UUID orgId = UUID.randomUUID();
        TenantContext.setOrganisationId(orgId);
        when(flagRepository.findByKey(KEY)).thenReturn(Optional.of(globalFlag(false, 100)));
        when(overrideRepository.findByFeatureFlagKeyAndOrganisationId(eq(KEY), any(UUID.class)))
                .thenReturn(Optional.empty());

        assertThat(service.isEnabled(KEY)).isTrue();
    }

    @Test
    void rollout_zero_percent_returns_false() {
        UUID orgId = UUID.randomUUID();
        TenantContext.setOrganisationId(orgId);
        when(flagRepository.findByKey(KEY)).thenReturn(Optional.of(globalFlag(false, 0)));

        assertThat(service.isEnabled(KEY)).isFalse();
    }

    @Test
    void rollout_is_deterministic_per_tenant() {
        UUID orgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(flagRepository.findByKey(KEY)).thenReturn(Optional.of(globalFlag(false, 50)));
        when(overrideRepository.findByFeatureFlagKeyAndOrganisationId(eq(KEY), any(UUID.class)))
                .thenReturn(Optional.empty());

        boolean first = service.isEnabledFor(KEY, orgId);
        boolean second = service.isEnabledFor(KEY, orgId);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void null_tenant_with_percent_rollout_is_conservative() {
        when(flagRepository.findByKey(KEY)).thenReturn(Optional.of(globalFlag(false, 50)));
        // No TenantContext → treated as anonymous. 50% rollout → should be OFF.
        assertThat(service.isEnabled(KEY)).isFalse();
    }
}
