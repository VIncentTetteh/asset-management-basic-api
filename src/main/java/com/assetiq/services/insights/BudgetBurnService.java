package com.assetiq.services.insights;

import com.assetiq.enums.BudgetStatus;
import com.assetiq.models.Budget;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.BudgetRepository;
import com.assetiq.services.money.CurrencyConversion;
import com.assetiq.services.money.MoneyAccumulator;
import com.assetiq.services.money.MoneyAggregator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Where is spend going, and is it within budget?"
 *
 * <p>Answers with commitments as well as actuals. A budget that looks 60% spent
 * but has another 35% already committed on unfulfilled purchase orders is not
 * 40% available, and a dashboard that says it is will get someone into trouble.
 *
 * <p>Two things the previous financial analytics got wrong and this does not:
 * budgets are restricted to the window being reported on rather than every
 * budget the tenant has ever had, and draft budgets — which commit nothing —
 * are excluded. Burn is also compared against how much of the period has
 * elapsed, because 60% spent is good news in November and bad news in February.
 *
 * <p>Cost: one indexed range scan over {@code budget} for the tenant and window.
 * Spend and commitment are stored on the budget by the ledger, so no join to
 * expenses or purchase orders is needed.
 */
@Service
@Transactional(readOnly = true)
public class BudgetBurnService {

    private final BudgetRepository budgetRepository;
    private final MoneyAggregator moneyAggregator;

    public BudgetBurnService(BudgetRepository budgetRepository, MoneyAggregator moneyAggregator) {
        this.budgetRepository = budgetRepository;
        this.moneyAggregator = moneyAggregator;
    }

    /**
     * @param from  window start; defaults to the start of the current calendar year
     * @param to    window end; defaults to the end of the current calendar year
     */
    public Map<String, Object> burn(Organisation org, LocalDate from, LocalDate to,
                                    Set<InsightSection> granted) {
        LocalDate today = LocalDate.now();
        LocalDate windowStart = from != null ? from : today.withDayOfYear(1);
        LocalDate windowEnd = to != null ? to : today.withDayOfYear(1).plusYears(1).minusDays(1);
        if (windowEnd.isBefore(windowStart)) {
            throw new IllegalArgumentException("'to' must not be before 'from'");
        }
        CurrencyConversion fx = moneyAggregator.begin(org, today);

        Set<InsightSection> required = EnumSet.of(InsightSection.BUDGETS);
        Map<String, Object> response = InsightResponse.envelope(fx, today, required, granted);
        response.put("windowStart", windowStart.toString());
        response.put("windowEnd", windowEnd.toString());

        if (!granted.contains(InsightSection.BUDGETS)) {
            response.put("budgetCount", 0L);
            response.put("budgets", List.of());
            return InsightResponse.finish(response, fx);
        }

        List<Budget> budgets = budgetRepository.findOverlapping(org, windowStart, windowEnd).stream()
                // A draft budget has committed nothing and approves nothing; counting it
                // inflates the ceiling and makes every burn figure look comfortable.
                .filter(b -> b.getStatus() != BudgetStatus.DRAFT)
                .toList();

        MoneyAccumulator total = fx.newAccumulator();
        MoneyAccumulator spent = fx.newAccumulator();
        MoneyAccumulator committed = fx.newAccumulator();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Budget b : budgets) {
            total.add(b.getTotalAmount(), b.getCurrency());
            spent.add(b.getSpentAmount(), b.getCurrency());
            committed.add(b.getCommittedAmount(), b.getCurrency());
            rows.add(row(b, today, fx));
        }
        rows.sort(Comparator.comparing(
                (Map<String, Object> m) -> m.get("burnPercent") == null ? -1.0 : (Double) m.get("burnPercent"))
                .reversed());

        BigDecimal totalRaw = total.rawSum();
        BigDecimal usedRaw = spent.rawSum().add(committed.rawSum());

        response.put("budgetCount", (long) budgets.size());
        response.put("totalBudget", total.amount());
        response.put("spent", spent.amount());
        response.put("committed", committed.amount());
        response.put("available", CurrencyConversion.round(totalRaw.subtract(usedRaw)));
        response.put("spentPercent", InsightResponse.percentage(spent.rawSum(), totalRaw));
        response.put("committedPercent", InsightResponse.percentage(committed.rawSum(), totalRaw));
        // Burn counts committed money as gone, because for planning purposes it is.
        response.put("burnPercent", InsightResponse.percentage(usedRaw, totalRaw));

        Double elapsed = elapsedPercent(windowStart, windowEnd, today);
        response.put("periodElapsedPercent", elapsed);
        Double burn = InsightResponse.percentage(usedRaw, totalRaw);
        response.put("pacePercentagePoints", burn == null || elapsed == null
                ? null : Math.round((burn - elapsed) * 10.0) / 10.0);
        response.put("projectedSpendAtPeriodEnd", projection(usedRaw, elapsed));
        response.put("budgetsOverThreshold", rows.stream()
                .filter(m -> Boolean.TRUE.equals(m.get("overThreshold"))).count());
        response.put("budgets", rows);
        return InsightResponse.finish(response, fx);
    }

    private Map<String, Object> row(Budget b, LocalDate today, CurrencyConversion fx) {
        BigDecimal totalNative = b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal spentNative = b.getSpentAmount() != null ? b.getSpentAmount() : BigDecimal.ZERO;
        BigDecimal committedNative = b.getCommittedAmount() != null ? b.getCommittedAmount() : BigDecimal.ZERO;
        BigDecimal usedNative = spentNative.add(committedNative);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("name", b.getName());
        m.put("resourceType", "budget");
        m.put("status", b.getStatus() == null ? null : b.getStatus().name());
        m.put("departmentId", b.getDepartment() == null ? null : b.getDepartment().getId());
        m.put("periodStart", b.getPeriodStart() == null ? null : b.getPeriodStart().toString());
        m.put("periodEnd", b.getPeriodEnd() == null ? null : b.getPeriodEnd().toString());
        m.put("fiscalYear", b.getFiscalYear());
        m.put("nativeCurrency", b.getCurrency());
        m.put("total", fx.toBase(totalNative, b.getCurrency()).map(CurrencyConversion::round).orElse(null));
        m.put("spent", fx.toBase(spentNative, b.getCurrency()).map(CurrencyConversion::round).orElse(null));
        m.put("committed", fx.toBase(committedNative, b.getCurrency()).map(CurrencyConversion::round).orElse(null));
        m.put("available", fx.toBase(totalNative.subtract(usedNative), b.getCurrency())
                .map(CurrencyConversion::round).orElse(null));
        // Ratios are computed in the budget's own currency: no conversion is
        // involved, so a missing rate cannot distort them.
        Double burn = InsightResponse.percentage(usedNative, totalNative);
        m.put("burnPercent", burn);
        m.put("elapsedPercent", elapsedPercent(b.getPeriodStart(), b.getPeriodEnd(), today));
        m.put("alertThresholdPct", b.getAlertThresholdPct());
        m.put("overThreshold", burn != null && b.getAlertThresholdPct() != null
                && burn >= b.getAlertThresholdPct());
        m.put("filter", Map.of("budgetId", String.valueOf(b.getId())));
        return m;
    }

    /**
     * How much of the period has gone, clamped to 0-100. Null before the period
     * has a length, so a one-day budget never divides by zero.
     */
    static Double elapsedPercent(LocalDate start, LocalDate end, LocalDate today) {
        if (start == null || end == null) {
            return null;
        }
        long span = ChronoUnit.DAYS.between(start, end);
        if (span <= 0) {
            return null;
        }
        long gone = ChronoUnit.DAYS.between(start, today);
        double pct = Math.max(0, Math.min(span, gone)) * 100.0 / span;
        return Math.round(pct * 10.0) / 10.0;
    }

    /**
     * Straight-line projection of the full-period spend. Deliberately null in the
     * first days of a period: extrapolating from three days of data produces a
     * confident number that is worthless.
     */
    private BigDecimal projection(BigDecimal usedSoFar, Double elapsedPercent) {
        if (elapsedPercent == null || elapsedPercent < 5.0) {
            return null;
        }
        return CurrencyConversion.round(usedSoFar
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(elapsedPercent), CurrencyConversion.MONEY_SCALE,
                        CurrencyConversion.MONEY_ROUNDING));
    }
}
