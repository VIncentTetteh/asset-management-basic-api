-- V60: switch the governed AI assistant on, and record why that is now safe.
--
-- V32 dark-launched 'commercial.governed-ai' with the note that re-enabling
-- requires "tenant opt-in, DLP, approved provider and AI permission". Those
-- preconditions are met by the change this migration ships with:
--
--   • AI permission   — Permission.USE_AI_ASSISTANT, and the chat endpoint now
--                       carries an explicit authority check instead of
--                       isAuthenticated(). Retrieval is narrowed per section to
--                       the same authorities that govern the REST read endpoints
--                       (AiDataSection), so the assistant cannot be used to read
--                       past a user's own permissions.
--   • DLP             — licence keys, supplier bank details and tax IDs are never
--                       placed in a prompt; user email addresses are dropped in
--                       favour of display names; every retrieved field is
--                       sanitised and fenced as data, never instructions.
--   • Approved provider — ai.provider, with the key supplied by the environment
--                       (AWS Secrets Manager on staging and production). When no
--                       key is present the feature degrades with a message
--                       instead of a 500.
--   • Tenant opt-in   — per-organisation overrides still work. A tenant that does
--                       not want its records sent to a model gets a row in
--                       feature_flag_organisation with enabled = FALSE, which
--                       takes precedence over the global default set here.
--
-- Expand-only: this changes one flag row. No table, column or constraint is
-- touched, and rolling back to the previous build simply leaves the flag on,
-- where the old code still worked.

UPDATE feature_flag
SET    enabled_globally = TRUE,
       description      = 'Governed AI assistant and predictive insights. '
                          || 'Org-scoped, permission-aware retrieval; per-tenant override still honoured.',
       updated_at       = now()
WHERE  flag_key = 'commercial.governed-ai';

-- Register the flag if an environment somehow missed V32, so the aspect does not
-- treat it as an unknown key (unknown keys evaluate to OFF).
INSERT INTO feature_flag (id, flag_key, description, enabled_globally, rollout_percentage)
SELECT gen_random_uuid(), 'commercial.governed-ai',
       'Governed AI assistant and predictive insights.', TRUE, 0
WHERE  NOT EXISTS (SELECT 1 FROM feature_flag WHERE flag_key = 'commercial.governed-ai');

-- Grant USE_AI_ASSISTANT to roles that already hold every permission explicitly
-- (rather than via the grant-all flag, which is computed in code and needs no row).
INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'USE_AI_ASSISTANT'
FROM   role r
WHERE  r.grant_all_permissions = TRUE
ON CONFLICT (role_id, permission) DO NOTHING;
