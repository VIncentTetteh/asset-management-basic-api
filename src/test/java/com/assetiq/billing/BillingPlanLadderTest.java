package com.assetiq.billing;

import com.assetiq.models.SubscriptionPlan;
import com.assetiq.repositories.SubscriptionPlanRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the shape of the public pricing ladder.
 *
 * <p>These assertions exist because the ladder silently broke once. During the
 * GHS→USD migration the amount defaults were re-denominated but the tier
 * boundaries and the seeder's own documentation were not, leaving Business at
 * $100 against Basic's $99 — a one-dollar step for forty times the assets.
 * Nothing failed, nothing warned, and the public pricing page would have
 * rendered it to customers.
 *
 * <p>Deliberately asserts <em>relationships</em> rather than exact prices, so
 * promotional pricing and regional experiments stay possible without editing
 * tests — but a ladder that stops climbing fails the build.
 */
@SpringBootTest
@DisplayName("Public pricing ladder")
class BillingPlanLadderTest {

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Test
    @DisplayName("each paid tier costs meaningfully more than the one below it")
    void priceIncreasesAcrossTheLadder() {
        SubscriptionPlan freemium = plan("FREEMIUM");
        SubscriptionPlan basic = plan("BASIC");
        SubscriptionPlan business = plan("BUSINESS");

        assertThat(freemium.getAmountMinor()).as("Freemium is free").isZero();
        assertThat(basic.getAmountMinor()).as("Basic must cost something").isPositive();

        assertThat(business.getAmountMinor())
                .as("Business must be a real step up from Basic, not a rounding difference")
                .isGreaterThan(basic.getAmountMinor() * 2);
    }

    @Test
    @DisplayName("quotas climb in step with price")
    void quotasIncreaseAcrossTheLadder() {
        SubscriptionPlan freemium = plan("FREEMIUM");
        SubscriptionPlan basic = plan("BASIC");
        SubscriptionPlan business = plan("BUSINESS");

        assertThat(basic.getMaxAssets()).isGreaterThan(freemium.getMaxAssets());
        assertThat(business.getMaxAssets()).isGreaterThan(basic.getMaxAssets());

        assertThat(basic.getMaxEmployees()).isGreaterThan(freemium.getMaxEmployees());
        assertThat(business.getMaxEmployees()).isGreaterThan(basic.getMaxEmployees());
    }

    @Test
    @DisplayName("the annual plan is cheaper than paying monthly for a year")
    void annualUndercutsTwelveMonths() {
        long monthly = plan("BUSINESS").getAmountMinor();
        long annual = plan("BUSINESS_ANNUAL").getAmountMinor();

        assertThat(annual)
                .as("an annual plan that costs more than 12 monthly payments is a bug, "
                        + "and this is exactly what a stale monthly figure produces")
                .isLessThan(monthly * 12);
        assertThat(annual)
                .as("...but it should still be recognisably a year's worth")
                .isGreaterThan(monthly * 6);
    }

    @Test
    @DisplayName("every advertised tier is seeded and active")
    void ladderIsComplete() {
        for (String code : new String[] {"FREEMIUM", "BASIC", "BUSINESS", "BUSINESS_ANNUAL", "ENTERPRISE"}) {
            assertThat(plan(code).getActive())
                    .as("%s is on the public pricing surface and must be active", code)
                    .isTrue();
        }
    }

    private SubscriptionPlan plan(String code) {
        return planRepository.findByCodeAndDeletedAtIsNull(code)
                .orElseThrow(() -> new AssertionError("Plan " + code + " is not seeded"));
    }
}
