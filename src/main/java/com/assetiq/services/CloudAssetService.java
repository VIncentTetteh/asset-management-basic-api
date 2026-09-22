package com.assetiq.services;

import com.assetiq.dto.CloudAssetDto;
import com.assetiq.dto.CloudCostSummaryDto;
import com.assetiq.enums.CloudProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CloudAssetService {

    CloudAssetDto create(CloudAssetDto dto);

    Page<CloudAssetDto> list(String provider, String environment, Pageable pageable);

    CloudAssetDto getById(UUID id);

    CloudAssetDto update(UUID id, CloudAssetDto dto);

    void delete(UUID id);

    /** Cost summary with breakdown by provider and environment */
    CloudCostSummaryDto getCostSummary();

    /**
     * Records the cost of a cloud asset for a month. Upsert on (asset, month,
     * service): recording the same month and service again replaces the amount.
     * A blank service name means the asset as a whole.
     */
    void recordMonthlyCost(UUID assetId, String billingMonth, java.math.BigDecimal amount, String serviceName);

    /** Recorded monthly costs of one asset, newest month first. */
    Page<com.assetiq.dto.CloudCostRecordDto> listCosts(UUID assetId, Pageable pageable);

    /**
     * Discover and upsert cloud assets for the specified provider.
     *
     * @param provider the target cloud provider (AWS, AZURE, GCP, …)
     * @param regions  provider-specific region codes to scan; null/empty → provider default
     * @return number of assets upserted (created or updated)
     */
    int syncFromCloud(CloudProvider provider, List<String> regions);

    /**
     * Discover and upsert assets from every configured cloud provider.
     *
     * @param regions region codes forwarded to all providers; null/empty → each provider's default
     * @return total number of assets upserted across all providers
     */
    int syncAll(List<String> regions);
}
