package com.assetiq.enums;

/**
 * Every way a budget's running totals can move, with the direction each one moves
 * {@code committedAmount} and {@code spentAmount} in (+1, -1 or 0).
 *
 * <p>Purchase orders and expenses follow the same shape: a commitment reserves
 * funds, then is either released (rejected, cancelled, deleted) or converted into
 * spend (approved expense, received order); spend already recorded can be reversed
 * when its source is deleted.
 */
public enum BudgetLedgerKind {
    /** Direct charge recorded outside the PO/expense workflows. */
    ADJUSTMENT(0, 1),

    PO_COMMIT(1, 0),
    PO_RELEASE(-1, 0),
    PO_SPEND(-1, 1),
    PO_SPEND_REVERSAL(0, -1),

    EXPENSE_COMMIT(1, 0),
    EXPENSE_RELEASE(-1, 0),
    EXPENSE_SPEND(-1, 1),
    EXPENSE_SPEND_REVERSAL(0, -1);

    private final int committedDirection;
    private final int spentDirection;

    BudgetLedgerKind(int committedDirection, int spentDirection) {
        this.committedDirection = committedDirection;
        this.spentDirection = spentDirection;
    }

    public int committedDirection() {
        return committedDirection;
    }

    public int spentDirection() {
        return spentDirection;
    }

    /** True for postings that reserve new funds, which require an open budget. */
    public boolean isCommitment() {
        return this == PO_COMMIT || this == EXPENSE_COMMIT;
    }
}
