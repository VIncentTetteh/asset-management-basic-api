package com.assetiq.services.money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Sums (amount, currency) pairs into the base currency of the owning
 * {@link CurrencyConversion}. Amounts whose currency cannot be converted are
 * excluded - never added raw - and recorded in {@link #missingRates()}.
 * Not thread-safe; create one per total.
 */
public final class MoneyAccumulator {

    private final CurrencyConversion conversion;
    private final SortedSet<String> missingRates = new TreeSet<>();
    private BigDecimal sum = BigDecimal.ZERO;
    private long includedCount;

    MoneyAccumulator(CurrencyConversion conversion) {
        this.conversion = conversion;
    }

    /**
     * Add an amount. A null amount contributes nothing; a null or blank currency
     * is treated as the base currency.
     *
     * @return {@code true} when the amount was included in the sum
     */
    public boolean add(BigDecimal amount, String currency) {
        if (amount == null) {
            return true;
        }
        Optional<BigDecimal> converted = conversion.toBase(amount, currency);
        if (converted.isEmpty()) {
            missingRates.add(conversion.pairLabel(currency));
            return false;
        }
        sum = sum.add(converted.get());
        includedCount++;
        return true;
    }

    /** Merge another accumulator (from the same conversion) into this one. */
    public MoneyAccumulator addAll(MoneyAccumulator other) {
        sum = sum.add(other.sum);
        includedCount += other.includedCount;
        missingRates.addAll(other.missingRates);
        return this;
    }

    /** Unrounded converted sum; use for ratios and further arithmetic. */
    public BigDecimal rawSum() {
        return sum;
    }

    /** Converted sum rounded to 2 decimal places, HALF_EVEN. */
    public BigDecimal amount() {
        return CurrencyConversion.round(sum);
    }

    /** Number of non-null amounts that were converted and included. */
    public long includedCount() {
        return includedCount;
    }

    public boolean isComplete() {
        return missingRates.isEmpty();
    }

    public List<String> missingRates() {
        return List.copyOf(missingRates);
    }

    public MoneyTotal total() {
        return new MoneyTotal(amount(), conversion.baseCurrency(), isComplete(), missingRates());
    }
}
