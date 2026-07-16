package com.assetiq.cloudsync;

import com.assetiq.enums.CloudProvider;
import com.assetiq.models.CloudAsset;
import com.assetiq.models.Organisation;

import java.util.List;

/**
 * Strategy interface for cloud-provider–specific asset discovery.
 *
 * <p>Each implementation is responsible for a single {@link CloudProvider}.
 * It discovers all supported resource types within the given regions and returns
 * a flat list of (partially-populated) {@link CloudAsset} instances ready for
 * upsert into the database.
 *
 * <p>Implementations must be self-contained with respect to authentication:
 * they read credentials from the environment (IAM roles, env variables, etc.)
 * and must not throw if credentials are absent — return an empty list and log a
 * warning instead, so one un-configured provider never blocks others.
 */
public interface CloudSyncProvider {

    /** The cloud provider this implementation handles. */
    CloudProvider provider();

    /**
     * Returns {@code true} when the required credentials / SDK configuration are
     * present in the current environment.  Called before {@link #discover} to give
     * a fast, safe no-op path for unconfigured providers.
     */
    boolean isConfigured();

    /**
     * Discovers all supported asset types for the given organisation across the
     * specified regions.  For global resources (e.g. S3, GCS) the region list is
     * used only to seed the SDK client; the scan itself is account-wide.
     *
     * @param org     the tenant organisation (used only to set {@code organisation} on
     *                returned assets; no DB calls should be made here)
     * @param regions cloud-provider region codes to scan; empty → use default region
     * @return discovered assets (not yet persisted)
     */
    List<CloudAsset> discover(Organisation org, List<String> regions);
}
