package com.assetiq.scheduling;

import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.FeatureFlagService;
import com.assetiq.services.PredictiveMaintenanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PredictiveInsightRefreshScheduler - every opted-in tenant, nightly")
class PredictiveInsightRefreshSchedulerTest {

    @Mock OrganisationRepository       organisationRepository;
    @Mock FeatureFlagService           featureFlags;
    @Mock PredictiveMaintenanceService predictiveService;

    private PredictiveInsightRefreshScheduler scheduler;
    private Organisation acme;
    private Organisation beta;

    @BeforeEach
    void setUp() {
        scheduler = new PredictiveInsightRefreshScheduler(
                organisationRepository, featureFlags, predictiveService);
        scheduler.configure(2000, 500);

        acme = organisation("Acme Ltd");
        beta = organisation("Beta Ltd");

        when(organisationRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(acme, beta));
        when(featureFlags.isEnabledFor(anyString(), any())).thenReturn(true);
        when(predictiveService.refreshInsights(anyInt())).thenReturn(3);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("every opted-in tenant is refreshed, each in its own tenant context")
    void refreshesEveryOptedInTenant() {
        List<UUID> seen = new ArrayList<>();
        when(predictiveService.refreshInsights(anyInt())).thenAnswer(invocation -> {
            seen.add(TenantContext.getOrganisationId());
            return 3;
        });

        scheduler.refreshAllTenants();

        assertThat(seen).containsExactly(acme.getId(), beta.getId());
        verify(predictiveService, times(2)).refreshInsights(2000);
    }

    @Test
    @DisplayName("one tenant's failure does not stop the rest")
    void oneFailureDoesNotStopTheRun() {
        List<UUID> succeeded = new ArrayList<>();
        when(predictiveService.refreshInsights(anyInt())).thenAnswer(invocation -> {
            UUID current = TenantContext.getOrganisationId();
            if (acme.getId().equals(current)) {
                throw new IllegalStateException("corrupt data for this tenant");
            }
            succeeded.add(current);
            return 3;
        });

        scheduler.refreshAllTenants();

        assertThat(succeeded)
                .as("the second tenant must still get its insights")
                .containsExactly(beta.getId());
    }

    @Test
    @DisplayName("a concurrent manual refresh is a conflict, not a crash, and the run continues")
    void concurrentRefreshIsTolerated() {
        List<UUID> succeeded = new ArrayList<>();
        when(predictiveService.refreshInsights(anyInt())).thenAnswer(invocation -> {
            UUID current = TenantContext.getOrganisationId();
            if (acme.getId().equals(current)) {
                throw new DataIntegrityViolationException("uq_predictive_insight_live");
            }
            succeeded.add(current);
            return 3;
        });

        scheduler.refreshAllTenants();

        assertThat(succeeded).containsExactly(beta.getId());
    }

    @Test
    @DisplayName("a tenant with the feature off is never analysed")
    void optedOutTenantIsSkipped() {
        when(featureFlags.isEnabledFor(eq(PredictiveInsightRefreshScheduler.AI_FLAG), eq(acme.getId())))
                .thenReturn(false);
        List<UUID> seen = new ArrayList<>();
        when(predictiveService.refreshInsights(anyInt())).thenAnswer(invocation -> {
            seen.add(TenantContext.getOrganisationId());
            return 1;
        });

        scheduler.refreshAllTenants();

        assertThat(seen).containsExactly(beta.getId());
        verify(predictiveService, times(1)).refreshInsights(anyInt());
    }

    @Test
    @DisplayName("the run is bounded: tenants beyond the cap are left for the next night")
    void tenantCountIsBounded() {
        scheduler.configure(2000, 1);

        scheduler.refreshAllTenants();

        verify(predictiveService, times(1)).refreshInsights(anyInt());
    }

    @Test
    @DisplayName("the per-tenant asset bound is passed through")
    void assetBoundIsPassedThrough() {
        scheduler.configure(50, 500);

        scheduler.refreshAllTenants();

        verify(predictiveService, times(2)).refreshInsights(50);
    }

    @Test
    @DisplayName("the tenant context never leaks past a tenant, even when it throws")
    void tenantContextIsAlwaysCleared() {
        when(predictiveService.refreshInsights(anyInt()))
                .thenThrow(new IllegalStateException("boom"));

        scheduler.refreshAllTenants();

        assertThat(TenantContext.hasOrganisationId())
                .as("a pooled scheduler thread must not carry a tenant id into its next job")
                .isFalse();
    }

    private static Organisation organisation(String name) {
        Organisation org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setName(name);
        return org;
    }
}
