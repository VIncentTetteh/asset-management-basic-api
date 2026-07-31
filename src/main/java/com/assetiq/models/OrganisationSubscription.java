package com.assetiq.models;

import com.assetiq.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
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

    /**
     * When this subscription first entered {@link SubscriptionStatus#PAST_DUE}.
     *
     * <p>The dunning clock. Set alongside the PAST_DUE transition, cleared when the
     * account recovers or is downgraded. Deliberately separate from {@code updatedAt},
     * which any unrelated write would bump — silently restarting the grace period.
     */
    private Instant pastDueSince;

    @Column(length = 120)
    private String paystackCustomerCode;

    @Column(length = 120)
    private String paystackSubscriptionCode;

    @Column(length = 120)
    private String paystackEmailToken;
}

