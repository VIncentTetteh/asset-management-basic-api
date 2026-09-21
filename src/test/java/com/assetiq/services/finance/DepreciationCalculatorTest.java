package com.assetiq.services.finance;

import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.DepreciationMethod;
import com.assetiq.models.Asset;
import com.assetiq.models.Category;
import com.assetiq.models.DepreciationPolicy;
import com.assetiq.services.finance.DepreciationCalculator.Inputs;
import com.assetiq.services.finance.DepreciationCalculator.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DepreciationCalculator")
class DepreciationCalculatorTest {

    private static final LocalDate START = LocalDate.of(2024, 1, 15);

    private static Result calc(DepreciationMethod method, String cost, String residual, int life, int monthsLater) {
        return DepreciationCalculator.calculate(new Inputs(new BigDecimal(cost), new BigDecimal(residual), life,
                method, START, START.plusMonths(monthsLater), false));
    }

    @Nested
    @DisplayName("golden values")
    class Golden {

        @Test
        void straightLine() {
            Result r = calc(DepreciationMethod.STRAIGHT_LINE, "1200", "0", 12, 3);
            assertThat(r.accumulatedDepreciation()).isEqualByComparingTo("300.00");
            assertThat(r.netBookValue()).isEqualByComparingTo("900.00");
            assertThat(r.monthlyDepreciation()).isEqualByComparingTo("100.00");
            assertThat(r.fullyDepreciated()).isFalse();
            assertThat(r.configured()).isTrue();
        }

        @Test
        void straightLineCountsOnlyWholeMonths() {
            Result r = DepreciationCalculator.calculate(new Inputs(new BigDecimal("1200"), BigDecimal.ZERO, 12,
                    DepreciationMethod.STRAIGHT_LINE, START, START.plusMonths(3).minusDays(1), false));
            assertThat(r.accumulatedDepreciation()).isEqualByComparingTo("200.00");
            assertThat(r.monthsInService()).isEqualTo(2);
        }

        @Test
        void doubleDecliningBalanceSwitchesToStraightLine() {
            // rate 2/10 per month on opening book value; switches to SL in month 9
            Result r3 = calc(DepreciationMethod.DECLINING_BALANCE, "1000", "100", 10, 3);
            assertThat(r3.accumulatedDepreciation()).isEqualByComparingTo("488.00");
            assertThat(r3.netBookValue()).isEqualByComparingTo("512.00");
            assertThat(r3.monthlyDepreciation()).isEqualByComparingTo("102.40");

            Result r9 = calc(DepreciationMethod.DECLINING_BALANCE, "1000", "100", 10, 9);
            assertThat(r9.netBookValue()).isEqualByComparingTo("133.89");
            assertThat(r9.monthlyDepreciation()).isEqualByComparingTo("33.89");

            Result end = calc(DepreciationMethod.DECLINING_BALANCE, "1000", "100", 10, 10);
            assertThat(end.netBookValue()).isEqualByComparingTo("100.00");
            assertThat(end.fullyDepreciated()).isTrue();
            assertThat(end.monthlyDepreciation()).isEqualByComparingTo("0");
        }

        @Test
        void sumOfYearsDigitsPerMonth() {
            // n = 5, S = 15: months 1+2 carry 5/15 + 4/15 of 1500
            Result r = calc(DepreciationMethod.SUM_OF_YEARS_DIGITS, "1500", "0", 5, 2);
            assertThat(r.accumulatedDepreciation()).isEqualByComparingTo("900.00");
            assertThat(r.netBookValue()).isEqualByComparingTo("600.00");
            assertThat(r.monthlyDepreciation()).isEqualByComparingTo("300.00");
        }

        @Test
        void unitsOfProductionFallsBackToStraightLine() {
            Result uop = calc(DepreciationMethod.UNITS_OF_PRODUCTION, "1200", "200", 10, 4);
            Result sl = calc(DepreciationMethod.STRAIGHT_LINE, "1200", "200", 10, 4);
            assertThat(uop.netBookValue()).isEqualByComparingTo(sl.netBookValue()).isEqualByComparingTo("800.00");
            assertThat(uop.monthlyDepreciation()).isEqualByComparingTo("100.00");
        }
    }

    @Nested
    @DisplayName("edge rules")
    class Edges {

        @Test
        void noUsefulLifeMeansNoDepreciationAndUnconfigured() {
            Result r = DepreciationCalculator.calculate(new Inputs(new BigDecimal("500"), null, null,
                    null, START, START.plusMonths(30), false));
            assertThat(r.netBookValue()).isEqualByComparingTo("500.00");
            assertThat(r.accumulatedDepreciation()).isEqualByComparingTo("0");
            assertThat(r.monthlyDepreciation()).isEqualByComparingTo("0");
            assertThat(r.configured()).isFalse();
        }

        @Test
        void noPurchaseDateIsUnconfigured() {
            Result r = DepreciationCalculator.calculate(new Inputs(new BigDecimal("500"), null, 12,
                    DepreciationMethod.STRAIGHT_LINE, null, START, false));
            assertThat(r.netBookValue()).isEqualByComparingTo("500.00");
            assertThat(r.configured()).isFalse();
        }

        @Test
        void nullCostHasNoBookValue() {
            Result r = DepreciationCalculator.calculate(new Inputs(null, null, 12,
                    DepreciationMethod.STRAIGHT_LINE, START, START.plusMonths(3), false));
            assertThat(r.netBookValue()).isNull();
            assertThat(r.monthlyDepreciation()).isEqualByComparingTo("0");
        }

        @Test
        void disposedAssetHasNoMonthlyCharge() {
            Result r = DepreciationCalculator.calculate(new Inputs(new BigDecimal("1200"), BigDecimal.ZERO, 12,
                    DepreciationMethod.STRAIGHT_LINE, START, START.plusMonths(3), true));
            assertThat(r.netBookValue()).isEqualByComparingTo("900.00");
            assertThat(r.monthlyDepreciation()).isEqualByComparingTo("0");
        }

        @Test
        void notYetInServiceHasNoDepreciation() {
            Result r = DepreciationCalculator.calculate(new Inputs(new BigDecimal("1200"), BigDecimal.ZERO, 12,
                    DepreciationMethod.STRAIGHT_LINE, START, START.minusDays(3), false));
            assertThat(r.netBookValue()).isEqualByComparingTo("1200.00");
            assertThat(r.monthlyDepreciation()).isEqualByComparingTo("0");
        }

        @Test
        void residualAboveCostIsClampedAndNothingDepreciates() {
            Result r = calc(DepreciationMethod.STRAIGHT_LINE, "100", "150", 12, 6);
            assertThat(r.netBookValue()).isEqualByComparingTo("100.00");
            assertThat(r.fullyDepreciated()).isTrue();
            assertThat(r.monthlyDepreciation()).isEqualByComparingTo("0");
        }

        @Test
        void neverBelowResidualLongAfterLife() {
            Result r = calc(DepreciationMethod.STRAIGHT_LINE, "1000", "100", 12, 120);
            assertThat(r.netBookValue()).isEqualByComparingTo("100.00");
            assertThat(r.fullyDepreciated()).isTrue();
            assertThat(r.monthlyDepreciation()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("parameter resolution")
    class Resolution {

        @Test
        void assetFieldsWinOverPolicy() {
            Asset a = asset();
            a.setUsefulLifeMonths(12);
            a.setResidualValue(BigDecimal.ZERO);
            a.setDepreciationMethod(DepreciationMethod.STRAIGHT_LINE);
            a.setCategory(categoryWith(policy(DepreciationMethod.DECLINING_BALANCE, 60, "50")));

            Result r = DepreciationCalculator.forAsset(a, START.plusMonths(3));
            assertThat(r.usefulLifeMonths()).isEqualTo(12);
            assertThat(r.method()).isEqualTo(DepreciationMethod.STRAIGHT_LINE);
            assertThat(r.netBookValue()).isEqualByComparingTo("900.00");
        }

        @Test
        void missingFieldsFallBackToCategoryPolicy() {
            Asset a = asset();
            a.setDepreciationMethod(null);
            a.setCategory(categoryWith(policy(DepreciationMethod.SUM_OF_YEARS_DIGITS, 5, "10")));

            Result r = DepreciationCalculator.forAsset(a, START.plusMonths(2));
            assertThat(r.method()).isEqualTo(DepreciationMethod.SUM_OF_YEARS_DIGITS);
            assertThat(r.usefulLifeMonths()).isEqualTo(5);
            assertThat(r.residualValue()).isEqualByComparingTo("120.00"); // 10% of 1200
            // depreciable 1080 x 9/15
            assertThat(r.accumulatedDepreciation()).isEqualByComparingTo("648.00");
        }

        @Test
        void nothingConfiguredAnywhere() {
            Asset a = asset();
            a.setCategory(categoryWith(null));
            Result r = DepreciationCalculator.forAsset(a, START.plusMonths(2));
            assertThat(r.configured()).isFalse();
            assertThat(r.netBookValue()).isEqualByComparingTo("1200.00");
        }

        @Test
        void disposedStatusIsHonoured() {
            Asset a = asset();
            a.setUsefulLifeMonths(12);
            a.setStatus(AssetStatus.DISPOSED);
            assertThat(DepreciationCalculator.forAsset(a, START.plusMonths(2)).monthlyDepreciation())
                    .isEqualByComparingTo("0");
        }

        private Asset asset() {
            Asset a = new Asset();
            a.setPurchaseCost(new BigDecimal("1200"));
            a.setPurchaseDate(START);
            a.setStatus(AssetStatus.IN_USE);
            return a;
        }

        private Category categoryWith(DepreciationPolicy policy) {
            Category c = new Category();
            c.setDepreciationPolicy(policy);
            return c;
        }

        private DepreciationPolicy policy(DepreciationMethod method, int life, String salvagePct) {
            DepreciationPolicy p = new DepreciationPolicy();
            p.setMethod(method);
            p.setUsefulLifeMonths(life);
            p.setSalvageValuePercent(new BigDecimal(salvagePct));
            return p;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(DepreciationMethod.class)
    @DisplayName("properties hold for every method over random inputs")
    void propertiesHoldForEveryMethod(DepreciationMethod method) {
        Random random = new Random(42L + method.ordinal());
        for (int trial = 0; trial < 200; trial++) {
            BigDecimal cost = BigDecimal.valueOf(1 + random.nextInt(1_000_000), 2).add(BigDecimal.ONE);
            BigDecimal residual = random.nextInt(4) == 0 ? BigDecimal.ZERO
                    : cost.multiply(BigDecimal.valueOf(random.nextInt(40), 2)).setScale(2, java.math.RoundingMode.DOWN);
            int life = 1 + random.nextInt(120);

            BigDecimal previousAccumulated = BigDecimal.ZERO;
            BigDecimal sumOfCharges = BigDecimal.ZERO;
            for (int m = 0; m <= life + 3; m++) {
                Result r = DepreciationCalculator.calculate(new Inputs(cost, residual, life, method,
                        START, START.plusMonths(m), false));
                assertThat(r.netBookValue()).as("NBV >= residual").isGreaterThanOrEqualTo(residual);
                assertThat(r.netBookValue()).as("NBV <= cost").isLessThanOrEqualTo(cost);
                assertThat(r.accumulatedDepreciation()).as("monotone").isGreaterThanOrEqualTo(previousAccumulated);
                assertThat(r.monthlyDepreciation()).as("non-negative charge").isGreaterThanOrEqualTo(BigDecimal.ZERO);
                assertThat(r.accumulatedDepreciation()).as("charges reconcile")
                        .isEqualByComparingTo(sumOfCharges);
                assertThat(r.netBookValue().add(r.accumulatedDepreciation())).isEqualByComparingTo(cost);
                if (m >= life) {
                    assertThat(r.fullyDepreciated()).isTrue();
                    assertThat(r.netBookValue()).isEqualByComparingTo(residual);
                    assertThat(r.monthlyDepreciation()).isEqualByComparingTo("0");
                }
                previousAccumulated = r.accumulatedDepreciation();
                sumOfCharges = sumOfCharges.add(r.monthlyDepreciation());
            }
        }
    }
}
