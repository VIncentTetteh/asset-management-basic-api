package com.example.demo.models;

import com.example.demo.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Table(name = "billing_payment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_billing_payment_reference", columnNames = "reference")
}, indexes = {
        @Index(name = "idx_billing_payment_org_created", columnList = "organisation_id,created_at")
})
public class BillingPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    private OrganisationSubscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private SubscriptionPlan plan;

    @Column(nullable = false, length = 120)
    private String reference;

    @Column(nullable = false)
    private Long amountMinor;

    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    private Long paystackTransactionId;

    @Column(length = 120)
    private String paystackAuthorizationCode;

    @Column(length = 120)
    private String paystackCustomerCode;

    @Column(length = 120)
    private String paystackSubscriptionCode;

    @Column(length = 120)
    private String paystackEmailToken;

    private Instant paidAt;

    @Column(length = 80)
    private String channel;

    @Column(length = 500)
    private String gatewayResponse;

    @Column(columnDefinition = "TEXT")
    private String rawGatewayPayload;
}

