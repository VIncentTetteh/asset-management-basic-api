INSERT INTO feature_flag (id, flag_key, description, enabled_globally, rollout_percentage)
VALUES (
    gen_random_uuid(),
    'commercial.document-attachments',
    'Document attachments. Requires signature validation, quarantine, malware scanning and scan audit evidence.',
    FALSE,
    0
)
ON CONFLICT (flag_key) DO UPDATE
SET description = EXCLUDED.description,
    enabled_globally = FALSE,
    rollout_percentage = 0,
    updated_at = now();
