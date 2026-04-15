package com.assetiq.services;

import com.assetiq.models.Organisation;
import com.assetiq.models.Role;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.security.RolePermissionDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Seeds the standard set of platform roles for a given organisation.
 *
 * <p>Roles are idempotent: if a role with the same name already exists in the
 * organisation it is left unchanged. This makes the service safe to call both
 * on new-org creation and on application startup (e.g. via DevDataSeeder).
 *
 * <h2>Standard roles</h2>
 * <ol>
 *   <li><b>ADMIN</b>           – all platform permissions (full platform access)</li>
 *   <li><b>ASSET_MANAGER</b>   – full asset lifecycle, locations, categories,
 *                                 maintenance, transfers, disposals, audits</li>
 *   <li><b>PROCUREMENT_OFFICER</b> – suppliers, purchase orders, contracts,
 *                                    budgets, vendor reviews, approval workflows</li>
 *   <li><b>COMPLIANCE_OFFICER</b>  – all compliance, audit logs, risk &amp;
 *                                    incident management, reports</li>
 *   <li><b>IT_MANAGER</b>      – infrastructure (network discovery, cloud assets),
 *                                 software licenses, depreciation, asset management</li>
 *   <li><b>FINANCE_MANAGER</b> – budgets, expenses, TCO, exchange rates,
 *                                 depreciation, reports</li>
 *   <li><b>HR_MANAGER</b>      – users, departments, basic org settings</li>
 *   <li><b>VIEWER</b>          – read-only access across all platform areas</li>
 * </ol>
 */
@Service
public class DefaultRoleSeederService {

    private static final Logger log = LoggerFactory.getLogger(DefaultRoleSeederService.class);

    private final RoleRepository roleRepository;

    public DefaultRoleSeederService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    /**
     * Creates all standard platform roles for the given organisation.
     * Roles that already exist (matched by name + org) are skipped.
     *
     * @param organisation the organisation to seed roles for
     */
    @Transactional
    public void seedRolesForOrganisation(Organisation organisation) {
        log.info("[ROLE SEED] Seeding default roles for organisation: {}", organisation.getName());
        for (RoleDefinition def : PLATFORM_ROLES) {
            roleRepository
                    .findByNameAndOrganisationAndDeletedAtIsNull(def.name(), organisation)
                    .ifPresentOrElse(
                            existing -> {
                                if (RolePermissionDefaults.isAdminRoleName(def.name())
                                        && !Objects.equals(existing.getPermissions(), def.permissionsJson())) {
                                    existing.setPermissions(def.permissionsJson());
                                    roleRepository.save(existing);
                                    log.info("[ROLE SEED] Updated role '{}' permissions for org '{}'",
                                            def.name(), organisation.getName());
                                } else {
                                    log.debug("[ROLE SEED] Role '{}' already exists – skipping", def.name());
                                }
                            },
                            () -> {
                                Role role = new Role();
                                role.setName(def.name());
                                role.setDescription(def.description());
                                role.setPermissions(def.permissionsJson());
                                role.setOrganisation(organisation);
                                roleRepository.save(role);
                                log.info("[ROLE SEED] Created role '{}' for org '{}'",
                                        def.name(), organisation.getName());
                            });
        }
        log.info("[ROLE SEED] Completed for organisation: {}", organisation.getName());
    }

    // ── Role Definitions ──────────────────────────────────────────────────────

    record RoleDefinition(String name, String description, String permissionsJson) {}

    /**
     * Ordered list of standard platform roles.
     * Permissions are stored as JSON arrays for clarity and
     * compatibility with both {@link com.assetiq.security.PermissionCacheService}
     * parse formats.
     */
    static final List<RoleDefinition> PLATFORM_ROLES = List.of(

        // ── 1. ADMIN ────────────────────────────────────────────────────────
        new RoleDefinition(
            "ADMIN",
            "Full platform access — all permissions granted. Intended for platform administrators.",
            RolePermissionDefaults.allPermissionsJson()
        ),

        // ── 2. ASSET_MANAGER ────────────────────────────────────────────────
        new RoleDefinition(
            "ASSET_MANAGER",
            "Manages the full asset lifecycle — creation, editing, disposal, transfers, maintenance, audits, and reporting.",
            """
            ["VIEW_ASSETS","CREATE_ASSET","EDIT_ASSET","DELETE_ASSET","DISPOSE_ASSET","TRANSFER_ASSET",
             "CHECKOUT_ASSET","REGENERATE_QR",
             "VIEW_LOCATIONS","MANAGE_LOCATIONS",
             "VIEW_CATEGORIES","MANAGE_CATEGORIES",
             "SCHEDULE_MAINTENANCE","VIEW_MAINTENANCE","MARK_MAINTENANCE_COMPLETE",
             "CONDUCT_AUDIT","VIEW_AUDIT_LOGS","EXPORT_AUDIT_LOGS",
             "VIEW_REPORTS","GENERATE_REPORTS","EXPORT_REPORTS",
             "VIEW_DEPARTMENTS","VIEW_USERS",
             "APPROVE_REQUESTS","REJECT_REQUESTS",
             "VIEW_DEPRECIATION",
             "VIEW_SOFTWARE_LICENSES"]"""
        ),

        // ── 3. PROCUREMENT_OFFICER ──────────────────────────────────────────
        new RoleDefinition(
            "PROCUREMENT_OFFICER",
            "Manages procurement workflows — purchase orders, suppliers, contracts, budgets, and vendor reviews.",
            """
            ["VIEW_ASSETS",
             "VIEW_SUPPLIERS","MANAGE_SUPPLIERS",
             "VIEW_PROCUREMENT","MANAGE_PROCUREMENT","APPROVE_PROCUREMENT",
             "VIEW_CONTRACTS","MANAGE_CONTRACTS",
             "MANAGE_BUDGETS","VIEW_BUDGETS","APPROVE_BUDGET",
             "VIEW_VENDOR_REVIEWS","MANAGE_VENDOR_REVIEWS",
             "APPROVE_REQUESTS","REJECT_REQUESTS","ESCALATE_REQUESTS",
             "VIEW_REPORTS","GENERATE_REPORTS",
             "VIEW_DEPARTMENTS"]"""
        ),

        // ── 4. COMPLIANCE_OFFICER ───────────────────────────────────────────
        new RoleDefinition(
            "COMPLIANCE_OFFICER",
            "Oversees compliance controls, risk management, incident reporting, policies, and audit trails.",
            """
            ["VIEW_ASSETS",
             "VIEW_COMPLIANCE","MANAGE_COMPLIANCE",
             "CONDUCT_AUDIT","VIEW_AUDIT_LOGS","EXPORT_AUDIT_LOGS",
             "REVIEW_ACCESS",
             "VIEW_REPORTS","GENERATE_REPORTS","EXPORT_REPORTS",
             "VIEW_LOCATIONS","VIEW_CATEGORIES",
             "VIEW_DEPARTMENTS","VIEW_USERS",
             "VIEW_SOFTWARE_LICENSES",
             "VIEW_NETWORK_DISCOVERY","VIEW_CLOUD_ASSETS"]"""
        ),

        // ── 5. IT_MANAGER ───────────────────────────────────────────────────
        new RoleDefinition(
            "IT_MANAGER",
            "Manages IT infrastructure — network discovery, cloud assets, software licenses, and depreciation policies.",
            """
            ["VIEW_ASSETS","CREATE_ASSET","EDIT_ASSET","CHECKOUT_ASSET","REGENERATE_QR",
             "VIEW_NETWORK_DISCOVERY","MANAGE_NETWORK_DISCOVERY",
             "VIEW_CLOUD_ASSETS","MANAGE_CLOUD_ASSETS",
             "VIEW_SOFTWARE_LICENSES","MANAGE_SOFTWARE_LICENSES",
             "VIEW_DEPRECIATION","MANAGE_DEPRECIATION",
             "VIEW_CATEGORIES","VIEW_LOCATIONS",
             "SCHEDULE_MAINTENANCE","VIEW_MAINTENANCE","MARK_MAINTENANCE_COMPLETE",
             "VIEW_REPORTS","GENERATE_REPORTS",
             "VIEW_COMPLIANCE",
             "VIEW_DEPARTMENTS","VIEW_USERS"]"""
        ),

        // ── 6. FINANCE_MANAGER ──────────────────────────────────────────────
        new RoleDefinition(
            "FINANCE_MANAGER",
            "Oversees financial operations — budgets, expenses, TCO, exchange rates, depreciation, and financial reporting.",
            """
            ["VIEW_ASSETS",
             "MANAGE_BUDGETS","VIEW_BUDGETS","APPROVE_BUDGET",
             "MANAGE_EXPENSES","VIEW_TCO","MANAGE_EXCHANGE_RATES",
             "VIEW_DEPRECIATION","MANAGE_DEPRECIATION",
             "MANAGE_LEASES",
             "VIEW_PROCUREMENT","APPROVE_PROCUREMENT",
             "VIEW_CONTRACTS","VIEW_SUPPLIERS",
             "VIEW_REPORTS","GENERATE_REPORTS","EXPORT_REPORTS",
             "APPROVE_REQUESTS","REJECT_REQUESTS",
             "VIEW_DEPARTMENTS"]"""
        ),

        // ── 7. HR_MANAGER ───────────────────────────────────────────────────
        new RoleDefinition(
            "HR_MANAGER",
            "Manages the user directory and department structure within the organisation.",
            """
            ["VIEW_ASSETS","CHECKOUT_ASSET",
             "MANAGE_USERS","VIEW_USERS","EDIT_USER",
             "MANAGE_DEPARTMENTS","VIEW_DEPARTMENTS",
             "VIEW_LOCATIONS",
             "VIEW_REPORTS",
             "REVIEW_ACCESS"]"""
        ),

        // ── 8. VIEWER ───────────────────────────────────────────────────────
        new RoleDefinition(
            "VIEWER",
            "Read-only access across all platform areas. Cannot create, edit, or delete any records.",
            """
            ["VIEW_ASSETS",
             "VIEW_USERS","VIEW_DEPARTMENTS",
             "VIEW_MAINTENANCE","VIEW_AUDIT_LOGS",
             "VIEW_REPORTS",
             "VIEW_LOCATIONS","VIEW_CATEGORIES",
             "VIEW_SUPPLIERS","VIEW_PROCUREMENT","VIEW_CONTRACTS",
             "VIEW_BUDGETS","VIEW_VENDOR_REVIEWS",
             "VIEW_SOFTWARE_LICENSES",
             "VIEW_COMPLIANCE",
             "VIEW_NETWORK_DISCOVERY","VIEW_CLOUD_ASSETS",
             "VIEW_DEPRECIATION","VIEW_ROLES",
             "VIEW_TCO"]"""
        )
    );
}
