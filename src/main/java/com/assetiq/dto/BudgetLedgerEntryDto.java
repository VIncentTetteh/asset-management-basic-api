package com.assetiq.dto;

import com.assetiq.enums.BudgetLedgerKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of GET /budgets/{id}/ledger. {@code amount} is positive; {@code kind} gives the direction. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetLedgerEntryDto {
    private UUID id;
    private BudgetLedgerKind kind;
    private BigDecimal amount;
    private String currency;
    /** Signed change to spentAmount made by this entry. */
    private BigDecimal spentDelta;
    /** Signed change to committedAmount made by this entry. */
    private BigDecimal committedDelta;
    private BigDecimal spentAfter;
    private BigDecimal committedAfter;
    private String sourceType;
    private UUID sourceId;
    private String actorEmail;
    private String note;
    private Instant createdAt;
}
