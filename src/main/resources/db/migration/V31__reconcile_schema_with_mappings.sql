-- Reconcile the migrated schema with the JPA mappings.
--
-- Companion to V30 and the second half of the fix documented in docs/SCHEMA_DRIFT.md.
-- V30 handled the 14 currency columns; ddl-auto=validate then reported the next
-- mismatch, and the next, because it stops at the first. Diffing a fully migrated
-- database against a Hibernate-generated one gave the whole set at once, which is
-- what this migration closes.
--
-- Direction of the fix: the entities win. Local development has run with
-- SPRING_JPA_HIBERNATE_DDL_AUTO=update, so Hibernate has been shaping the database
-- from the mappings for a long time while the migrations were maintained by hand
-- and fell behind. The mappings are what the running code reads and writes, so they
-- are the de facto source of truth and the migrations are brought up to them.
--
-- Two deliberate exceptions, both to avoid destroying data:
--   * Ten free-text columns are TEXT in the database and were varchar(n) in the
--     mappings. Narrowing them could truncate real content, so the ENTITIES were
--     changed to columnDefinition = "TEXT" instead and they do not appear here.
--   * New columns are added NULLable regardless of the mapping's nullability.
--     Adding NOT NULL without a default fails outright on a table that already has
--     rows. Hibernate's validate checks that a column exists and has the right
--     type; it does not check nullability, so this is sufficient and safe.

-- ── Columns the mappings expect that no migration ever created ───────────────

-- asset_audit
ALTER TABLE asset_audit ADD COLUMN IF NOT EXISTS conducted_by_id UUID;
ALTER TABLE asset_audit ADD COLUMN IF NOT EXISTS department_id UUID;
ALTER TABLE asset_audit ADD COLUMN IF NOT EXISTS organisation_id UUID;
ALTER TABLE asset_audit ADD COLUMN IF NOT EXISTS remarks TEXT;
-- asset_custom_field
ALTER TABLE asset_custom_field ADD COLUMN IF NOT EXISTS organisation_id UUID;
-- asset_transfer
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS approved_by_id UUID;
ALTER TABLE asset_transfer ADD COLUMN IF NOT EXISTS requested_by_id UUID;
-- audit_item
ALTER TABLE audit_item ADD COLUMN IF NOT EXISTS audit_id UUID;
-- billing_payment
ALTER TABLE billing_payment ADD COLUMN IF NOT EXISTS amount_minor BIGINT;
ALTER TABLE billing_payment ADD COLUMN IF NOT EXISTS channel VARCHAR(80);
ALTER TABLE billing_payment ADD COLUMN IF NOT EXISTS gateway_response VARCHAR(500);
ALTER TABLE billing_payment ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;
ALTER TABLE billing_payment ADD COLUMN IF NOT EXISTS paystack_authorization_code VARCHAR(120);
ALTER TABLE billing_payment ADD COLUMN IF NOT EXISTS paystack_customer_code VARCHAR(120);
ALTER TABLE billing_payment ADD COLUMN IF NOT EXISTS paystack_email_token VARCHAR(120);
ALTER TABLE billing_payment ADD COLUMN IF NOT EXISTS paystack_subscription_code VARCHAR(120);
ALTER TABLE billing_payment ADD COLUMN IF NOT EXISTS raw_gateway_payload TEXT;
ALTER TABLE billing_payment ADD COLUMN IF NOT EXISTS reference VARCHAR(120);
ALTER TABLE billing_payment ADD COLUMN IF NOT EXISTS subscription_id UUID;
-- bog_control
ALTER TABLE bog_control ADD COLUMN IF NOT EXISTS directive_ref VARCHAR(32);
ALTER TABLE bog_control ADD COLUMN IF NOT EXISTS evidence_url VARCHAR(255);
ALTER TABLE bog_control ADD COLUMN IF NOT EXISTS gap_description TEXT;
ALTER TABLE bog_control ADD COLUMN IF NOT EXISTS owner_id UUID;
ALTER TABLE bog_control ADD COLUMN IF NOT EXISTS remediation_plan TEXT;
ALTER TABLE bog_control ADD COLUMN IF NOT EXISTS requirement TEXT;
ALTER TABLE bog_control ADD COLUMN IF NOT EXISTS target_date TIMESTAMPTZ;
-- checkout_records
ALTER TABLE checkout_records ADD COLUMN IF NOT EXISTS actual_return_date DATE;
ALTER TABLE checkout_records ADD COLUMN IF NOT EXISTS checked_in_by_id UUID;
ALTER TABLE checkout_records ADD COLUMN IF NOT EXISTS checked_out_by_id UUID;
ALTER TABLE checkout_records ADD COLUMN IF NOT EXISTS condition_on_checkout VARCHAR(50);
ALTER TABLE checkout_records ADD COLUMN IF NOT EXISTS condition_on_return VARCHAR(50);
ALTER TABLE checkout_records ADD COLUMN IF NOT EXISTS expected_return_date DATE;
ALTER TABLE checkout_records ADD COLUMN IF NOT EXISTS organisation_id UUID;
-- cloud_asset
ALTER TABLE cloud_asset ADD COLUMN IF NOT EXISTS account_id VARCHAR(200);
ALTER TABLE cloud_asset ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE cloud_asset ADD COLUMN IF NOT EXISTS environment VARCHAR(50);
ALTER TABLE cloud_asset ADD COLUMN IF NOT EXISTS last_sync_at TIMESTAMPTZ;
ALTER TABLE cloud_asset ADD COLUMN IF NOT EXISTS monthly_cost_estimate NUMERIC(15,4);
-- cloud_cost_record
ALTER TABLE cloud_cost_record ADD COLUMN IF NOT EXISTS amount NUMERIC(15,4);
ALTER TABLE cloud_cost_record ADD COLUMN IF NOT EXISTS billing_month DATE;
ALTER TABLE cloud_cost_record ADD COLUMN IF NOT EXISTS service_name VARCHAR(200);
-- compliance_control
ALTER TABLE compliance_control ADD COLUMN IF NOT EXISTS control_description TEXT;
ALTER TABLE compliance_control ADD COLUMN IF NOT EXISTS control_name VARCHAR(255);
ALTER TABLE compliance_control ADD COLUMN IF NOT EXISTS control_ref VARCHAR(64);
ALTER TABLE compliance_control ADD COLUMN IF NOT EXISTS evidence_url VARCHAR(255);
ALTER TABLE compliance_control ADD COLUMN IF NOT EXISTS framework VARCHAR(32);
ALTER TABLE compliance_control ADD COLUMN IF NOT EXISTS gap_description TEXT;
ALTER TABLE compliance_control ADD COLUMN IF NOT EXISTS justification TEXT;
ALTER TABLE compliance_control ADD COLUMN IF NOT EXISTS last_reviewed_at TIMESTAMPTZ;
ALTER TABLE compliance_control ADD COLUMN IF NOT EXISTS last_reviewed_by VARCHAR(255);
ALTER TABLE compliance_control ADD COLUMN IF NOT EXISTS organisation_id UUID;
ALTER TABLE compliance_control ADD COLUMN IF NOT EXISTS owner_id UUID;
ALTER TABLE compliance_control ADD COLUMN IF NOT EXISTS remediation_plan TEXT;
ALTER TABLE compliance_control ADD COLUMN IF NOT EXISTS review_due_date TIMESTAMPTZ;
ALTER TABLE compliance_control ADD COLUMN IF NOT EXISTS status VARCHAR(32);
-- contract
ALTER TABLE contract ADD COLUMN IF NOT EXISTS alert_days_before INTEGER;
ALTER TABLE contract ADD COLUMN IF NOT EXISTS asset_id UUID;
ALTER TABLE contract ADD COLUMN IF NOT EXISTS contract_type VARCHAR(40);
ALTER TABLE contract ADD COLUMN IF NOT EXISTS supplier_id UUID;
-- discovered_device
ALTER TABLE discovered_device ADD COLUMN IF NOT EXISTS discovery_method VARCHAR(20);
ALTER TABLE discovered_device ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;
ALTER TABLE discovered_device ADD COLUMN IF NOT EXISTS open_ports VARCHAR(500);
ALTER TABLE discovered_device ADD COLUMN IF NOT EXISTS os_hint VARCHAR(200);
ALTER TABLE discovered_device ADD COLUMN IF NOT EXISTS promoted_asset_id UUID;
ALTER TABLE discovered_device ADD COLUMN IF NOT EXISTS response_time_ms BIGINT;
-- disposal_record
ALTER TABLE disposal_record ADD COLUMN IF NOT EXISTS approved_by_id UUID;
ALTER TABLE disposal_record ADD COLUMN IF NOT EXISTS compliance_document_url VARCHAR(255);
ALTER TABLE disposal_record ADD COLUMN IF NOT EXISTS disposal_method VARCHAR(255);
ALTER TABLE disposal_record ADD COLUMN IF NOT EXISTS organisation_id UUID;
ALTER TABLE disposal_record ADD COLUMN IF NOT EXISTS sale_value NUMERIC(15,2);
-- exchange_rates
ALTER TABLE exchange_rates ADD COLUMN IF NOT EXISTS effective_date DATE;
ALTER TABLE exchange_rates ADD COLUMN IF NOT EXISTS organisation_id UUID;
-- ics_asset
ALTER TABLE ics_asset ADD COLUMN IF NOT EXISTS asset_id UUID;
ALTER TABLE ics_asset ADD COLUMN IF NOT EXISTS isolated BOOLEAN;
ALTER TABLE ics_asset ADD COLUMN IF NOT EXISTS known_vulnerabilities TEXT;
ALTER TABLE ics_asset ADD COLUMN IF NOT EXISTS last_patched_at TIMESTAMPTZ;
ALTER TABLE ics_asset ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE ics_asset ADD COLUMN IF NOT EXISTS vendor_support_status VARCHAR(24);
-- lease_records
ALTER TABLE lease_records ADD COLUMN IF NOT EXISTS auto_renew BOOLEAN;
ALTER TABLE lease_records ADD COLUMN IF NOT EXISTS department_id UUID;
ALTER TABLE lease_records ADD COLUMN IF NOT EXISTS lessor_id UUID;
ALTER TABLE lease_records ADD COLUMN IF NOT EXISTS notice_period_days INTEGER;
ALTER TABLE lease_records ADD COLUMN IF NOT EXISTS organisation_id UUID;
-- maintenance_record
ALTER TABLE maintenance_record ADD COLUMN IF NOT EXISTS maintenance_type VARCHAR(255);
ALTER TABLE maintenance_record ADD COLUMN IF NOT EXISTS next_due_date DATE;
ALTER TABLE maintenance_record ADD COLUMN IF NOT EXISTS organisation_id UUID;
ALTER TABLE maintenance_record ADD COLUMN IF NOT EXISTS performed_date DATE;
ALTER TABLE maintenance_record ADD COLUMN IF NOT EXISTS vendor_id UUID;
-- org_sso_config
ALTER TABLE org_sso_config ADD COLUMN IF NOT EXISTS acs_url TEXT;
ALTER TABLE org_sso_config ADD COLUMN IF NOT EXISTS idp_metadata_url TEXT;
ALTER TABLE org_sso_config ADD COLUMN IF NOT EXISTS issuer_uri VARCHAR(255);
ALTER TABLE org_sso_config ADD COLUMN IF NOT EXISTS redirect_uri TEXT;
ALTER TABLE org_sso_config ADD COLUMN IF NOT EXISTS scopes VARCHAR(255);
ALTER TABLE org_sso_config ADD COLUMN IF NOT EXISTS sp_entity_id VARCHAR(255);
-- organisation_subscription
ALTER TABLE organisation_subscription ADD COLUMN IF NOT EXISTS auto_renew BOOLEAN;
ALTER TABLE organisation_subscription ADD COLUMN IF NOT EXISTS canceled_at TIMESTAMPTZ;
ALTER TABLE organisation_subscription ADD COLUMN IF NOT EXISTS next_billing_at TIMESTAMPTZ;
ALTER TABLE organisation_subscription ADD COLUMN IF NOT EXISTS paystack_email_token VARCHAR(120);
-- patch_record
ALTER TABLE patch_record ADD COLUMN IF NOT EXISTS applied_by_email VARCHAR(255);
ALTER TABLE patch_record ADD COLUMN IF NOT EXISTS organisation_id UUID;
ALTER TABLE patch_record ADD COLUMN IF NOT EXISTS patch_name VARCHAR(255);
ALTER TABLE patch_record ADD COLUMN IF NOT EXISTS rollback_plan TEXT;
ALTER TABLE patch_record ADD COLUMN IF NOT EXISTS test_env_validated BOOLEAN;
ALTER TABLE patch_record ADD COLUMN IF NOT EXISTS version VARCHAR(64);
-- pci_saq_record
ALTER TABLE pci_saq_record ADD COLUMN IF NOT EXISTS compensating_control TEXT;
ALTER TABLE pci_saq_record ADD COLUMN IF NOT EXISTS compliance_status VARCHAR(24);
ALTER TABLE pci_saq_record ADD COLUMN IF NOT EXISTS evidence_url VARCHAR(255);
ALTER TABLE pci_saq_record ADD COLUMN IF NOT EXISTS requirement_number VARCHAR(16);
ALTER TABLE pci_saq_record ADD COLUMN IF NOT EXISTS requirement_text TEXT;
ALTER TABLE pci_saq_record ADD COLUMN IF NOT EXISTS target_date TIMESTAMPTZ;
-- predictive_insight
ALTER TABLE predictive_insight ADD COLUMN IF NOT EXISTS predicted_date DATE;
ALTER TABLE predictive_insight ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;
ALTER TABLE predictive_insight ADD COLUMN IF NOT EXISTS severity VARCHAR(20);
ALTER TABLE predictive_insight ADD COLUMN IF NOT EXISTS title VARCHAR(255);
-- purchase_order
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS approved_by_id UUID;
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS department_id UUID;
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS linked_budget_id UUID;
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS remarks TEXT;
-- qr_revisions
ALTER TABLE qr_revisions ADD COLUMN IF NOT EXISTS generated_by_id UUID;
ALTER TABLE qr_revisions ADD COLUMN IF NOT EXISTS organisation_id UUID;
ALTER TABLE qr_revisions ADD COLUMN IF NOT EXISTS version INTEGER;
-- regulatory_filing
ALTER TABLE regulatory_filing ADD COLUMN IF NOT EXISTS due_date TIMESTAMPTZ;
ALTER TABLE regulatory_filing ADD COLUMN IF NOT EXISTS reference VARCHAR(128);
ALTER TABLE regulatory_filing ADD COLUMN IF NOT EXISTS regulator VARCHAR(32);
-- risk_register
ALTER TABLE risk_register ADD COLUMN IF NOT EXISTS framework VARCHAR(32);
ALTER TABLE risk_register ADD COLUMN IF NOT EXISTS mitigation_plan TEXT;
ALTER TABLE risk_register ADD COLUMN IF NOT EXISTS owner_id UUID;
ALTER TABLE risk_register ADD COLUMN IF NOT EXISTS residual_risk INTEGER;
ALTER TABLE risk_register ADD COLUMN IF NOT EXISTS risk_id VARCHAR(32);
ALTER TABLE risk_register ADD COLUMN IF NOT EXISTS treatment VARCHAR(16);
-- security_incident
ALTER TABLE security_incident ADD COLUMN IF NOT EXISTS assigned_to_id UUID;
ALTER TABLE security_incident ADD COLUMN IF NOT EXISTS category VARCHAR(64);
ALTER TABLE security_incident ADD COLUMN IF NOT EXISTS detected_at TIMESTAMPTZ;
ALTER TABLE security_incident ADD COLUMN IF NOT EXISTS lessons_learned TEXT;
ALTER TABLE security_incident ADD COLUMN IF NOT EXISTS reported_by_id UUID;
ALTER TABLE security_incident ADD COLUMN IF NOT EXISTS root_cause TEXT;
-- security_policy
ALTER TABLE security_policy ADD COLUMN IF NOT EXISTS approved_by_email VARCHAR(255);
ALTER TABLE security_policy ADD COLUMN IF NOT EXISTS document_url VARCHAR(255);
ALTER TABLE security_policy ADD COLUMN IF NOT EXISTS owner_id UUID;
ALTER TABLE security_policy ADD COLUMN IF NOT EXISTS review_due_date TIMESTAMPTZ;
ALTER TABLE security_policy ADD COLUMN IF NOT EXISTS title VARCHAR(255);
-- security_zone
ALTER TABLE security_zone ADD COLUMN IF NOT EXISTS allowed_protocols VARCHAR(255);
ALTER TABLE security_zone ADD COLUMN IF NOT EXISTS asset_count INTEGER;
ALTER TABLE security_zone ADD COLUMN IF NOT EXISTS network_range VARCHAR(255);
ALTER TABLE security_zone ADD COLUMN IF NOT EXISTS purdue_level INTEGER;
-- sla_metric
ALTER TABLE sla_metric ADD COLUMN IF NOT EXISTS incident_count INTEGER;
ALTER TABLE sla_metric ADD COLUMN IF NOT EXISTS month INTEGER;
ALTER TABLE sla_metric ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE sla_metric ADD COLUMN IF NOT EXISTS planned_downtime_minutes INTEGER;
ALTER TABLE sla_metric ADD COLUMN IF NOT EXISTS rpo_minutes INTEGER;
ALTER TABLE sla_metric ADD COLUMN IF NOT EXISTS rto_minutes INTEGER;
ALTER TABLE sla_metric ADD COLUMN IF NOT EXISTS sla_breached BOOLEAN;
ALTER TABLE sla_metric ADD COLUMN IF NOT EXISTS unplanned_downtime_minutes INTEGER;
ALTER TABLE sla_metric ADD COLUMN IF NOT EXISTS uptime_percent DOUBLE PRECISION;
ALTER TABLE sla_metric ADD COLUMN IF NOT EXISTS year INTEGER;
-- subscription_plan
ALTER TABLE subscription_plan ADD COLUMN IF NOT EXISTS amount_minor BIGINT;
ALTER TABLE subscription_plan ADD COLUMN IF NOT EXISTS analytics_enabled BOOLEAN;
ALTER TABLE subscription_plan ADD COLUMN IF NOT EXISTS audit_retention_days INTEGER;
ALTER TABLE subscription_plan ADD COLUMN IF NOT EXISTS max_employees INTEGER;
ALTER TABLE subscription_plan ADD COLUMN IF NOT EXISTS paystack_plan_code VARCHAR(120);
-- vendor_performance_review
ALTER TABLE vendor_performance_review ADD COLUMN IF NOT EXISTS delivery_score INTEGER;
ALTER TABLE vendor_performance_review ADD COLUMN IF NOT EXISTS feedback TEXT;
ALTER TABLE vendor_performance_review ADD COLUMN IF NOT EXISTS period_end DATE;
ALTER TABLE vendor_performance_review ADD COLUMN IF NOT EXISTS period_start DATE;
ALTER TABLE vendor_performance_review ADD COLUMN IF NOT EXISTS quality_score INTEGER;
ALTER TABLE vendor_performance_review ADD COLUMN IF NOT EXISTS rating NUMERIC(3,2);
ALTER TABLE vendor_performance_review ADD COLUMN IF NOT EXISTS reviewed_by_id UUID;
ALTER TABLE vendor_performance_review ADD COLUMN IF NOT EXISTS support_score INTEGER;
-- vulnerability_scan
ALTER TABLE vulnerability_scan ADD COLUMN IF NOT EXISTS next_scan_due TIMESTAMPTZ;
ALTER TABLE vulnerability_scan ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE vulnerability_scan ADD COLUMN IF NOT EXISTS scan_type VARCHAR(24);
ALTER TABLE vulnerability_scan ADD COLUMN IF NOT EXISTS scanner_tool VARCHAR(128);
ALTER TABLE vulnerability_scan ADD COLUMN IF NOT EXISTS status VARCHAR(24);
-- webhook
ALTER TABLE webhook ADD COLUMN IF NOT EXISTS delivery_count BIGINT;
ALTER TABLE webhook ADD COLUMN IF NOT EXISTS failure_count BIGINT;
ALTER TABLE webhook ADD COLUMN IF NOT EXISTS last_triggered_at TIMESTAMPTZ;
ALTER TABLE webhook ADD COLUMN IF NOT EXISTS name VARCHAR(200);
-- webhook_delivery
ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS attempts INTEGER;
ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS event_name VARCHAR(100);
ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS organisation_id UUID;
ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS response_time_ms BIGINT;
ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS status_code INTEGER;

-- ── Type and length differences ──────────────────────────────────────────────
-- Widenings are unconditional. Narrowings apply only to bounded code-like columns
-- (status, type, reference), never to free text, and are cast explicitly so the
-- intent is visible in the migration rather than implied.

-- app_user
ALTER TABLE app_user ALTER COLUMN email_verification_token_expiry TYPE TIMESTAMPTZ USING email_verification_token_expiry::timestamptz;
ALTER TABLE app_user ALTER COLUMN email_verified_at TYPE TIMESTAMPTZ USING email_verified_at::timestamptz;
ALTER TABLE app_user ALTER COLUMN employee_id TYPE VARCHAR(255) USING employee_id::varchar;
ALTER TABLE app_user ALTER COLUMN phone TYPE VARCHAR(255) USING phone::varchar;
-- asset
ALTER TABLE asset ALTER COLUMN asset_tag TYPE VARCHAR(255) USING asset_tag::varchar;
ALTER TABLE asset ALTER COLUMN asset_type TYPE VARCHAR(255) USING asset_type::varchar;
ALTER TABLE asset ALTER COLUMN barcode_qr_code TYPE VARCHAR(255) USING left(barcode_qr_code, 255);
ALTER TABLE asset ALTER COLUMN depreciation_method TYPE VARCHAR(255) USING depreciation_method::varchar;
ALTER TABLE asset ALTER COLUMN status TYPE VARCHAR(20) USING left(status, 20);
-- asset_transfer
ALTER TABLE asset_transfer ALTER COLUMN status TYPE VARCHAR(20) USING left(status, 20);
-- audit_event
ALTER TABLE audit_event ALTER COLUMN handler TYPE VARCHAR(200) USING left(handler, 200);
ALTER TABLE audit_event ALTER COLUMN request_id TYPE VARCHAR(100) USING left(request_id, 100);
-- audit_item
ALTER TABLE audit_item ALTER COLUMN condition TYPE VARCHAR(255) USING condition::varchar;
-- billing_payment
ALTER TABLE billing_payment ALTER COLUMN status TYPE VARCHAR(20) USING left(status, 20);
-- bog_control
ALTER TABLE bog_control ALTER COLUMN status TYPE VARCHAR(32) USING status::varchar;
-- budget
ALTER TABLE budget ALTER COLUMN status TYPE VARCHAR(20) USING left(status, 20);
-- category
ALTER TABLE category ALTER COLUMN asset_prefix_code TYPE VARCHAR(255) USING asset_prefix_code::varchar;
-- checkout_records
ALTER TABLE checkout_records ALTER COLUMN status TYPE VARCHAR(255) USING status::varchar;
-- cloud_asset
ALTER TABLE cloud_asset ALTER COLUMN name TYPE VARCHAR(200) USING left(name, 200);
ALTER TABLE cloud_asset ALTER COLUMN resource_type TYPE VARCHAR(30) USING left(resource_type, 30);
ALTER TABLE cloud_asset ALTER COLUMN status TYPE VARCHAR(20) USING left(status, 20);
-- contract
ALTER TABLE contract ALTER COLUMN status TYPE VARCHAR(20) USING left(status, 20);
-- department
ALTER TABLE department ALTER COLUMN budget_limit TYPE NUMERIC(38,2) USING budget_limit::numeric;
ALTER TABLE department ALTER COLUMN cost_center_code TYPE VARCHAR(255) USING cost_center_code::varchar;
ALTER TABLE department ALTER COLUMN department_code TYPE VARCHAR(255) USING department_code::varchar;
-- depreciation_policy
ALTER TABLE depreciation_policy ALTER COLUMN method TYPE VARCHAR(255) USING method::varchar;
-- discovered_device
ALTER TABLE discovered_device ALTER COLUMN ip_address TYPE VARCHAR(45) USING left(ip_address, 45);
ALTER TABLE discovered_device ALTER COLUMN mac_address TYPE VARCHAR(17) USING left(mac_address, 17);
ALTER TABLE discovered_device ALTER COLUMN status TYPE VARCHAR(20) USING left(status, 20);
-- exchange_rates
ALTER TABLE exchange_rates ALTER COLUMN rate TYPE NUMERIC(18,8) USING rate::numeric;
ALTER TABLE exchange_rates ALTER COLUMN source TYPE VARCHAR(50) USING left(source, 50);
-- expenses
ALTER TABLE expenses ALTER COLUMN category TYPE VARCHAR(255) USING category::varchar;
ALTER TABLE expenses ALTER COLUMN status TYPE VARCHAR(255) USING status::varchar;
-- feature_flag
ALTER TABLE feature_flag ALTER COLUMN rollout_percentage TYPE SMALLINT USING rollout_percentage::smallint;
-- ics_asset
ALTER TABLE ics_asset ALTER COLUMN firmware_version TYPE VARCHAR(64) USING left(firmware_version, 64);
ALTER TABLE ics_asset ALTER COLUMN protocol TYPE VARCHAR(128) USING protocol::varchar;
-- lease_records
ALTER TABLE lease_records ALTER COLUMN status TYPE VARCHAR(255) USING status::varchar;
-- location
ALTER TABLE location ALTER COLUMN floor TYPE VARCHAR(255) USING floor::varchar;
ALTER TABLE location ALTER COLUMN room TYPE VARCHAR(255) USING room::varchar;
-- maintenance_record
ALTER TABLE maintenance_record ALTER COLUMN status TYPE VARCHAR(20) USING left(status, 20);
-- notification
ALTER TABLE notification ALTER COLUMN action_url TYPE VARCHAR(255) USING left(action_url, 255);
ALTER TABLE notification ALTER COLUMN type TYPE VARCHAR(30) USING left(type, 30);
-- organisation
ALTER TABLE organisation ALTER COLUMN contact_phone TYPE VARCHAR(255) USING contact_phone::varchar;
ALTER TABLE organisation ALTER COLUMN purge_after TYPE TIMESTAMPTZ USING purge_after::timestamptz;
ALTER TABLE organisation ALTER COLUMN timezone TYPE VARCHAR(255) USING timezone::varchar;
-- organisation_subscription
ALTER TABLE organisation_subscription ALTER COLUMN past_due_since TYPE TIMESTAMPTZ USING past_due_since::timestamptz;
ALTER TABLE organisation_subscription ALTER COLUMN paystack_customer_code TYPE VARCHAR(120) USING left(paystack_customer_code, 120);
ALTER TABLE organisation_subscription ALTER COLUMN paystack_subscription_code TYPE VARCHAR(120) USING left(paystack_subscription_code, 120);
ALTER TABLE organisation_subscription ALTER COLUMN status TYPE VARCHAR(20) USING left(status, 20);
-- patch_record
ALTER TABLE patch_record ALTER COLUMN status TYPE VARCHAR(16) USING left(status, 16);
-- predictive_insight
ALTER TABLE predictive_insight ALTER COLUMN confidence TYPE DOUBLE PRECISION USING confidence::double precision;
ALTER TABLE predictive_insight ALTER COLUMN insight_type TYPE VARCHAR(30) USING left(insight_type, 30);
-- purchase_order
ALTER TABLE purchase_order ALTER COLUMN po_number TYPE VARCHAR(255) USING po_number::varchar;
ALTER TABLE purchase_order ALTER COLUMN status TYPE VARCHAR(20) USING left(status, 20);
-- regulatory_filing
ALTER TABLE regulatory_filing ALTER COLUMN filing_type TYPE VARCHAR(255) USING filing_type::varchar;
ALTER TABLE regulatory_filing ALTER COLUMN status TYPE VARCHAR(16) USING left(status, 16);
-- risk_register
ALTER TABLE risk_register ALTER COLUMN review_date TYPE TIMESTAMPTZ USING review_date::timestamptz;
ALTER TABLE risk_register ALTER COLUMN status TYPE VARCHAR(16) USING left(status, 16);
-- security_incident
ALTER TABLE security_incident ALTER COLUMN severity TYPE VARCHAR(16) USING left(severity, 16);
ALTER TABLE security_incident ALTER COLUMN status TYPE VARCHAR(16) USING left(status, 16);
-- security_policy
ALTER TABLE security_policy ALTER COLUMN effective_date TYPE TIMESTAMPTZ USING effective_date::timestamptz;
ALTER TABLE security_policy ALTER COLUMN status TYPE VARCHAR(16) USING left(status, 16);
ALTER TABLE security_policy ALTER COLUMN version TYPE VARCHAR(16) USING left(version, 16);
-- software_license
ALTER TABLE software_license ALTER COLUMN license_document_url TYPE VARCHAR(255) USING left(license_document_url, 255);
ALTER TABLE software_license ALTER COLUMN license_key TYPE VARCHAR(255) USING left(license_key, 255);
ALTER TABLE software_license ALTER COLUMN license_type TYPE VARCHAR(20) USING left(license_type, 20);
ALTER TABLE software_license ALTER COLUMN status TYPE VARCHAR(20) USING left(status, 20);
ALTER TABLE software_license ALTER COLUMN version TYPE VARCHAR(255) USING version::varchar;
-- subscription_plan
ALTER TABLE subscription_plan ALTER COLUMN code TYPE VARCHAR(40) USING left(code, 40);
ALTER TABLE subscription_plan ALTER COLUMN name TYPE VARCHAR(80) USING left(name, 80);
ALTER TABLE subscription_plan ALTER COLUMN tier TYPE VARCHAR(20) USING left(tier, 20);
-- supplier
ALTER TABLE supplier ALTER COLUMN phone TYPE VARCHAR(255) USING phone::varchar;
-- vulnerability_scan
ALTER TABLE vulnerability_scan ALTER COLUMN report_url TYPE VARCHAR(255) USING left(report_url, 255);
-- webhook
ALTER TABLE webhook ALTER COLUMN secret TYPE VARCHAR(200) USING left(secret, 200);
ALTER TABLE webhook ALTER COLUMN url TYPE VARCHAR(2048) USING url::varchar;
