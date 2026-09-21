-- Budget ledger: every movement of a budget's committed or spent total.
--
-- budget.spent_amount / committed_amount are running totals changed by manual
-- adjustments, purchase orders (commit on approval, spend on receipt, release on
-- cancel) and expenses (commit on submit, spend on approval, release on reject).
-- Until now nothing recorded those movements, so a budget's history could not be
-- shown or audited. Rows are written in the same transaction as the total they
-- explain, under a row lock on the budget.
CREATE TABLE IF NOT EXISTS budget_ledger_entry (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id  UUID          NOT NULL REFERENCES organisation(id) ON DELETE CASCADE,
    budget_id        UUID          NOT NULL REFERENCES budget(id) ON DELETE CASCADE,
    kind             VARCHAR(40)   NOT NULL,
    amount           NUMERIC(15,2) NOT NULL,
    currency         VARCHAR(10)   NOT NULL,
    spent_after      NUMERIC(15,2) NOT NULL,
    committed_after  NUMERIC(15,2) NOT NULL,
    source_type      VARCHAR(40)   NOT NULL,
    source_id        UUID,
    actor_email      VARCHAR(255),
    note             TEXT,
    idempotency_key  VARCHAR(120),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    modified_by      VARCHAR(255),
    CONSTRAINT ck_budget_ledger_amount_positive CHECK (amount > 0)
);

-- One workflow event (e.g. "commit PO x") can be applied at most once per tenant.
-- NULL keys (manual adjustments) are not constrained.
CREATE UNIQUE INDEX IF NOT EXISTS uk_budget_ledger_idempotency
    ON budget_ledger_entry (organisation_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_budget_ledger_budget_created
    ON budget_ledger_entry (budget_id, created_at);

CREATE INDEX IF NOT EXISTS idx_budget_ledger_source
    ON budget_ledger_entry (organisation_id, source_type, source_id);
