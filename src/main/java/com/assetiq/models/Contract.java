package com.assetiq.models;

import com.assetiq.enums.ContractStatus;
import com.assetiq.enums.ContractType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tracks vendor/supplier contracts — purchase agreements, SLAs, leases, insurance, etc.
 * Linked optionally to a Supplier and/or an Asset.
 */
@Entity
@Getter
@Setter
@Table(name = "contract")
public class Contract extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(unique = false, length = 100)
    private String contractNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ContractType contractType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractStatus status = ContractStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    private Supplier supplier;

    /** Optionally tied to a specific asset (e.g. maintenance or warranty contract). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Asset asset;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    /** Reminder: alert X days before expiry. */
    private Integer alertDaysBefore = 30;

    @Column(precision = 15, scale = 2)
    private BigDecimal value;

    private String currency = "USD";

    private boolean autoRenew = false;

    /** URL or path to the signed contract document. */
    @Column(length = 500)
    private String documentUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
}
