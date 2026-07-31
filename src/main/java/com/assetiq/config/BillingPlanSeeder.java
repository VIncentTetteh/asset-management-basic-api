package com.assetiq.config;

import com.assetiq.enums.BillingInterval;
import com.assetiq.enums.BillingPlanTier;
import com.assetiq.models.SubscriptionPlan;
import com.assetiq.repositories.SubscriptionPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seed the public subscription plan ladder.
 *
 * <p>AssetIQ's public pricing surface lists four packages: Freemium, Basic,
 * Business, and Enterprise. Amounts are expressed in the platform's minor unit
 * (currency × 100), matching Paystack's minor-unit convention. The currency is
 * whatever {@code app.billing.default-currency} resolves to — USD since the
 * multi-currency migration, not the GHS this once assumed.
 *
 * <ul>
 *   <li><b>FREEMIUM</b> — free. 50 assets, 5 employees.</li>
 *   <li><b>BASIC</b> — 99/month. 250 assets, 10 employees, core modules.</li>
 *   <li><b>BUSINESS</b> — 799/month. 10,000 assets, 250 employees,
 *       full observability + audit retention.</li>
 *   <li><b>ENTERPRISE</b> — quote-based, unlimited, SSO, dedicated support.</li>
 * </ul>
 *
 * <p>Keep these figures and the {@code @Value} defaults below in agreement. They
 * silently diverged once already: the defaults were re-denominated to USD while
 * this list still said GHS, leaving Business at $100 against Basic's $99.
 *
 * <p>Every numeric is overridable via {@code application.yml} / env vars so
 * promotional pricing and regional experiments don't require a code change.
 * Paystack plan codes are injected when operations creates them in the
 * Paystack dashboard and plugs the code into the env.
 */
@Component
public class BillingPlanSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BillingPlanSeeder.class);

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Value("${app.billing.default-currency:USD}")
    private String defaultCurrency;

    // ── Basic ────────────────────────────────────────────────────────────────
    @Value("${app.billing.plans.basic.amount-minor:9900}")
    private Long basicAmount;

    @Value("${app.billing.plans.basic.paystack-plan-code:}")
    private String basicPaystackPlanCode;

    // ── Business ($799/mo = 79900 cents) ────────────────────────────────────
    // Was 10000 ($100) — a dollar above Basic's $99, which is not a ladder. The
    // amount drifted from the documented 799 during the GHS→USD migration while
    // the tier boundaries (10,000 assets / 250 users) stayed put.
    @Value("${app.billing.plans.business.amount-minor:79900}")
    private Long businessAmount;

    @Value("${app.billing.plans.business.paystack-plan-code:}")
    private String businessPaystackPlanCode;

    // ── Business Annual ($8,629.20/yr = 862920 cents, 10% off $9,588) ───────
    // Re-derived from the corrected monthly: 799 × 12 = 9,588, less the same 10%
    // annual discount the previous figure used.
    @Value("${app.billing.plans.business-annual.amount-minor:862920}")
    private Long businessAnnualAmount;

    @Value("${app.billing.plans.business-annual.paystack-plan-code:}")
    private String businessAnnualPaystackPlanCode;

    public BillingPlanSeeder(SubscriptionPlanRepository subscriptionPlanRepository) {
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedFreemium();
        seedBasic();
        seedBusiness();
        seedBusinessAnnual();
        seedEnterprise();

        deactivateRemovedPlans();
    }

    // ── Public ladder ────────────────────────────────────────────────────────

    private void seedFreemium() {
        upsert("FREEMIUM", "Freemium", BillingPlanTier.FREEMIUM, BillingInterval.MONTHLY,
                0L, defaultCurrency, 50, 5, false, 365, null, null, true);
    }

    private void seedBasic() {
        upsert("BASIC", "Basic", BillingPlanTier.BASIC, BillingInterval.MONTHLY,
                basicAmount, defaultCurrency, 250, 10, false, 90,
                blankToNull(basicPaystackPlanCode), null, true);
    }

    /**
     * Remove previous package names from the active catalog. Rows are left in
     * storage for FK integrity, but they are inactive and filtered from APIs.
     */
    private void deactivateRemovedPlans() {
        for (String code : new String[] {
                "FREE_TRIAL", "STARTER_GHS", "STARTER", "GROWTH_GHS", "GROWTH",
                "PREMIUM", "PROFESSIONAL", "BUSINESS_GHS", "ENTERPRISE_CUSTOM"
        }) {
            deactivatePlan(code);
        }
    }

    private void deactivatePlan(String code) {
        subscriptionPlanRepository.findByCodeAndDeletedAtIsNull(code).ifPresent(plan -> {
            plan.setActive(false);
            subscriptionPlanRepository.save(plan);
            log.info("[BILLING] Deactivated removed subscription package {}", code);
        });
    }

    private void seedBusiness() {
        upsert("BUSINESS", "Business", BillingPlanTier.BUSINESS, BillingInterval.MONTHLY,
                businessAmount, defaultCurrency, 10_000, 250, true, 1_825,
                blankToNull(businessPaystackPlanCode), null, true);
    }

    private void seedBusinessAnnual() {
        upsert("BUSINESS_ANNUAL", "Business (Annual)", BillingPlanTier.BUSINESS, BillingInterval.ANNUALLY,
                businessAnnualAmount, defaultCurrency, 10_000, 250, true, 1_825,
                blankToNull(businessAnnualPaystackPlanCode),
                new java.math.BigDecimal("10.00"), true);
    }

    private void seedEnterprise() {
        // 0 amount + null paystack code → portal renders a "Contact sales" CTA
        // rather than a checkout button. The tier is still active so quota
        // resolution works if sales upgrades a tenant manually.
        upsert("ENTERPRISE", "Enterprise", BillingPlanTier.ENTERPRISE, BillingInterval.MONTHLY,
                0L, defaultCurrency, Integer.MAX_VALUE, Integer.MAX_VALUE, true, 3_650, null, null, true);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private void upsert(
            String code,
            String name,
            BillingPlanTier tier,
            BillingInterval interval,
            Long amountMinor,
            String currency,
            Integer maxAssets,
            Integer maxEmployees,
            Boolean analyticsEnabled,
            Integer retentionDays,
            String paystackPlanCode,
            java.math.BigDecimal discountPercent,
            boolean active) {
        SubscriptionPlan plan = subscriptionPlanRepository.findByCodeAndDeletedAtIsNull(code)
                .orElseGet(SubscriptionPlan::new);
        plan.setCode(code);
        plan.setName(name);
        plan.setTier(tier);
        plan.setInterval(interval);
        plan.setAmountMinor(amountMinor);
        plan.setCurrency(currency);
        plan.setMaxAssets(maxAssets);
        plan.setMaxEmployees(maxEmployees);
        plan.setAnalyticsEnabled(analyticsEnabled);
        plan.setAuditRetentionDays(retentionDays);
        if (paystackPlanCode != null) {
            plan.setPaystackPlanCode(paystackPlanCode);
        }
        plan.setDiscountPercent(discountPercent);
        plan.setActive(active);
        subscriptionPlanRepository.save(plan);
        log.info("[BILLING] Ensured subscription plan {} ({})", code, name);
    }
}
