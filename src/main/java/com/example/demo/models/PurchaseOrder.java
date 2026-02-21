package com.example.demo.models;

import com.example.demo.enums.POStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "purchase_order")
public class PurchaseOrder extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String poNumber;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private POStatus status = POStatus.DRAFT;

    @ManyToOne
    private User approvedBy;

    private Instant approvedAt;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Organisation organisation;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Department department;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Supplier supplier;

}

