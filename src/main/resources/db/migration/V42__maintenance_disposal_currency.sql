-- Currency for maintenance costs and disposal sale values.
--
-- Both amounts were stored bare and treated as being in the related asset's
-- currency, while the web maintenance form already let users pick a currency that
-- was silently dropped. The columns are nullable and are not backfilled: a NULL
-- keeps the old meaning (the asset's currency), so existing figures are read
-- exactly as before. New records default to the asset's currency when the client
-- sends none.
ALTER TABLE maintenance_record ADD COLUMN IF NOT EXISTS currency VARCHAR(3);
ALTER TABLE disposal_record    ADD COLUMN IF NOT EXISTS currency VARCHAR(3);
