package com.example.demo.services.impl;

import com.example.demo.models.Asset;
import com.example.demo.models.Organisation;
import com.example.demo.repositories.AssetRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.DepreciationService;
import com.example.demo.services.TenantAwareService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class DepreciationServiceImpl extends TenantAwareService implements DepreciationService {

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
        Organisation org = requireTenantOrg();
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found in your organisation"));
        return computeDepreciation(asset, asOfDate);
    }

    @Override
    public BigDecimal calculateMonthlyDepreciation(UUID assetId) {
        Organisation org = requireTenantOrg();
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found in your organisation"));

        if (asset.getPurchaseCost() == null)
            return BigDecimal.ZERO;

        var policy = asset.getCategory() != null ? asset.getCategory().getDepreciationPolicy() : null;
        if (policy == null)
            return BigDecimal.ZERO;

        int usefulLifeMonths = policy.getUsefulLifeMonths() != null ? policy.getUsefulLifeMonths() : 60;
        BigDecimal salvagePct = policy.getSalvageValuePercent() != null ? policy.getSalvageValuePercent()
                : BigDecimal.ZERO;
        BigDecimal depreciable = asset.getPurchaseCost()
                .multiply(BigDecimal.ONE.subtract(salvagePct.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP)));

        return depreciable.divide(new BigDecimal(usefulLifeMonths), 2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal getCurrentBookValue(UUID assetId) {
        Organisation org = requireTenantOrg();
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found in your organisation"));

        if (asset.getPurchaseCost() == null)
            return BigDecimal.ZERO;
        BigDecimal accumulated = computeDepreciation(asset, LocalDate.now());
        return asset.getPurchaseCost().subtract(accumulated);
    }

    @Override
    public void updateBookValue(UUID assetId) {
        Organisation org = requireTenantOrg();
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found in your organisation"));
        BigDecimal currentBookValue = asset.getPurchaseCost() == null ? BigDecimal.ZERO
                : asset.getPurchaseCost().subtract(computeDepreciation(asset, LocalDate.now()));
        asset.setCurrentBookValue(currentBookValue);
        assetRepository.save(asset);
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 1 1 * ?") // 01:00 on the 1st of every month
    public void runMonthlyDepreciationBatch() {
        Set<Asset> assets = assetRepository.findByStatusAndDeletedAtIsNull(
                com.example.demo.enums.AssetStatus.IN_USE);
        for (Asset asset : assets) {
            if (asset.getPurchaseCost() == null)
                continue;
            BigDecimal bookValue = asset.getPurchaseCost()
                    .subtract(computeDepreciation(asset, LocalDate.now()));
            asset.setCurrentBookValue(bookValue);
            assetRepository.save(asset);
        }
    }

    // --- Internal helper (no tenant check needed — caller already validated) ---
    private BigDecimal computeDepreciation(Asset asset, LocalDate asOfDate) {
        if (asset.getPurchaseDate() == null || asset.getPurchaseCost() == null)
            return BigDecimal.ZERO;

        var policy = asset.getCategory() != null ? asset.getCategory().getDepreciationPolicy() : null;
        if (policy == null)
            return BigDecimal.ZERO;

        int usefulLifeMonths = policy.getUsefulLifeMonths() != null ? policy.getUsefulLifeMonths() : 60;
        BigDecimal salvagePct = policy.getSalvageValuePercent() != null ? policy.getSalvageValuePercent()
                : BigDecimal.ZERO;
        BigDecimal depreciable = asset.getPurchaseCost()
                .multiply(BigDecimal.ONE.subtract(salvagePct.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP)));

        YearMonth purchaseMonth = YearMonth.from(asset.getPurchaseDate());
        YearMonth asOfMonth = YearMonth.from(asOfDate);
        long monthsElapsed = Math.max(0, java.time.temporal.ChronoUnit.MONTHS.between(purchaseMonth, asOfMonth));

        if (monthsElapsed >= usefulLifeMonths)
            return depreciable;

        BigDecimal monthly = depreciable.divide(new BigDecimal(usefulLifeMonths), 4, RoundingMode.HALF_UP);
        return monthly.multiply(new BigDecimal(monthsElapsed));
    }
}
