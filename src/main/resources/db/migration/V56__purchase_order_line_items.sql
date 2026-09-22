-- V56: purchase order line items.
--
-- A purchase order carried a single total_amount and nothing else, so a buyer
-- could not itemise what was being bought: no description, no quantity, no unit
-- price, no tax. Receiving, budget reconciliation and supplier disputes all need
-- the lines, and every one of them was being kept outside the system.
--
-- Expand-only: one new table. Nothing existing is altered or backfilled. An order
-- with no lines keeps behaving exactly as it does today (the total stays the
-- lump sum the buyer typed); the total is only derived once lines exist.
--
-- Money columns are NUMERIC(19,4) so a unit price can carry four decimal places
-- (fractional-cent pricing is normal for consumables and per-seat licences).
-- line_total is stored rather than generated: it is the figure the supplier
-- agreed, and a later change to how tax is computed must not silently restate
-- historical orders.
--
-- Pre-check before deploying to staging/production — a new table cannot collide,
-- so there is nothing to clean up, but the unique index below is only safe on an
-- empty table. Confirm it is absent (0 rows means this migration creates it fresh):
--   SELECT to_regclass('public.po_line_item') AS existing_table;
-- and, if the table somehow already exists, the duplicate pre-check for its
-- unique index is:
--   SELECT purchase_order_id, line_number, count(*)
--     FROM po_line_item
--    WHERE deleted_at IS NULL
--    GROUP BY 1, 2 HAVING count(*) > 1;

CREATE TABLE IF NOT EXISTS po_line_item (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id      UUID NOT NULL REFERENCES organisation(id)   ON DELETE CASCADE,
    purchase_order_id    UUID NOT NULL REFERENCES purchase_order(id) ON DELETE CASCADE,
    line_number          INT NOT NULL,
    description          VARCHAR(500) NOT NULL,
    supplier_part_number VARCHAR(100),
    category_id          UUID REFERENCES category(id) ON DELETE SET NULL,
    quantity             NUMERIC(15,4) NOT NULL,
    unit_price           NUMERIC(19,4) NOT NULL,
    tax_rate             NUMERIC(9,4),
    tax_amount           NUMERIC(19,4),
    line_total           NUMERIC(19,4) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ,
    deleted_at           TIMESTAMPTZ,
    created_by           VARCHAR(255),
    modified_by          VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_po_line_item_order ON po_line_item(purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_po_line_item_org   ON po_line_item(organisation_id);

-- Line numbers are unique among live rows only, matching every other natural key
-- in this schema (V46): replacing an order's lines soft-deletes the old ones, and
-- the replacements reuse the same line numbers in the same transaction.
CREATE UNIQUE INDEX IF NOT EXISTS uq_po_line_item_order_line_live
    ON po_line_item (purchase_order_id, line_number)
    WHERE deleted_at IS NULL;
