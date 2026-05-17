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

    /** Record a monthly cost entry for a cloud asset */
    void recordMonthlyCost(UUID assetId, String billingMonth, java.math.BigDecimal amount, String serviceName);

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
