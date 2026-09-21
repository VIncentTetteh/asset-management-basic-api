package com.assetiq.services.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Source of exchange rates for a {@link CurrencyConversion}: the multiplier that
 * turns one unit of {@code from} into {@code to}, or empty when none exists.
 */
@FunctionalInterface
public interface RateLookup {

    Optional<BigDecimal> find(String from, String to, LocalDate asOf);
}
