package com.assetiq.services.insights;

import com.assetiq.services.money.CurrencyConversion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared shape for every insight response.
 *
 * <p>Three rules are enforced here rather than remembered at each call site:
 * every response states the currency its money is in and whether any amount was
 * dropped for want of a rate; every response names the sections that were left
 * out because the caller may not see them; and every ratio refuses to divide by
 * zero, so a tenant with nothing yet sees {@code null}, not {@code NaN}.
 */
public final class InsightResponse {

    private InsightResponse() {
    }

    /**
     * Start a response with the standard envelope fields.
     *
     * <p>The currency block is written here to fix its position in the payload,
     * but it is only true once the aggregation has run — a missing rate is
     * discovered while summing, not before. {@link #finish} re-stamps it, which
     * updates the values in place without moving the keys.
     */
    public static Map<String, Object> envelope(CurrencyConversion fx, LocalDate asOf,
                                               Set<InsightSection> required, Set<InsightSection> granted) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("asOf", asOf.toString());
        fx.putMetadata(response);
        response.put("withheldSections", withheld(required, granted));
        return response;
    }

    /** Labels of the sections this answer wanted but the caller may not read. */
    public static List<String> withheld(Set<InsightSection> required, Set<InsightSection> granted) {
        return required.stream()
                .filter(s -> !granted.contains(s))
                .map(InsightSection::label)
                .sorted()
                .toList();
    }

    /**
     * Close a response: re-stamp the currency block with what the completed
     * aggregation actually found, then record the generation time.
     */
    public static Map<String, Object> finish(Map<String, Object> response, CurrencyConversion fx) {
        fx.putMetadata(response);
        return generatedNow(response);
    }

    /** Stamp the generation time last, so it reads at the bottom of the payload. */
    public static Map<String, Object> generatedNow(Map<String, Object> response) {
        response.put("generatedAt", Instant.now().toString());
        return response;
    }

    /**
     * {@code part / whole} as a percentage to one decimal place, or {@code null}
     * when {@code whole} is zero. A share of an empty estate is not zero — it is
     * undefined, and a dashboard that prints 0% for it is lying to a new tenant.
     */
    public static Double percentage(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0 || part == null) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(whole, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** Integer form of {@link #percentage(BigDecimal, BigDecimal)}. */
    public static Double percentage(long part, long whole) {
        if (whole <= 0) {
            return null;
        }
        return percentage(BigDecimal.valueOf(part), BigDecimal.valueOf(whole));
    }
}
