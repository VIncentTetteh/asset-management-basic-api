INSERT INTO feature_flag (id, flag_key, description, enabled_globally, rollout_percentage)
VALUES (
    gen_random_uuid(),
    'commercial.governed-custom-fields',
    'Typed tenant field definitions with validation, sensitivity, permissions, history, search and export governance.',
    FALSE,
    0
)
ON CONFLICT (flag_key) DO UPDATE
SET description = EXCLUDED.description,
    enabled_globally = FALSE,
    rollout_percentage = 0,
    updated_at = now();
