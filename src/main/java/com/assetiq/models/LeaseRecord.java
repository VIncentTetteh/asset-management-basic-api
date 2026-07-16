package com.assetiq.models;

import com.assetiq.enums.LeaseStatus;
//import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
//import java.util.UUID;

@Entity
@Table(name = "lease_records")
@Getter
@Setter
public class LeaseRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lessor_id", nullable = false)
    private Supplier lessor;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "monthly_payment", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyPayment;

    @Column(length = 3)
    private String currency = "GHS";

    @Column(name = "auto_renew")
    private Boolean autoRenew = false;

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays = 30;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LeaseStatus status;
}
