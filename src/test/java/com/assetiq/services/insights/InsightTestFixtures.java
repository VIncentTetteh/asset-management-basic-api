package com.assetiq.services.insights;

import com.assetiq.enums.AssetCondition;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.DepreciationMethod;
import com.assetiq.models.Organisation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Builders for the slim rows the insight services read. */
final class InsightTestFixtures {

    private InsightTestFixtures() {
    }

    static Organisation org(String baseCurrency) {
        Organisation org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setBillingCurrency(baseCurrency);
        return org;
    }

    static Builder asset(String name, String currency, String cost) {
        return new Builder(name, currency, cost);
    }

    static final class Builder {
        private final UUID id = UUID.randomUUID();
        private final String name;
        private final String currency;
        private final BigDecimal cost;
        private BigDecimal residual = BigDecimal.ZERO;
        private Integer life;
        private DepreciationMethod method = DepreciationMethod.STRAIGHT_LINE;
        private LocalDate purchaseDate;
        private LocalDate warrantyExpiry;
        private LocalDate insuranceExpiry;
        private AssetStatus status = AssetStatus.IN_USE;
        private AssetCondition condition = AssetCondition.GOOD;
        private UUID assignedUserId = UUID.randomUUID();
        private UUID departmentId;
        private String departmentName;
        private UUID locationId;
        private String locationName;
        private UUID categoryId;
        private String categoryName;
        private Integer policyLife;
        private DepreciationMethod policyMethod;
        private BigDecimal policySalvagePercent;
        private Instant updatedAt = Instant.now();
        private Instant lastScannedAt;

        private Builder(String name, String currency, String cost) {
            this.name = name;
            this.currency = currency;
            this.cost = cost == null ? null : new BigDecimal(cost);
        }

        Builder depreciatedOver(int months, LocalDate purchased) {
            this.life = months;
            this.purchaseDate = purchased;
            return this;
        }

        Builder residual(String value) {
            this.residual = new BigDecimal(value);
            return this;
        }

        Builder method(DepreciationMethod m) {
            this.method = m;
            return this;
        }

        Builder categoryPolicy(Integer life, DepreciationMethod method, String salvagePercent) {
            this.method = null;
            this.residual = null;
            this.life = null;
            this.policyLife = life;
            this.policyMethod = method;
            this.policySalvagePercent = salvagePercent == null ? null : new BigDecimal(salvagePercent);
            return this;
        }

        Builder status(AssetStatus s) {
            this.status = s;
            return this;
        }

        Builder condition(AssetCondition c) {
            this.condition = c;
            return this;
        }

        Builder unassigned() {
            this.assignedUserId = null;
            return this;
        }

        Builder department(UUID id, String name) {
            this.departmentId = id;
            this.departmentName = name;
            return this;
        }

        Builder location(UUID id, String name) {
            this.locationId = id;
            this.locationName = name;
            return this;
        }

        Builder category(UUID id, String name) {
            this.categoryId = id;
            this.categoryName = name;
            return this;
        }

        Builder warrantyExpiry(LocalDate date) {
            this.warrantyExpiry = date;
            return this;
        }

        Builder insuranceExpiry(LocalDate date) {
            this.insuranceExpiry = date;
            return this;
        }

        Builder touchedDaysAgo(long days) {
            this.updatedAt = Instant.now().minusSeconds(days * 86_400L);
            return this;
        }

        Builder scannedDaysAgo(long days) {
            this.lastScannedAt = Instant.now().minusSeconds(days * 86_400L);
            return this;
        }

        Builder neverTouched() {
            this.updatedAt = null;
            this.lastScannedAt = null;
            return this;
        }

        AssetValuationRow build() {
            return new AssetValuationRow(id, name, "TAG-" + name, currency, cost, residual, life, method,
                    purchaseDate, warrantyExpiry, insuranceExpiry, status, condition, assignedUserId,
                    departmentId, departmentName, locationId, locationName, categoryId, categoryName,
                    policyLife, policyMethod, policySalvagePercent, updatedAt, lastScannedAt);
        }
    }
}
