package com.assetiq.services.insights;

import com.assetiq.enums.AssetCondition;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.DepreciationMethod;
import com.assetiq.services.finance.DepreciationCalculator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The slim row every estate aggregate reads, in place of a hydrated
 * {@link com.assetiq.models.Asset}.
 *
 * <p>Book value cannot be computed in SQL — the four depreciation methods are
 * not expressible as an aggregate — so the choice is between loading entities
 * (and paying for lazy associations and an N+1 on the category policy) or
 * projecting exactly the columns the calculation needs. This is the projection:
 * one query with four left joins, no entity hydration, no second round trip,
 * and it carries the identifiers the UI needs to link back to the record.
 *
 * <p>Populated by {@code AssetRepository#findValuationRows}; the constructor
 * argument order is the select list of that query.
 */
public record AssetValuationRow(
        UUID id,
        String name,
        String assetTag,
        String currency,
        BigDecimal purchaseCost,
        BigDecimal residualValue,
        Integer usefulLifeMonths,
        DepreciationMethod depreciationMethod,
        LocalDate purchaseDate,
        LocalDate warrantyExpiryDate,
        LocalDate insurancePolicyExpiry,
        AssetStatus status,
        AssetCondition condition,
        UUID assignedUserId,
        UUID departmentId,
        String departmentName,
        UUID locationId,
        String locationName,
        UUID categoryId,
        String categoryName,
        Integer policyUsefulLifeMonths,
        DepreciationMethod policyMethod,
        BigDecimal policySalvageValuePercent,
        Instant updatedAt,
        Instant lastScannedAt) {

    /** True for assets still carried on the books (everything but DISPOSED). */
    public boolean onBooks() {
        return status != AssetStatus.DISPOSED;
    }

    /** True when the asset is one a tenant is actively relying on. */
    public boolean active() {
        return status == AssetStatus.IN_USE || status == AssetStatus.IN_STOCK || status == AssetStatus.RESERVED;
    }

    /** Depreciation as of {@code asOf}, in the asset's own currency. */
    public DepreciationCalculator.Result depreciation(LocalDate asOf) {
        return DepreciationCalculator.calculate(DepreciationCalculator.resolve(
                purchaseCost, residualValue, usefulLifeMonths, depreciationMethod, purchaseDate,
                status == AssetStatus.DISPOSED,
                policyUsefulLifeMonths, policyMethod, policySalvageValuePercent, asOf));
    }

    /**
     * The last time this asset's <em>record</em> changed.
     *
     * <p>Not a sighting, and not evidence of use: an import, a category rename
     * or a corrected serial number all move it without anybody going near the
     * asset. It is only good for one thing — bounding how long an asset with no
     * sighting at all has gone unseen — and callers must describe it as record
     * activity, never as idleness. For a real sighting see
     * {@link AssetSightingService}.
     */
    public Instant lastRecordActivityAt() {
        return updatedAt;
    }
}
