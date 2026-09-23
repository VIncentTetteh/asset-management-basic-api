-- V66: turn on commercial.governed-custom-fields.
--
-- Supersedes the containment V38 applied. V38 is left exactly as it is: it has run
-- against staging and its checksum must not change, so this is a new migration that
-- flips the flag rather than an edit to the one that set it.
--
-- WHY V38 CONTAINED IT, AND WHY THAT REASONING NO LONGER DECIDES
--
-- V38's description promised "typed tenant field definitions with validation,
-- sensitivity, permissions, history, search and export governance" and set the flag
-- FALSE because that governance was never built. That is a feature-completeness gate,
-- not a safety precondition -- unlike V34, which contained document attachments until
-- malware scanning existed, where the missing piece was the thing that made the feature
-- dangerous.
--
-- The distinction matters here because of what the capability actually reaches. An
-- asset custom field is read by exactly one door:
--
--     GET /api/v1/assets/{assetId}/custom-fields
--
-- It is not a property of AssetDto, so it does not ride along with the asset anywhere.
-- Nothing in exports, reports, analytics, the AI assistant's retrieval or asset search
-- reads asset_custom_field. Enabling the flag therefore opens one read endpoint to the
-- roles that can already read the asset it hangs off, and nothing else.
--
-- WHAT IS BEING SWITCHED ON, HONESTLY
--
-- Of V38's six promises, what exists today is: storage, tenant scoping, a uniqueness
-- constraint per asset, and -- new in V65 -- a per-organisation definition record with
-- an inferred type, so a tenant can at least see which fields their imports created.
--
-- What does NOT exist, and is not built by this migration:
--
--   * Validation. A custom field value is an unconstrained TEXT column. The declared
--     data_type on custom_field_definition is a rendering hint that nothing enforces.
--   * Sensitivity classification. There is no way to mark a field as holding salaries
--     or national ID numbers, and therefore no way for anything downstream to treat it
--     differently. Today nothing downstream reads these values at all, which is what
--     makes that survivable; the day an export or the AI assistant learns to read them,
--     classification stops being optional.
--   * Per-field permissions. AssetIQ has no field-level permission model for any field,
--     custom or built-in: VIEW_ASSETS is what gates reading an asset, and purchase cost
--     is as visible as anything a customer imports. A custom field is not a new hole,
--     it is the existing granularity.
--   * Change history. ApiAuditInterceptor records that a request touched the endpoint,
--     with actor, path, status and IP. It does not record a field's before and after
--     values, so "who changed this value from X to Y" cannot be answered.
--   * Search. AssetSpecification's free-text predicate covers name, asset tag, serial
--     number, manufacturer and model only, and V18's trigram indexes cover the same
--     five. A value that was imported cannot be found by searching for it.
--   * Export governance. There is nothing to govern yet, because nothing exports them.
--     ReportGeneratorService writes fixed String[] header arrays; the tenant data export
--     in AccountLifecycleService lists thirteen asset keys by hand.
--
-- Two consequences of that last point that cut the other way, and are tracked with the
-- rest:
--
--   * The tenant data export omits custom fields entirely. If a customer imports
--     personal data into one, a subject access or portability request answered from that
--     export would be incomplete. That is a DPA gap created by the values existing, not
--     by this flag, and it argues for finishing the work rather than leaving the
--     capability off.
--   * field_value is not among the columns SensitiveDataEncryptionBackfill protects, so
--     these values sit in plain text at rest like most of the estate.
--
-- ON THE DECISION TO ENABLE
--
-- The capability was asked for directly: a customer migrating from another system has
-- columns AssetIQ has no field for, and the alternative to keeping them is discarding
-- their data at the door. Shipping the storage and withholding the switch does not make
-- the missing governance exist; it makes the import silently lossy. The governance items
-- above are a tracked follow-up, and the two that gate a customer demo -- sensitivity
-- classification and per-field permissions -- must land before anything downstream is
-- taught to read these values.

UPDATE feature_flag
SET description = 'Tenant-defined custom fields on assets. Storage, tenant scoping and '
                  || 'per-organisation field definitions exist. Validation, sensitivity '
                  || 'classification, per-field permissions, change history, search and '
                  || 'export governance do not. Values are readable only via '
                  || 'GET /api/v1/assets/{id}/custom-fields and are not carried into '
                  || 'exports, reports, AI retrieval or search. See V66.',
    enabled_globally = TRUE,
    rollout_percentage = 0,
    updated_at = now()
WHERE flag_key = 'commercial.governed-custom-fields';

-- Seed it if V38 never ran on this database, so a fresh environment is not left with
-- the import quietly dropping every unmapped column.
INSERT INTO feature_flag (id, flag_key, description, enabled_globally, rollout_percentage)
SELECT gen_random_uuid(),
       'commercial.governed-custom-fields',
       'Tenant-defined custom fields on assets. See V66 for what is and is not governed.',
       TRUE,
       0
WHERE NOT EXISTS (
    SELECT 1 FROM feature_flag WHERE flag_key = 'commercial.governed-custom-fields'
);

-- Per-organisation overrides win over enabled_globally, so a tenant that was explicitly
-- switched OFF stays off. Deliberate: this migration grants the capability by default
-- without overriding a decision somebody made about a specific customer.
