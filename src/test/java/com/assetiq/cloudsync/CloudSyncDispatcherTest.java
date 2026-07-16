package com.assetiq.cloudsync;

import com.assetiq.enums.CloudAssetStatus;
import com.assetiq.enums.CloudProvider;
import com.assetiq.enums.CloudResourceType;
import com.assetiq.models.CloudAsset;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.CloudAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CloudSyncDispatcher")
class CloudSyncDispatcherTest {

    @Mock CloudAssetRepository cloudAssetRepo;

    // Two fake providers — one configured, one not
    CloudSyncProvider awsProvider;
    CloudSyncProvider azureProvider;

    CloudSyncDispatcher dispatcher;
    Organisation org;

    @BeforeEach
    void setUp() {
        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setName("Test Org");

        awsProvider = mock(CloudSyncProvider.class);
        when(awsProvider.provider()).thenReturn(CloudProvider.AWS);

        azureProvider = mock(CloudSyncProvider.class);
        when(azureProvider.provider()).thenReturn(CloudProvider.AZURE);

        dispatcher = new CloudSyncDispatcher(List.of(awsProvider, azureProvider), cloudAssetRepo);
    }

    // ── syncProvider ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("syncProvider()")
    class SyncProvider {

        @Test
        @DisplayName("calls discover() on the matching provider and upserts results")
        void callsDiscoverAndUpserts() {
            CloudAsset asset = makeAsset(CloudProvider.AWS, "i-12345");
            when(awsProvider.isConfigured()).thenReturn(true);
            when(awsProvider.discover(eq(org), anyList())).thenReturn(List.of(asset));
            when(cloudAssetRepo.findByResourceIdAndProviderAndOrganisationAndDeletedAtIsNull(
                    any(), any(), any())).thenReturn(Optional.empty());

            int result = dispatcher.syncProvider(CloudProvider.AWS, org, List.of("us-east-1"));

            assertThat(result).isEqualTo(1);
            verify(awsProvider).discover(eq(org), eq(List.of("us-east-1")));
            verify(cloudAssetRepo).save(any(CloudAsset.class));
        }

        @Test
        @DisplayName("returns 0 and skips discover() when provider is not configured")
        void skipsUnconfiguredProvider() {
            when(azureProvider.isConfigured()).thenReturn(false);

            int result = dispatcher.syncProvider(CloudProvider.AZURE, org, List.of());

            assertThat(result).isZero();
            verify(azureProvider, never()).discover(any(), any());
            verify(cloudAssetRepo, never()).save(any());
        }

        @Test
        @DisplayName("returns 0 for an unknown provider with no registered implementation")
        void returnsZeroForUnknownProvider() {
            int result = dispatcher.syncProvider(CloudProvider.GCP, org, List.of());

            assertThat(result).isZero();
            verify(cloudAssetRepo, never()).save(any());
        }

        @Test
        @DisplayName("upserts multiple assets returned by discover()")
        void upsertsMultipleAssets() {
            List<CloudAsset> assets = List.of(
                    makeAsset(CloudProvider.AWS, "i-aaa"),
                    makeAsset(CloudProvider.AWS, "i-bbb"),
                    makeAsset(CloudProvider.AWS, "i-ccc"));
            when(awsProvider.isConfigured()).thenReturn(true);
            when(awsProvider.discover(any(), any())).thenReturn(assets);
            when(cloudAssetRepo.findByResourceIdAndProviderAndOrganisationAndDeletedAtIsNull(
                    any(), any(), any())).thenReturn(Optional.empty());

            int result = dispatcher.syncProvider(CloudProvider.AWS, org, List.of());

            assertThat(result).isEqualTo(3);
            verify(cloudAssetRepo, times(3)).save(any(CloudAsset.class));
        }

        @Test
        @DisplayName("updates existing asset fields on re-discovery instead of inserting duplicate")
        void updatesExistingAssetOnUpsert() {
            CloudAsset discovered = makeAsset(CloudProvider.AWS, "i-existing");
            discovered.setStatus(CloudAssetStatus.STOPPED);
            discovered.setRegion("eu-west-1");

            CloudAsset existing = makeAsset(CloudProvider.AWS, "i-existing");
            existing.setId(UUID.randomUUID());
            existing.setStatus(CloudAssetStatus.RUNNING);
            existing.setRegion("us-east-1");

            when(awsProvider.isConfigured()).thenReturn(true);
            when(awsProvider.discover(any(), any())).thenReturn(List.of(discovered));
            when(cloudAssetRepo.findByResourceIdAndProviderAndOrganisationAndDeletedAtIsNull(
                    eq("i-existing"), eq(CloudProvider.AWS), eq(org)))
                    .thenReturn(Optional.of(existing));

            dispatcher.syncProvider(CloudProvider.AWS, org, List.of());

            ArgumentCaptor<CloudAsset> captor = ArgumentCaptor.forClass(CloudAsset.class);
            verify(cloudAssetRepo).save(captor.capture());

            CloudAsset saved = captor.getValue();
            // Should have updated the existing record's mutable fields
            assertThat(saved.getId()).isEqualTo(existing.getId());
            assertThat(saved.getStatus()).isEqualTo(CloudAssetStatus.STOPPED);
            assertThat(saved.getRegion()).isEqualTo("eu-west-1");
        }

        @Test
        @DisplayName("does not overwrite user-managed environment when discovered value is null")
        void preservesUserManagedEnvironment() {
            CloudAsset discovered = makeAsset(CloudProvider.AWS, "i-env");
            discovered.setEnvironment(null); // provider didn't set an environment

            CloudAsset existing = makeAsset(CloudProvider.AWS, "i-env");
            existing.setEnvironment("production"); // user set this manually

            when(awsProvider.isConfigured()).thenReturn(true);
            when(awsProvider.discover(any(), any())).thenReturn(List.of(discovered));
            when(cloudAssetRepo.findByResourceIdAndProviderAndOrganisationAndDeletedAtIsNull(
                    any(), any(), any())).thenReturn(Optional.of(existing));

            dispatcher.syncProvider(CloudProvider.AWS, org, List.of());

            ArgumentCaptor<CloudAsset> captor = ArgumentCaptor.forClass(CloudAsset.class);
            verify(cloudAssetRepo).save(captor.capture());
            assertThat(captor.getValue().getEnvironment()).isEqualTo("production");
        }
    }

    // ── syncAll ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("syncAll()")
    class SyncAll {

        @Test
        @DisplayName("runs only configured providers and sums upsert counts")
        void runsOnlyConfiguredProviders() {
            CloudAsset asset = makeAsset(CloudProvider.AWS, "i-all");
            when(awsProvider.isConfigured()).thenReturn(true);
            when(azureProvider.isConfigured()).thenReturn(false);
            when(awsProvider.discover(any(), any())).thenReturn(List.of(asset));
            when(cloudAssetRepo.findByResourceIdAndProviderAndOrganisationAndDeletedAtIsNull(
                    any(), any(), any())).thenReturn(Optional.empty());

            int total = dispatcher.syncAll(org, List.of());

            assertThat(total).isEqualTo(1);
            verify(awsProvider).discover(any(), any());
            verify(azureProvider, never()).discover(any(), any()); // not configured
        }

        @Test
        @DisplayName("continues with remaining providers when one throws")
        void continuesAfterProviderException() {
            // Make GCP configured and throw
            CloudSyncProvider gcpProvider = mock(CloudSyncProvider.class);
            when(gcpProvider.provider()).thenReturn(CloudProvider.GCP);
            when(gcpProvider.isConfigured()).thenReturn(true);
            when(gcpProvider.discover(any(), any())).thenThrow(new RuntimeException("GCP exploded"));

            CloudAsset asset = makeAsset(CloudProvider.AWS, "i-resilient");
            when(awsProvider.isConfigured()).thenReturn(true);
            when(awsProvider.discover(any(), any())).thenReturn(List.of(asset));
            when(cloudAssetRepo.findByResourceIdAndProviderAndOrganisationAndDeletedAtIsNull(
                    any(), any(), any())).thenReturn(Optional.empty());

            CloudSyncDispatcher multiDispatcher = new CloudSyncDispatcher(
                    List.of(awsProvider, gcpProvider), cloudAssetRepo);

            int total = multiDispatcher.syncAll(org, List.of());

            // AWS still contributed 1 despite GCP failure
            assertThat(total).isEqualTo(1);
            verify(cloudAssetRepo).save(any());
        }

        @Test
        @DisplayName("returns 0 when no provider is configured")
        void returnsZeroWhenNoneConfigured() {
            when(awsProvider.isConfigured()).thenReturn(false);
            when(azureProvider.isConfigured()).thenReturn(false);

            int total = dispatcher.syncAll(org, List.of());

            assertThat(total).isZero();
            verify(cloudAssetRepo, never()).save(any());
        }
    }

    // ── hasConfiguredProvider ─────────────────────────────────────────────────

    @Test
    @DisplayName("hasConfiguredProvider() is true when at least one provider is configured")
    void hasConfiguredProvider_trueWhenOneConfigured() {
        when(awsProvider.isConfigured()).thenReturn(true);
        assertThat(dispatcher.hasConfiguredProvider()).isTrue();
    }

    @Test
    @DisplayName("hasConfiguredProvider() is false when no provider is configured")
    void hasConfiguredProvider_falseWhenNoneConfigured() {
        when(awsProvider.isConfigured()).thenReturn(false);
        when(azureProvider.isConfigured()).thenReturn(false);
        assertThat(dispatcher.hasConfiguredProvider()).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CloudAsset makeAsset(CloudProvider provider, String resourceId) {
        CloudAsset a = new CloudAsset();
        a.setProvider(provider);
        a.setResourceId(resourceId);
        a.setName(resourceId);
        a.setRegion("us-east-1");
        a.setResourceType(CloudResourceType.VIRTUAL_MACHINE);
        a.setStatus(CloudAssetStatus.RUNNING);
        a.setOrganisation(org);
        a.setLastSyncAt(Instant.now());
        return a;
    }
}
