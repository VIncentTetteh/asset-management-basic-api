package com.assetiq.models;

import com.assetiq.enums.BudgetLedgerKind;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One immutable movement of a budget's committed or spent total.
 *
 * <p>Written in the same transaction, and under the same row lock, as the change
 * to {@link Budget}, so the ledger and the running totals cannot disagree. The
 * {@code spentAfter}/{@code committedAfter} snapshots make the history readable
 * without replaying it. {@code idempotencyKey} (unique per organisation when set)
 * stops the same workflow event, e.g. one purchase order's commitment, from ever
 * being applied twice.
 */
@Entity
@Getter
@Setter
@Table(name = "budget_ledger_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_budget_ledger_idempotency",
                columnNames = { "organisation_id", "idempotency_key" })
})
public class BudgetLedgerEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BudgetLedgerKind kind;

    /** Always positive; {@link BudgetLedgerKind} carries the direction. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "spent_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal spentAfter;

    @Column(name = "committed_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal committedAfter;

    /** PURCHASE_ORDER, EXPENSE or ADJUSTMENT. */
    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;
}
