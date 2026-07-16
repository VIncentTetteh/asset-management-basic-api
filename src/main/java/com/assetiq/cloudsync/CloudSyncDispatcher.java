package com.assetiq.cloudsync;

import com.assetiq.enums.CloudProvider;
import com.assetiq.models.CloudAsset;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.CloudAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes cloud-asset discovery requests to the appropriate {@link CloudSyncProvider}
 * implementation and persists the results via upsert.
 *
 * <p>All registered {@link CloudSyncProvider} beans are injected automatically by
 * Spring via the {@code List<CloudSyncProvider>} constructor parameter.  A new cloud
 * provider only needs to implement the interface and annotate itself with
 * {@code @Component} — no wiring changes needed here.
 *
 * <p>Upsert logic: if a {@code CloudAsset} already exists for the same
 * ({@code resourceId}, {@code provider}, {@code organisation}) tuple its mutable
 * fields (name, status, region, description, lastSyncAt) are overwritten; otherwise
 * a new record is inserted.  Soft-deleted assets are treated as non-existent and a
 * fresh record is created.
 */
@Service
public class CloudSyncDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CloudSyncDispatcher.class);

    private final Map<CloudProvider, CloudSyncProvider> providers;
    private final CloudAssetRepository cloudAssetRepo;

    public CloudSyncDispatcher(List<CloudSyncProvider> providerList,
                               CloudAssetRepository cloudAssetRepo) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(CloudSyncProvider::provider, Function.identity()));
        this.cloudAssetRepo = cloudAssetRepo;
        log.info("[CloudSync] Registered providers: {}", this.providers.keySet());
    }

    /**
     * Discovers and upserts assets for a single cloud provider.
     *
     * @param provider the target cloud provider
     * @param org      the tenant organisation
     * @param regions  cloud-specific region codes to scan; empty → provider default
     * @return count of assets upserted (created or updated)
     */
    public int syncProvider(CloudProvider provider, Organisation org, List<String> regions) {
        CloudSyncProvider impl = providers.get(provider);
        if (impl == null) {
            log.warn("[CloudSync] No provider implementation registered for {}", provider);
            return 0;
        }
        if (!impl.isConfigured()) {
            log.info("[CloudSync] Provider {} is not configured for org {} — skipping",
                    provider, org.getId());
            return 0;
        }

        List<CloudAsset> discovered = impl.discover(org, regions != null ? regions : Collections.emptyList());
        return upsertAll(discovered, org);
    }

    /**
     * Discovers and upserts assets across ALL configured providers.
     *
     * @param org     the tenant organisation
     * @param regions region codes; applies to all providers (each interprets them per its own API)
     * @return total assets upserted across all providers
     */
    public int syncAll(Organisation org, List<String> regions) {
        int total = 0;
        for (CloudSyncProvider impl : providers.values()) {
            if (!impl.isConfigured()) {
                log.debug("[CloudSync] Provider {} not configured — skipping for org {}",
                        impl.provider(), org.getId());
                continue;
            }
            try {
                List<CloudAsset> discovered = impl.discover(org,
                        regions != null ? regions : Collections.emptyList());
                total += upsertAll(discovered, org);
            } catch (Exception ex) {
                log.error("[CloudSync] Provider {} threw during syncAll for org {}: {}",
                        impl.provider(), org.getId(), ex.getMessage());
            }
        }
        log.info("[CloudSync] syncAll complete for org {} — {} total asset(s) upserted", org.getId(), total);
        return total;
    }

    /**
     * Returns {@code true} when at least one provider is registered and configured.
     */
    public boolean hasConfiguredProvider() {
        return providers.values().stream().anyMatch(CloudSyncProvider::isConfigured);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private int upsertAll(List<CloudAsset> assets, Organisation org) {
        int count = 0;
        for (CloudAsset discovered : assets) {
            try {
                upsert(discovered, org);
                count++;
            } catch (Exception ex) {
                log.warn("[CloudSync] Failed to upsert asset {} ({}): {}",
                        discovered.getResourceId(), discovered.getProvider(), ex.getMessage());
            }
        }
        return count;
    }

    private void upsert(CloudAsset discovered, Organisation org) {
        Optional<CloudAsset> existing = cloudAssetRepo
                .findByResourceIdAndProviderAndOrganisationAndDeletedAtIsNull(
                        discovered.getResourceId(), discovered.getProvider(), org);

        CloudAsset target = existing.orElseGet(CloudAsset::new);

        // Overwrite mutable discovery fields; never touch cost, tags, or environment set by users
        target.setResourceId(discovered.getResourceId());
        target.setProvider(discovered.getProvider());
        target.setOrganisation(org);
        target.setName(discovered.getName());
        target.setRegion(discovered.getRegion());
        target.setResourceType(discovered.getResourceType());
        target.setStatus(discovered.getStatus());
        target.setLastSyncAt(discovered.getLastSyncAt());

        // Only overwrite description / environment if the provider returned one
        // and the field has not been manually set (null check as a proxy)
        if (discovered.getDescription() != null) {
            target.setDescription(discovered.getDescription());
        }
        if (discovered.getEnvironment() != null && target.getEnvironment() == null) {
            target.setEnvironment(discovered.getEnvironment());
        }

        cloudAssetRepo.save(target);
    }
}
