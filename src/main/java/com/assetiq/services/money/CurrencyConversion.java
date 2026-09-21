package com.assetiq.services.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * One aggregation pass into a single base currency at a fixed date.
 *
 * <p>Rate lookups are memoised per source currency for the lifetime of this
 * object, so an aggregate over N records costs at most one lookup per distinct
 * currency rather than N queries. Every amount that could not be converted is
 * recorded here as well as on the accumulator that saw it, so a response built
 * from several totals can report a single {@code complete}/{@code missingRates} pair.
 *
 * <p>Create through {@link MoneyAggregator}; not thread-safe.
 */
public final class CurrencyConversion {

    /** Response key for the ISO-4217 code every money figure is expressed in. */
    public static final String FIELD_CURRENCY = "currency";
    /** Response key: false when some amounts were excluded for lack of a rate. */
    public static final String FIELD_COMPLETE = "complete";
    /** Response key: sorted {@code "FROM->TO"} pairs that had no rate. */
    public static final String FIELD_MISSING_RATES = "missingRates";

    public static final int MONEY_SCALE = 2;
    public static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_EVEN;

    private final String baseCurrency;
    private final LocalDate asOf;
    private final RateLookup rateLookup;
    private final Map<String, Optional<BigDecimal>> rateCache = new HashMap<>();
    private final SortedSet<String> missingRates = new TreeSet<>();

    public CurrencyConversion(String baseCurrency, LocalDate asOf, RateLookup rateLookup) {
        if (baseCurrency == null || baseCurrency.isBlank()) {
            throw new IllegalArgumentException("Base currency is required");
        }
        this.baseCurrency = baseCurrency.trim().toUpperCase(Locale.ROOT);
        this.asOf = asOf != null ? asOf : LocalDate.now();
        this.rateLookup = rateLookup;
    }

    public String baseCurrency() {
        return baseCurrency;
    }

    public LocalDate asOf() {
        return asOf;
    }

    public MoneyAccumulator newAccumulator() {
        return new MoneyAccumulator(this);
    }

    /** Sum {@code amountFn} over {@code items}, each amount in {@code currencyFn}'s currency. */
    public <T> MoneyAccumulator sum(Iterable<T> items,
                                   Function<T, BigDecimal> amountFn,
                                   Function<T, String> currencyFn) {
        MoneyAccumulator acc = newAccumulator();
        for (T item : items) {
            acc.add(amountFn.apply(item), currencyFn.apply(item));
        }
        return acc;
    }

    /**
     * Convert one amount into the base currency without rounding.
     *
     * @return the converted amount ({@code ZERO} for a null amount), or empty when
     *         no rate exists - in which case the pair is recorded as missing
     */
    public Optional<BigDecimal> toBase(BigDecimal amount, String currency) {
        if (amount == null) {
            return Optional.of(BigDecimal.ZERO);
        }
        String from = normalise(currency);
        if (from.equals(baseCurrency)) {
            return Optional.of(amount);
        }
        Optional<BigDecimal> rate = rateCache.computeIfAbsent(from, this::lookup);
        if (rate.isEmpty()) {
            missingRates.add(pairLabel(from));
            return Optional.empty();
        }
        return Optional.of(amount.multiply(rate.get()));
    }

    /** True when every amount converted through this pass had a rate. */
    public boolean isComplete() {
        return missingRates.isEmpty();
    }

    public List<String> missingRates() {
        return List.copyOf(missingRates);
    }

    /** Adds {@code currency}, {@code complete} and {@code missingRates} to a response map. */
    public Map<String, Object> putMetadata(Map<String, Object> target) {
        target.put(FIELD_CURRENCY, baseCurrency);
        target.put(FIELD_COMPLETE, isComplete());
        target.put(FIELD_MISSING_RATES, missingRates());
        return target;
    }

    /** Round a money value to scale 2, HALF_EVEN (null is treated as zero). */
    public static BigDecimal round(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    String pairLabel(String currency) {
        return normalise(currency) + "->" + baseCurrency;
    }

    private String normalise(String currency) {
        if (currency == null || currency.isBlank()) {
            return baseCurrency;
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private Optional<BigDecimal> lookup(String from) {
        Optional<BigDecimal> rate = rateLookup.find(from, baseCurrency, asOf);
        return rate != null ? rate : Optional.empty();
    }
}
