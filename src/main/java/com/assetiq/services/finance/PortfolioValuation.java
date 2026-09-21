package com.assetiq.services.finance;

import com.assetiq.enums.AssetStatus;
import com.assetiq.models.Asset;
import com.assetiq.services.money.CurrencyConversion;
import com.assetiq.services.money.MoneyAccumulator;

import java.time.LocalDate;
import java.util.Map;

/**
 * Book-value totals for a set of assets, in the tenant base currency.
 *
 * <p>Shared by the dashboard, analytics and reports so that every portfolio
 * figure is produced the same way: disposed assets are off the books and
 * excluded; every other asset is valued with {@link DepreciationCalculator} in
 * its own currency and then converted through the caller's
 * {@link CurrencyConversion} (an asset lacking a rate drops out of every total
 * alike).
 */
public final class PortfolioValuation {

    private final MoneyAccumulator cost;
    private final MoneyAccumulator netBookValue;
    private final MoneyAccumulator accumulatedDepreciation;
    private final MoneyAccumulator monthlyDepreciation;
    private long assetCount;
    private long fullyDepreciated;
    private long missingDepreciationSetup;

    private PortfolioValuation(CurrencyConversion fx) {
        this.cost = fx.newAccumulator();
        this.netBookValue = fx.newAccumulator();
        this.accumulatedDepreciation = fx.newAccumulator();
        this.monthlyDepreciation = fx.newAccumulator();
    }

    /** True for assets still carried on the books (everything but DISPOSED). */
    public static boolean isOnBooks(Asset asset) {
        return asset.getStatus() != AssetStatus.DISPOSED;
    }

    /** Value the on-book assets in {@code assets} as of {@code asOf}. */
    public static PortfolioValuation of(CurrencyConversion fx, Iterable<Asset> assets, LocalDate asOf) {
        PortfolioValuation v = new PortfolioValuation(fx);
        for (Asset asset : assets) {
            if (isOnBooks(asset)) {
                v.add(asset, DepreciationCalculator.forAsset(asset, asOf));
            }
        }
        return v;
    }

    private void add(Asset asset, DepreciationCalculator.Result r) {
        assetCount++;
        String currency = asset.getCurrency();
        cost.add(asset.getPurchaseCost(), currency);
        netBookValue.add(r.netBookValue(), currency);
        accumulatedDepreciation.add(r.accumulatedDepreciation(), currency);
        monthlyDepreciation.add(r.monthlyDepreciation(), currency);
        if (asset.getPurchaseCost() != null) {
            if (!r.configured()) {
                missingDepreciationSetup++;
            } else if (r.fullyDepreciated()) {
                fullyDepreciated++;
            }
        }
    }

    public MoneyAccumulator cost() {
        return cost;
    }

    public MoneyAccumulator netBookValue() {
        return netBookValue;
    }

    public MoneyAccumulator accumulatedDepreciation() {
        return accumulatedDepreciation;
    }

    public MoneyAccumulator monthlyDepreciation() {
        return monthlyDepreciation;
    }

    /** Number of on-book assets valued. */
    public long assetCount() {
        return assetCount;
    }

    public long fullyDepreciated() {
        return fullyDepreciated;
    }

    /** On-book assets with a cost but no useful life / purchase date to depreciate them. */
    public long missingDepreciationSetup() {
        return missingDepreciationSetup;
    }

    /**
     * Writes the standard book-value fields ({@code totalAssetValue},
     * {@code totalDepreciation}, {@code netBookValue}, {@code monthlyDepreciation},
     * {@code assetsFullyDepreciated}, {@code assetsMissingDepreciationSetup}).
     */
    public Map<String, Object> putTotals(Map<String, Object> target) {
        target.put("totalAssetValue", cost.amount());
        target.put("totalDepreciation", accumulatedDepreciation.amount());
        target.put("netBookValue", netBookValue.amount());
        target.put("monthlyDepreciation", monthlyDepreciation.amount());
        target.put("assetsFullyDepreciated", fullyDepreciated);
        target.put("assetsMissingDepreciationSetup", missingDepreciationSetup);
        return target;
    }
}
