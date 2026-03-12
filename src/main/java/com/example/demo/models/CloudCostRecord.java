package com.example.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "cloud_cost_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cloud_asset_id", "billing_month", "service_name"}))
public class CloudCostRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cloud_asset_id", nullable = false)
    private CloudAsset cloudAsset;

    /** First day of the billing month (e.g. 2025-01-01) */
    @Column(name = "billing_month", nullable = false)
    private LocalDate billingMonth;

    @Column(name = "amount", nullable = false, precision = 15, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 10)
    private String currency = "USD";

    /** Sub-service name (e.g. "EC2 Compute", "S3 Storage") */
    @Column(name = "service_name", length = 200)
    private String serviceName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
}
