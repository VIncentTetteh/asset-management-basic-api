package com.assetiq.services.impl;

import com.assetiq.models.Asset;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.DepreciationService;
import com.assetiq.services.TenantAwareService;
import com.assetiq.services.finance.DepreciationCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Tenant-scoped depreciation queries plus the monthly book-value refresh. All
 * figures come from {@link DepreciationCalculator}.
 */
@Service
@Transactional
public class DepreciationServiceImpl extends TenantAwareService implements DepreciationService {

    private static final Logger log = LoggerFactory.getLogger(DepreciationServiceImpl.class);
    private static final int BATCH_SIZE = 500;

    private final AssetRepository assetRepository;

    public DepreciationServiceImpl(AssetRepository assetRepository,
            OrganisationRepository organisationRepository) {
        super(organisationRepository);
        this.assetRepository = assetRepository;
    }

    @Override
    public BigDecimal calculateDepreciation(UUID assetId) {
        return calculateDepreciationAsOf(assetId, LocalDate.now());
    }

    @Override
    public BigDecimal calculateDepreciationAsOf(UUID assetId, LocalDate asOfDate) {
        return DepreciationCalculator.forAsset(requireAsset(assetId), asOfDate).accumulatedDepreciation();
    }

    @Override
    public BigDecimal calculateMonthlyDepreciation(UUID assetId) {
        return DepreciationCalculator.forAsset(requireAsset(assetId), LocalDate.now()).monthlyDepreciation();
    }

    @Override
    public BigDecimal getCurrentBookValue(UUID assetId) {
        BigDecimal nbv = DepreciationCalculator.forAsset(requireAsset(assetId), LocalDate.now()).netBookValue();
        return nbv != null ? nbv : BigDecimal.ZERO;
    }

    @Override
    public void updateBookValue(UUID assetId) {
        Asset asset = requireAsset(assetId);
        asset.setCurrentBookValue(DepreciationCalculator.forAsset(asset, LocalDate.now()).netBookValue());
        assetRepository.save(asset);
    }

    /**
     * Refreshes the stored book value of every non-disposed asset (all statuses,
     * all tenants). Disposed assets keep the value they had at disposal.
     */
    @Override
    @Transactional
    @Scheduled(cron = "0 0 1 1 * ?") // 01:00 on the 1st of every month
    @SchedulerLock(name = "monthlyDepreciation", lockAtMostFor = "PT2H", lockAtLeastFor = "PT30M")
    public void runMonthlyDepreciationBatch() {
        LocalDate today = LocalDate.now();
        int pageNumber = 0;
        long refreshed = 0;
        Page<Asset> page;
        do {
            page = assetRepository.findUndisposedForDepreciation(
                    PageRequest.of(pageNumber++, BATCH_SIZE, Sort.by("id")));
            for (Asset asset : page.getContent()) {
                asset.setCurrentBookValue(DepreciationCalculator.forAsset(asset, today).netBookValue());
            }
            assetRepository.saveAll(page.getContent());
            refreshed += page.getNumberOfElements();
        } while (page.hasNext());
        log.info("[DEPRECIATION] Refreshed book value of {} assets", refreshed);
    }

    private Asset requireAsset(UUID assetId) {
        Organisation org = requireTenantOrg();
        return assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found in your organisation"));
    }
}
