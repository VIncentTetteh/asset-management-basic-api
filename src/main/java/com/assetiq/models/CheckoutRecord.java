package com.assetiq.models;

import com.assetiq.enums.CheckoutStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;


@Entity
@Table(name = "checkout_records")
@Getter
@Setter
public class CheckoutRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_out_by_id", nullable = false)
    private User checkedOutBy;

    @Column(nullable = false)
    private Instant checkedOutAt;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Column(name = "actual_return_date")
    private LocalDate actualReturnDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_in_by_id")
    private User checkedInBy;

    @Column(name = "condition_on_checkout", length = 50)
    private String conditionOnCheckout;

    @Column(name = "condition_on_return", length = 50)
    private String conditionOnReturn;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CheckoutStatus status = CheckoutStatus.ACTIVE;
}
