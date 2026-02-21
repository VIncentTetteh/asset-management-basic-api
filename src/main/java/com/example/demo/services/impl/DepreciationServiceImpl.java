package com.example.demo.services.impl;

import com.example.demo.models.Asset;
import com.example.demo.models.DepreciationPolicy;
import com.example.demo.repositories.AssetRepository;
import com.example.demo.services.DepreciationService;
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
public class DepreciationServiceImpl implements DepreciationService {

    private final AssetRepository assetRepository;

    public DepreciationServiceImpl(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Override
    public BigDecimal calculateDepreciation(UUID assetId) {
        return calculateDepreciationAsOf(assetId, LocalDate.now());
    }

    @Override
    public BigDecimal calculateDepreciationAsOf(UUID assetId, LocalDate asOfDate) {
        Asset asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        if (asset.getPurchaseDate() == null || asset.getPurchaseCost() == null) {
            return BigDecimal.ZERO;
        }

        DepreciationPolicy policy = asset.getCategory() != null ?
            asset.getCategory().getDepreciationPolicy() : null;

        if (policy == null) {
            return BigDecimal.ZERO;
        }

        int usefulLifeMonths = policy.getUsefulLifeMonths() != null ?
            policy.getUsefulLifeMonths() : 60; // Default 5 years

        BigDecimal salvagePercent = policy.getSalvageValuePercent() != null ?
            policy.getSalvageValuePercent() : BigDecimal.ZERO;

        BigDecimal depreciableAmount = asset.getPurchaseCost()
            .multiply(BigDecimal.ONE.subtract(salvagePercent.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP)));

        // Calculate months elapsed
        YearMonth purchaseMonth = YearMonth.from(asset.getPurchaseDate());
        YearMonth asOfMonth = YearMonth.from(asOfDate);
        long monthsElapsed = java.time.temporal.ChronoUnit.MONTHS.between(purchaseMonth, asOfMonth);

        if (monthsElapsed < 0) {
            monthsElapsed = 0;
        }

        if (monthsElapsed > usefulLifeMonths) {
            return depreciableAmount;
        }

        // Straight-line depreciation
        BigDecimal monthlyDepreciation = depreciableAmount.divide(
            new BigDecimal(usefulLifeMonths), 4, RoundingMode.HALF_UP);

        return monthlyDepreciation.multiply(new BigDecimal(monthsElapsed));
    }

    @Override
    public BigDecimal calculateMonthlyDepreciation(UUID assetId) {
        Asset asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        if (asset.getPurchaseCost() == null) {
            return BigDecimal.ZERO;
        }

        DepreciationPolicy policy = asset.getCategory() != null ?
            asset.getCategory().getDepreciationPolicy() : null;

        if (policy == null) {
            return BigDecimal.ZERO;
        }

        int usefulLifeMonths = policy.getUsefulLifeMonths() != null ?
            policy.getUsefulLifeMonths() : 60;

        BigDecimal salvagePercent = policy.getSalvageValuePercent() != null ?
            policy.getSalvageValuePercent() : BigDecimal.ZERO;

        BigDecimal depreciableAmount = asset.getPurchaseCost()
            .multiply(BigDecimal.ONE.subtract(salvagePercent.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP)));

        return depreciableAmount.divide(new BigDecimal(usefulLifeMonths), 2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal getCurrentBookValue(UUID assetId) {
        Asset asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        if (asset.getPurchaseCost() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal accumulatedDepreciation = calculateDepreciation(assetId);
        return asset.getPurchaseCost().subtract(accumulatedDepreciation);
    }

    @Override
    public void updateBookValue(UUID assetId) {
        Asset asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        BigDecimal currentBookValue = getCurrentBookValue(assetId);
        asset.setCurrentBookValue(currentBookValue);

        assetRepository.save(asset);
    }

    @Override
    @Transactional
    public void runMonthlyDepreciationBatch() {
        // Get all active assets
        Set<Asset> assets = assetRepository.findByStatusAndDeletedAtIsNull(
            com.example.demo.enums.AssetStatus.IN_USE);

        assets.stream().forEach(a -> updateBookValue(a.getId()));
    }
}

