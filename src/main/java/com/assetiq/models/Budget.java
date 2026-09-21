package com.assetiq.models;

import com.assetiq.enums.BudgetStatus;
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

    /** Running total of SUBMITTED expenses not yet approved. Decremented on approve/reject. */
    @Column(name = "committed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal committedAmount = BigDecimal.ZERO;

    /** Fires a BUDGET_THRESHOLD notification when spent / total reaches this %. Default 80. */
    @Column(name = "alert_threshold_pct", nullable = false)
    private Integer alertThresholdPct = 80;

    /** Reason recorded alongside the last POST /budgets/{id}/adjustment call. */
    @Column(name = "last_adjustment_note", columnDefinition = "TEXT")
    private String lastAdjustmentNote;

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

    @Column(name = "fiscal_year")
    private Integer fiscalYear;

    /** Total less spend and open commitments: what can still be committed. */
    public BigDecimal availableAmount() {
        BigDecimal spent = spentAmount != null ? spentAmount : BigDecimal.ZERO;
        BigDecimal committed = committedAmount != null ? committedAmount : BigDecimal.ZERO;
        return totalAmount.subtract(spent).subtract(committed);
    }

    /**
     * Keeps EXCEEDED in step with the figures: an ACTIVE budget whose spend passes
     * its total becomes EXCEEDED, and an EXCEEDED budget brought back within its
     * total (a reversal, or a raised allocation) becomes ACTIVE again. DRAFT and
     * CLOSED are chosen by people and are never changed here.
     */
    public void reconcileExceededStatus() {
        if (totalAmount == null) return;
        BigDecimal spent = spentAmount != null ? spentAmount : BigDecimal.ZERO;
        boolean over = spent.compareTo(totalAmount) > 0;
        if (over && status == BudgetStatus.ACTIVE) {
            status = BudgetStatus.EXCEEDED;
        } else if (!over && status == BudgetStatus.EXCEEDED) {
            status = BudgetStatus.ACTIVE;
        }
    }
}
