package com.example.demo.models;

import com.example.demo.enums.BillingInterval;
import com.example.demo.enums.BillingPlanTier;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Table(name = "subscription_plan", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subscription_plan_code", columnNames = "code")
})
public class SubscriptionPlan extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingPlanTier tier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingInterval interval;

    // Amount in minor units (kobo / cents)
    @Column(nullable = false)
    private Long amountMinor;

    @Column(nullable = false, length = 10)
    private String currency = "GHC";

    @Column(nullable = false)
    private Integer maxAssets;

    @Column(nullable = false)
    private Integer maxEmployees;

    @Column(nullable = false)
    private Boolean analyticsEnabled = false;

    @Column(nullable = false)
    private Integer auditRetentionDays = 7;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(length = 120)
    private String paystackPlanCode;
}

