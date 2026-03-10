package com.example.demo.models;

import com.example.demo.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Table(name = "organisation_subscription", indexes = {
        @Index(name = "idx_org_subscription_org_status", columnList = "organisation_id,status")
})
public class OrganisationSubscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(nullable = false)
    private Boolean autoRenew = true;

    @Column(nullable = false)
    private Instant currentPeriodStart;

    @Column(nullable = false)
    private Instant currentPeriodEnd;

    private Instant nextBillingAt;

    private Instant canceledAt;

    @Column(length = 120)
    private String paystackCustomerCode;

    @Column(length = 120)
    private String paystackSubscriptionCode;

    @Column(length = 120)
    private String paystackEmailToken;
}

