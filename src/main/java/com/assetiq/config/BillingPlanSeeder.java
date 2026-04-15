package com.assetiq.config;

import com.assetiq.enums.BillingInterval;
import com.assetiq.enums.BillingPlanTier;
import com.assetiq.models.SubscriptionPlan;
import com.assetiq.repositories.SubscriptionPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BillingPlanSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BillingPlanSeeder.class);

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @org.springframework.beans.factory.annotation.Value("${app.billing.default-currency:GHS}")
    private String defaultCurrency;

    @org.springframework.beans.factory.annotation.Value("${app.billing.plans.basic.amount-minor:2000000}")
    private Long basicAmount;

    @org.springframework.beans.factory.annotation.Value("${app.billing.plans.premium.amount-minor:10000000}")
    private Long premiumAmount;

    @org.springframework.beans.factory.annotation.Value("${app.billing.plans.basic.paystack-plan-code:}")
    private String basicPaystackPlanCode;

    @org.springframework.beans.factory.annotation.Value("${app.billing.plans.premium.paystack-plan-code:}")
    private String premiumPaystackPlanCode;

    public BillingPlanSeeder(SubscriptionPlanRepository subscriptionPlanRepository) {
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedFreemium();
        seedBasic();
        seedPremium();
    }

    private void seedFreemium() {
        upsert("FREEMIUM", "Freemium", BillingPlanTier.FREEMIUM, BillingInterval.MONTHLY,
                0L, defaultCurrency, 50, 5, false, 7, null);
    }

    private void seedBasic() {
        upsert("BASIC", "Basic", BillingPlanTier.BASIC, BillingInterval.MONTHLY,
                basicAmount, defaultCurrency, 1000, 50, true, 90,
                basicPaystackPlanCode.isBlank() ? null : basicPaystackPlanCode);
    }

    private void seedPremium() {
        upsert("PREMIUM", "Premium", BillingPlanTier.PREMIUM, BillingInterval.MONTHLY,
                premiumAmount, defaultCurrency, 100000, 10000, true, 3650,
                premiumPaystackPlanCode.isBlank() ? null : premiumPaystackPlanCode);
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
            String paystackPlanCode) {
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
        plan.setActive(true);
        subscriptionPlanRepository.save(plan);
        log.info("[BILLING] Ensured subscription plan {} ({})", code, name);
    }
}
