package com.assetiq.services.finance;

import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.DepreciationMethod;
import com.assetiq.models.Asset;
import com.assetiq.models.DepreciationPolicy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The single depreciation engine. Every book value, accumulated depreciation and
 * monthly charge shown anywhere in AssetIQ comes from here, so the dashboard,
 * analytics, reports and the asset detail always agree.
 *
 * <p>Pure and stateless: no I/O, no clock (the caller passes {@code asOf}).
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li><b>Parameters</b> come from the asset's own fields ({@code depreciationMethod},
 *       {@code usefulLifeMonths}, {@code residualValue}); each missing field falls back
 *       to the category's {@link DepreciationPolicy} ({@code method},
 *       {@code usefulLifeMonths}, {@code salvageValuePercent} of cost). Method defaults
 *       to straight-line.</li>
 *   <li><b>Time</b> is counted in whole months of service from the purchase date
 *       (the asset has no separate in-service date): an asset bought on 10 Jan has one
 *       month of depreciation on 10 Feb. The "monthly depreciation" is the charge for
 *       the month of service in progress on {@code asOf}.</li>
 *   <li><b>Unconfigured</b> assets (no useful life anywhere, no purchase date, or no
 *       cost) are not depreciated: NBV equals cost and {@link Result#configured()} is
 *       false so the UI can prompt for setup.</li>
 *   <li>NBV never drops below the residual value; once the useful life has elapsed the
 *       asset is fully depreciated and its monthly charge is zero. A residual above
 *       cost is clamped to cost (nothing to depreciate).</li>
 *   <li><b>Disposed</b> assets carry no monthly charge.</li>
 * </ul>
 *
 * <h2>Methods</h2>
 * <ul>
 *   <li>{@code STRAIGHT_LINE}: (cost - residual) x months / life.</li>
 *   <li>{@code DECLINING_BALANCE}: double-declining balance at a monthly rate of
 *       2 / life on the opening book value, switching to straight-line over the
 *       remaining months as soon as that gives the larger charge, so the residual is
 *       reached exactly at the end of the useful life.</li>
 *   <li>{@code SUM_OF_YEARS_DIGITS}: applied per month of life ("sum of the months'
 *       digits"): month k of n is charged (n - k + 1) / (n(n + 1) / 2) of the
 *       depreciable amount.</li>
 *   <li>{@code UNITS_OF_PRODUCTION}: AssetIQ records no usage (units produced / hours
 *       run), so this method falls back to straight-line over the useful life.</li>
 * </ul>
 *
 * <p>Accumulated depreciation is computed unrounded and rounded once to 2 dp
 * (HALF_EVEN); the monthly charge is the difference of two rounded accumulations, so
 * charges always sum exactly to the accumulated figure.
 */
public final class DepreciationCalculator {

    public static final int MONEY_SCALE = 2;
    public static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_EVEN;
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DOUBLE_DECLINING_FACTOR = BigDecimal.valueOf(2);

    private DepreciationCalculator() {
    }

    /** Resolved inputs for one calculation. */
    public record Inputs(BigDecimal cost,
                         BigDecimal residualValue,
                         Integer usefulLifeMonths,
                         DepreciationMethod method,
                         LocalDate inServiceDate,
                         LocalDate asOf,
                         boolean disposed) {
    }

    /**
     * Outcome of a calculation, in the asset's own currency.
     *
     * @param accumulatedDepreciation depreciation charged up to {@code asOf}
     * @param netBookValue            cost minus accumulated depreciation; null when cost is unknown
     * @param monthlyDepreciation     charge for the month of service in progress (zero when
     *                                fully depreciated, disposed, unconfigured or not yet in service)
     * @param fullyDepreciated        true once the useful life has elapsed (or nothing is depreciable)
     * @param configured              false when no useful life / purchase date / cost is available
     * @param method                  the effective method
     * @param usefulLifeMonths        the effective useful life (null when unconfigured)
     * @param residualValue           the effective residual value
     * @param monthsInService         whole months of service at {@code asOf}
     */
    public record Result(BigDecimal accumulatedDepreciation,
                         BigDecimal netBookValue,
                         BigDecimal monthlyDepreciation,
                         boolean fullyDepreciated,
                         boolean configured,
                         DepreciationMethod method,
                         Integer usefulLifeMonths,
                         BigDecimal residualValue,
                         long monthsInService) {
    }

    /** Resolve an asset's parameters (asset fields, then category policy) and calculate. */
    public static Result forAsset(Asset asset, LocalDate asOf) {
        return calculate(resolve(asset, asOf));
    }

    /** Resolve the effective inputs for {@code asset} without calculating. */
    public static Inputs resolve(Asset asset, LocalDate asOf) {
        DepreciationPolicy policy = asset.getCategory() != null ? asset.getCategory().getDepreciationPolicy() : null;
        BigDecimal cost = asset.getPurchaseCost();

        Integer life = positive(asset.getUsefulLifeMonths());
        if (life == null && policy != null) {
            life = positive(policy.getUsefulLifeMonths());
        }

        DepreciationMethod method = asset.getDepreciationMethod();
        if (method == null && policy != null) {
            method = policy.getMethod();
        }

        BigDecimal residual = asset.getResidualValue();
        if (residual == null && policy != null && policy.getSalvageValuePercent() != null && cost != null) {
            residual = cost.multiply(policy.getSalvageValuePercent()).divide(HUNDRED, MC);
        }

        return new Inputs(cost, residual, life, method, asset.getPurchaseDate(), asOf,
                asset.getStatus() == AssetStatus.DISPOSED);
    }

    /** Calculate from already-resolved inputs. */
    public static Result calculate(Inputs in) {
        DepreciationMethod method = in.method() != null ? in.method() : DepreciationMethod.STRAIGHT_LINE;
        BigDecimal cost = in.cost();
        if (cost == null) {
            return new Result(money(BigDecimal.ZERO), null, money(BigDecimal.ZERO), false, false,
                    method, in.usefulLifeMonths(), in.residualValue(), 0);
        }
        BigDecimal residual = clampResidual(in.residualValue(), cost);
        Integer life = positive(in.usefulLifeMonths());
        LocalDate asOf = in.asOf() != null ? in.asOf() : LocalDate.now();

        if (life == null || in.inServiceDate() == null) {
            return new Result(money(BigDecimal.ZERO), money(cost), money(BigDecimal.ZERO), false, false,
                    method, life, money(residual), 0);
        }

        long months = in.inServiceDate().isAfter(asOf) ? 0
                : Math.max(0, ChronoUnit.MONTHS.between(in.inServiceDate(), asOf));
        boolean inService = !in.inServiceDate().isAfter(asOf);
        BigDecimal depreciable = cost.subtract(residual);

        BigDecimal accumulated = money(accumulatedAfter(method, cost, residual, life, months));
        boolean fully = months >= life || depreciable.signum() <= 0;

        BigDecimal monthly = BigDecimal.ZERO;
        if (inService && !fully && !in.disposed()) {
            monthly = money(accumulatedAfter(method, cost, residual, life, months + 1)).subtract(accumulated);
        }

        return new Result(accumulated, money(cost.subtract(accumulated)), money(monthly), fully, true,
                method, life, money(residual), months);
    }

    /** Unrounded accumulated depreciation after {@code months} whole months of service. */
    static BigDecimal accumulatedAfter(DepreciationMethod method, BigDecimal cost, BigDecimal residual,
                                       int life, long months) {
        BigDecimal depreciable = cost.subtract(residual);
        if (depreciable.signum() <= 0 || months <= 0) {
            return BigDecimal.ZERO;
        }
        if (months >= life) {
            return depreciable;
        }
        return switch (method) {
            case DECLINING_BALANCE -> doubleDeclining(cost, residual, life, months);
            case SUM_OF_YEARS_DIGITS -> sumOfDigits(depreciable, life, months);
            // No usage data is recorded, so units-of-production depreciates on time.
            case STRAIGHT_LINE, UNITS_OF_PRODUCTION -> straightLine(depreciable, life, months);
        };
    }

    private static BigDecimal straightLine(BigDecimal depreciable, int life, long months) {
        return depreciable.multiply(BigDecimal.valueOf(months)).divide(BigDecimal.valueOf(life), MC);
    }

    private static BigDecimal sumOfDigits(BigDecimal depreciable, int life, long months) {
        // Sum of (n - k + 1) for k = 1..m  =  m*n - m(m-1)/2
        long n = life;
        long numerator = months * n - months * (months - 1) / 2;
        long denominator = n * (n + 1) / 2;
        return depreciable.multiply(BigDecimal.valueOf(numerator)).divide(BigDecimal.valueOf(denominator), MC);
    }

    private static BigDecimal doubleDeclining(BigDecimal cost, BigDecimal residual, int life, long months) {
        BigDecimal rate = DOUBLE_DECLINING_FACTOR.divide(BigDecimal.valueOf(life), MC);
        BigDecimal book = cost;
        for (long k = 1; k <= months; k++) {
            BigDecimal remaining = book.subtract(residual);
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal declining = book.multiply(rate, MC);
            BigDecimal straight = remaining.divide(BigDecimal.valueOf(life - k + 1), MC);
            BigDecimal charge = declining.max(straight).min(remaining);
            book = book.subtract(charge);
        }
        return cost.subtract(book);
    }

    private static BigDecimal clampResidual(BigDecimal residual, BigDecimal cost) {
        if (residual == null || residual.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return residual.min(cost.max(BigDecimal.ZERO));
    }

    private static Integer positive(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
