package com.example.demo.models;

import com.example.demo.enums.BudgetStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tracks a procurement or IT budget for a department or the whole organisation.
 * Actual spend is derived by summing asset purchase costs and purchase orders
 * created within the budget period.
 */
@Entity
@Getter
@Setter
@Table(name = "budget")
public class Budget extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Optional: scope budget to a specific department. Null = org-wide. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Department department;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    /** Running total of approved spend tracked against this budget. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal spentAmount = BigDecimal.ZERO;

    private String currency = "USD";

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BudgetStatus status = BudgetStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
}
