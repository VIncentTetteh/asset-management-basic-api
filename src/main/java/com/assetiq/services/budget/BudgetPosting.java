package com.assetiq.services.budget;

import com.assetiq.enums.BudgetLedgerKind;
import com.assetiq.models.Expense;
import com.assetiq.models.PurchaseOrder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One requested movement of a budget's totals.
 *
 * @param currency       currency of {@code amount}; must equal the budget's currency
 * @param requireFunds   reject the posting when it exceeds the budget's available amount
 * @param idempotencyKey unique per organisation; null for postings that may repeat
 */
public record BudgetPosting(
        BudgetLedgerKind kind,
        BigDecimal amount,
        String currency,
        String sourceType,
        UUID sourceId,
        String note,
        String idempotencyKey,
        boolean requireFunds) {

    public static final String SOURCE_PURCHASE_ORDER = "PURCHASE_ORDER";
    public static final String SOURCE_EXPENSE = "EXPENSE";
    public static final String SOURCE_ADJUSTMENT = "ADJUSTMENT";

    /** A purchase-order event. Commitments are funds-checked; the key is {@code KIND:poId}. */
    public static BudgetPosting forPurchaseOrder(BudgetLedgerKind kind, PurchaseOrder po) {
        return new BudgetPosting(kind, po.getTotalAmount(), po.getCurrency(), SOURCE_PURCHASE_ORDER,
                po.getId(), "PO " + po.getPoNumber(), keyFor(kind, po.getId()),
                kind == BudgetLedgerKind.PO_COMMIT);
    }

    /**
     * An expense event. The commitment made at submit is funds-checked, as a
     * purchase-order approval is: an expense larger than the budget's available
     * amount is refused (409) rather than silently pushing the budget over. Later
     * postings (spend, release, reversal) only move an amount already reserved.
     */
    public static BudgetPosting forExpense(BudgetLedgerKind kind, Expense expense) {
        return new BudgetPosting(kind, expense.getAmount(), expense.getCurrency(), SOURCE_EXPENSE,
                expense.getId(), expense.getTitle(), keyFor(kind, expense.getId()),
                kind == BudgetLedgerKind.EXPENSE_COMMIT);
    }

    /** A manual adjustment in the budget's own currency. */
    public static BudgetPosting adjustment(BigDecimal amount, String note) {
        return new BudgetPosting(BudgetLedgerKind.ADJUSTMENT, amount, null, SOURCE_ADJUSTMENT,
                null, note, null, false);
    }

    public static String keyFor(BudgetLedgerKind kind, UUID sourceId) {
        return kind.name() + ":" + sourceId;
    }
}
