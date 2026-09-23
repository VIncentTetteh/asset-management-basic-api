package com.assetiq.security;

import com.assetiq.enums.Permission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Plain-language descriptions of every {@link Permission}, so a screen can say
 * "this person will be able to dispose of assets" instead of printing
 * {@code DISPOSE_ASSET}.
 *
 * <p>Two further facts travel with each entry and matter as much as the wording:
 * <ul>
 *   <li>{@code enforced} — whether any endpoint actually consults the permission.
 *       Three do not (see {@link #UNENFORCED}); presenting them as if they gated
 *       something is a lie a permission matrix should not tell.</li>
 *   <li>{@code group} and {@code write} — enough structure for a matrix to lay
 *       itself out without the client re-deriving categories from name prefixes,
 *       which is what the web app does today.</li>
 * </ul>
 *
 * <p>This is a static catalogue, not configuration: it describes the code's own
 * authority checks, so it belongs beside them and changes with them.
 */
public final class PermissionCatalogue {

    /**
     * Permissions no endpoint consults today. They can be granted, and granting
     * them changes nothing. Reported honestly rather than quietly hidden, because
     * a role that "has" them and cannot do the thing is worse than one that does
     * not offer them.
     */
    public static final Set<Permission> UNENFORCED = Set.of(
            Permission.ESCALATE_REQUESTS,
            Permission.REGENERATE_QR,
            Permission.REVIEW_ACCESS);

    /**
     * One catalogue entry.
     *
     * @param permission the enum value, as it appears on a role
     * @param group      human-readable area of the product
     * @param label      short verb phrase — "Dispose of assets"
     * @param summary    one sentence a non-technical administrator can act on
     * @param write      true when it permits change, false when it only permits reading
     * @param enforced   whether any endpoint actually requires it
     */
    public record Entry(Permission permission, String group, String label, String summary,
                        boolean write, boolean enforced) {

        public String key() {
            return permission.name();
        }
    }

    private static final Map<Permission, Entry> ENTRIES = new EnumMap<>(Permission.class);

    private static void add(Permission p, String group, String label, String summary, boolean write) {
        ENTRIES.put(p, new Entry(p, group, label, summary, write, !UNENFORCED.contains(p)));
    }

    static {
        // ── Assets ────────────────────────────────────────────────────────────
        add(Permission.VIEW_ASSETS, "Assets", "See the asset register",
                "Open the asset list and any individual asset record.", false);
        add(Permission.CREATE_ASSET, "Assets", "Add assets",
                "Register new assets, including bulk imports.", true);
        add(Permission.EDIT_ASSET, "Assets", "Edit assets",
                "Change asset details, status, custodian and location.", true);
        add(Permission.DELETE_ASSET, "Assets", "Delete assets",
                "Remove an asset record from the register.", true);
        add(Permission.DISPOSE_ASSET, "Assets", "Dispose of assets",
                "Record write-offs, sales and scrappage, retiring the asset.", true);
        add(Permission.TRANSFER_ASSET, "Assets", "Transfer assets",
                "Move assets between locations, departments and custodians.", true);
        add(Permission.CHECKOUT_ASSET, "Assets", "Check assets in and out",
                "Issue an asset to a colleague and take it back again.", true);
        add(Permission.REGENERATE_QR, "Assets", "Reissue asset QR codes",
                "Issue a replacement QR label for an asset.", true);

        // ── Approvals ─────────────────────────────────────────────────────────
        add(Permission.APPROVE_REQUESTS, "Approvals", "Approve requests",
                "Sign off requests that are waiting on a decision.", true);
        add(Permission.REJECT_REQUESTS, "Approvals", "Reject requests",
                "Turn down requests that are waiting on a decision.", true);
        add(Permission.ESCALATE_REQUESTS, "Approvals", "Escalate requests",
                "Pass a request up to a higher approver.", true);

        // ── People ────────────────────────────────────────────────────────────
        add(Permission.VIEW_USERS, "People", "See colleagues",
                "Open the user directory and individual profiles.", false);
        add(Permission.MANAGE_USERS, "People", "Add and invite colleagues",
                "Invite people, create accounts, and change who can sign in.", true);
        add(Permission.EDIT_USER, "People", "Edit colleagues' profiles",
                "Change another person's name, contact details and department.", true);
        add(Permission.DELETE_USER, "People", "Remove colleagues",
                "Deactivate accounts so they can no longer sign in.", true);
        add(Permission.VIEW_EMPLOYEES, "People", "See employee records",
                "Open HR employee records and their asset handovers.", false);
        add(Permission.MANAGE_EMPLOYEES, "People", "Manage employee records",
                "Create and edit employee records and onboarding checklists.", true);
        add(Permission.OFFBOARD_EMPLOYEE, "People", "Offboard employees",
                "Run a leaver's checklist and reclaim the assets they hold.", true);
        add(Permission.VIEW_DEPARTMENTS, "People", "See departments",
                "Open the department structure.", false);
        add(Permission.MANAGE_DEPARTMENTS, "People", "Manage departments",
                "Create, rename and restructure departments.", true);

        // ── Access control ────────────────────────────────────────────────────
        add(Permission.VIEW_ROLES, "Access control", "See roles",
                "Open the roles list and see what each role allows.", false);
        add(Permission.MANAGE_ROLES, "Access control", "Manage roles",
                "Create roles, change what they allow, and delete them. "
                        + "A role can never be given permissions the person editing it does not hold.", true);
        add(Permission.REVIEW_ACCESS, "Access control", "Review access",
                "Carry out periodic reviews of who has access to what.", true);
        add(Permission.SYSTEM_ADMIN, "Access control", "Full administrative access",
                "Everything an organisation administrator can do, including access control.", true);

        // ── Settings ──────────────────────────────────────────────────────────
        add(Permission.MANAGE_ORGANIZATION_SETTINGS, "Settings", "Manage organisation settings",
                "Change company details, currency and product-wide preferences.", true);
        add(Permission.MANAGE_SECURITY_SETTINGS, "Settings", "Manage security settings",
                "Configure single sign-on, email domains and security policy.", true);

        // ── Locations & categories ────────────────────────────────────────────
        add(Permission.VIEW_LOCATIONS, "Locations & categories", "See locations",
                "Open the site and location hierarchy.", false);
        add(Permission.MANAGE_LOCATIONS, "Locations & categories", "Manage locations",
                "Create, rename and restructure sites and locations.", true);
        add(Permission.VIEW_CATEGORIES, "Locations & categories", "See categories",
                "Open the asset category list.", false);
        add(Permission.MANAGE_CATEGORIES, "Locations & categories", "Manage categories",
                "Create, rename and restructure asset categories.", true);

        // ── Maintenance ───────────────────────────────────────────────────────
        add(Permission.VIEW_MAINTENANCE, "Maintenance", "See maintenance",
                "Open maintenance schedules and history.", false);
        add(Permission.SCHEDULE_MAINTENANCE, "Maintenance", "Schedule maintenance",
                "Book planned and corrective maintenance against assets.", true);
        add(Permission.MARK_MAINTENANCE_COMPLETE, "Maintenance", "Complete maintenance",
                "Close out maintenance jobs and record what was done.", true);

        // ── Audit & compliance ────────────────────────────────────────────────
        add(Permission.CONDUCT_AUDIT, "Audit & compliance", "Run physical audits",
                "Start a stock-take and record what was found.", true);
        add(Permission.VIEW_AUDIT_LOGS, "Audit & compliance", "See the audit trail",
                "Read the record of who changed what, and when.", false);
        add(Permission.EXPORT_AUDIT_LOGS, "Audit & compliance", "Export the audit trail",
                "Download audit records for an external reviewer.", false);
        add(Permission.VIEW_COMPLIANCE, "Audit & compliance", "See compliance records",
                "Open policies, risks, incidents and their evidence.", false);
        add(Permission.MANAGE_COMPLIANCE, "Audit & compliance", "Manage compliance records",
                "Create and update policies, risks and incidents.", true);

        // ── Reporting ─────────────────────────────────────────────────────────
        add(Permission.VIEW_REPORTS, "Reporting", "See reports",
                "Open dashboards and saved reports.", false);
        add(Permission.GENERATE_REPORTS, "Reporting", "Generate reports",
                "Run reports over the organisation's data.", true);
        add(Permission.EXPORT_REPORTS, "Reporting", "Export reports",
                "Download reports as files.", false);
        add(Permission.USE_AI_ASSISTANT, "Reporting", "Use the AI assistant",
                "Ask the assistant questions. It can only read what this role "
                        + "is otherwise allowed to see.", false);

        // ── Finance ───────────────────────────────────────────────────────────
        add(Permission.VIEW_BUDGETS, "Finance", "See budgets",
                "Open budgets and their spend.", false);
        add(Permission.MANAGE_BUDGETS, "Finance", "Manage budgets",
                "Create and adjust budgets and their allocations.", true);
        add(Permission.APPROVE_BUDGET, "Finance", "Approve budgets",
                "Sign off a budget so it can be spent against.", true);
        add(Permission.MANAGE_EXPENSES, "Finance", "Manage expenses",
                "Record and adjust expenses charged against assets and budgets.", true);
        add(Permission.VIEW_TCO, "Finance", "See total cost of ownership",
                "Open lifetime cost analysis for assets.", false);
        add(Permission.MANAGE_EXCHANGE_RATES, "Finance", "Manage exchange rates",
                "Set the rates used to convert between currencies.", true);
        add(Permission.MANAGE_LEASES, "Finance", "Manage leases",
                "Record and update leased and rented assets.", true);
        add(Permission.VIEW_DEPRECIATION, "Finance", "See depreciation",
                "Open depreciation policies and schedules.", false);
        add(Permission.MANAGE_DEPRECIATION, "Finance", "Manage depreciation",
                "Set depreciation methods and rates.", true);

        // ── Procurement ───────────────────────────────────────────────────────
        add(Permission.VIEW_PROCUREMENT, "Procurement", "See purchase orders",
                "Open purchase orders and their line items.", false);
        add(Permission.MANAGE_PROCUREMENT, "Procurement", "Manage purchase orders",
                "Raise and amend purchase orders.", true);
        add(Permission.APPROVE_PROCUREMENT, "Procurement", "Approve purchase orders",
                "Authorise a purchase order for issue to a supplier.", true);
        add(Permission.VIEW_SUPPLIERS, "Procurement", "See suppliers",
                "Open the supplier list and supplier records.", false);
        add(Permission.MANAGE_SUPPLIERS, "Procurement", "Manage suppliers",
                "Add and edit suppliers.", true);
        add(Permission.VIEW_CONTRACTS, "Procurement", "See contracts",
                "Open supplier and service contracts.", false);
        add(Permission.MANAGE_CONTRACTS, "Procurement", "Manage contracts",
                "Create, renew and terminate contracts.", true);
        add(Permission.VIEW_VENDOR_REVIEWS, "Procurement", "See vendor reviews",
                "Open supplier performance reviews.", false);
        add(Permission.MANAGE_VENDOR_REVIEWS, "Procurement", "Manage vendor reviews",
                "Record and update supplier performance reviews.", true);

        // ── IT & infrastructure ───────────────────────────────────────────────
        add(Permission.VIEW_SOFTWARE_LICENSES, "IT & infrastructure", "See software licences",
                "Open software licences and their allocations.", false);
        add(Permission.MANAGE_SOFTWARE_LICENSES, "IT & infrastructure", "Manage software licences",
                "Add licences and assign seats.", true);
        add(Permission.VIEW_NETWORK_DISCOVERY, "IT & infrastructure", "See discovered devices",
                "Open the results of network discovery scans.", false);
        add(Permission.MANAGE_NETWORK_DISCOVERY, "IT & infrastructure", "Run network discovery",
                "Start scans and turn discovered devices into assets.", true);
        add(Permission.VIEW_CLOUD_ASSETS, "IT & infrastructure", "See cloud resources",
                "Open synchronised cloud resources and their costs.", false);
        add(Permission.MANAGE_CLOUD_ASSETS, "IT & infrastructure", "Manage cloud connections",
                "Connect cloud accounts and control synchronisation.", true);
    }

    static {
        // A missing entry means a permission the UI would render as a bare enum
        // name. Fail at class-load rather than in front of a customer.
        for (Permission p : Permission.values()) {
            if (!ENTRIES.containsKey(p)) {
                throw new IllegalStateException(
                        "PermissionCatalogue has no entry for " + p.name() + " — add one before shipping it");
            }
        }
    }

    private PermissionCatalogue() {
    }

    /** The entry for one permission, or null if the name is not a permission at all. */
    public static Entry find(String permissionName) {
        if (permissionName == null) return null;
        try {
            return ENTRIES.get(Permission.valueOf(permissionName));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** Every entry, in permission-declaration order. */
    public static List<Entry> all() {
        return new ArrayList<>(ENTRIES.values());
    }

    /**
     * Describes the given permission names, skipping any the catalogue does not
     * recognise (a stale row left by an older build should not break a screen).
     * Ordered by group, then by read-before-write, then alphabetically.
     */
    public static List<Entry> describe(Iterable<String> permissionNames) {
        List<Entry> out = new ArrayList<>();
        for (String name : permissionNames) {
            Entry e = find(name);
            if (e != null) out.add(e);
        }
        out.sort(Comparator.comparing(Entry::group)
                .thenComparing(Entry::write)
                .thenComparing(Entry::label));
        return out;
    }

    /** The same entries, bucketed by group and keeping the sort within each bucket. */
    public static Map<String, List<Entry>> grouped(List<Entry> entries) {
        return entries.stream().collect(Collectors.groupingBy(
                Entry::group, LinkedHashMap::new, Collectors.toList()));
    }
}
