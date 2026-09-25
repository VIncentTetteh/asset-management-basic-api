-- Unsafe, incomplete, or obsolete commercial surfaces are dark by default.
-- Re-enabling requires a per-organisation override after the acceptance tests
-- and governance approvals recorded in COMMERCIAL_READINESS_AUDIT_2026-08-24.md.

INSERT INTO feature_flag (id, flag_key, description, enabled_globally, rollout_percentage)
VALUES
    (gen_random_uuid(), 'commercial.governed-ai',
     'Governed AI chat and predictive insights. Requires tenant opt-in, DLP, approved provider and AI permission.',
     FALSE, 0),
    (gen_random_uuid(), 'commercial.outbound-webhooks',
     'Tenant-scoped outbound event webhooks. Requires outbox dispatch and cross-tenant security tests.',
     FALSE, 0),
    (gen_random_uuid(), 'commercial.platform-health-dashboard',
     'Platform operator health dashboard. Never available to ordinary tenant administrators.',
     FALSE, 0),
    (gen_random_uuid(), 'compliance.bog-2026',
     'Versioned Bank of Ghana 2026 directive controls and reports. Requires approved regulatory mapping.',
     FALSE, 0)
ON CONFLICT (flag_key) DO UPDATE
SET description = EXCLUDED.description,
    enabled_globally = FALSE,
    rollout_percentage = 0,
    updated_at = now();
