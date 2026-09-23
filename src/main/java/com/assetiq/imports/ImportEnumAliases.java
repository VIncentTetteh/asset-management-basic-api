package com.assetiq.imports;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What a customer's own vocabulary means in AssetIQ's enums.
 *
 * <p>A sheet exported from another platform says {@code Laptop}, not {@code HARDWARE};
 * {@code In Service}, not {@code IN_USE}. Refusing the file over that is the single
 * biggest reason a migration stalls, so two things happen instead: the wizard is handed
 * a <em>suggestion</em> per distinct raw value (rendered as a dropdown the user can
 * correct), and an unrecognised value is never fatal — see {@link ImportRow#enumValue}.
 *
 * <p>The table is deliberately small and hand-written. A fuzzy matcher that guessed
 * {@code "Retired"} as {@code RESERVED} would be worse than no suggestion at all: the
 * user sees a dropdown either way, and a wrong pre-selection is one they may not
 * notice across 3000 rows.</p>
 *
 * <p>Lookup order, most confident first: the constant itself (after case and
 * punctuation are collapsed), then this table, keyed on the enum's simple name so two
 * enums may read the same word differently — {@code ACTIVE} means one thing on a
 * supplier and another on a licence.</p>
 */
public final class ImportEnumAliases {

    private ImportEnumAliases() {}

    /** enum simple name → normalised alias → constant name. */
    private static final Map<String, Map<String, String>> TABLE = build();

    /**
     * Collapses a raw cell to its comparison form: lower case, no punctuation, no
     * whitespace. {@code "In Service"}, {@code "in-service"} and {@code "IN_SERVICE"}
     * all become {@code "inservice"}.
     */
    public static String normalise(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * The constant a raw cell means, if it can be known without guessing.
     *
     * @param enumType    the enum's simple name, e.g. {@code AssetType}
     * @param allowed     the enum's constants, in declaration order
     * @param rawValue    the cell as the user wrote it
     * @return the constant name, or empty when nothing in the table matches
     */
    public static Optional<String> suggest(String enumType, List<String> allowed, String rawValue) {
        String key = normalise(rawValue);
        if (key.isEmpty() || allowed == null || allowed.isEmpty()) return Optional.empty();

        for (String constant : allowed) {
            if (normalise(constant).equals(key)) return Optional.of(constant);
        }
        Map<String, String> aliases = TABLE.get(enumType);
        if (aliases == null) return Optional.empty();
        String constant = aliases.get(key);
        return constant != null && allowed.contains(constant) ? Optional.of(constant) : Optional.empty();
    }

    /** The same lookup against a live enum class. */
    public static <E extends Enum<E>> Optional<E> resolve(Class<E> type, String rawValue) {
        List<String> allowed = java.util.Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        return suggest(type.getSimpleName(), allowed, rawValue).map(name -> Enum.valueOf(type, name));
    }

    private static Map<String, Map<String, String>> build() {
        Map<String, Map<String, String>> table = new LinkedHashMap<>();

        table.put("AssetType", aliases(
                "HARDWARE", "laptop", "laptops", "notebook", "desktop", "desktops", "pc", "pcs",
                "computer", "computers", "workstation", "monitor", "monitors", "screen", "display",
                "printer", "printers", "scanner", "server", "servers", "network", "networking",
                "network device", "router", "switch", "firewall", "phone", "mobile", "mobile phone",
                "smartphone", "tablet", "ipad", "device", "devices", "peripheral", "peripherals",
                "it equipment", "it asset", "it hardware", "physical", "keyboard", "mouse",
                "docking station", "dock", "projector", "camera", "storage", "nas", "ups")
                .and("SOFTWARE", "app", "apps", "application", "applications", "licence", "license",
                        "licences", "licenses", "saas", "subscription", "program", "programme",
                        "software licence", "software license", "cloud", "cloud service", "platform")
                .and("VEHICLE", "car", "cars", "van", "vans", "truck", "trucks", "lorry", "bus",
                        "motorbike", "motorcycle", "bike", "fleet", "automobile", "pickup", "trailer",
                        "forklift", "tractor")
                .and("FURNITURE", "desk", "desks", "chair", "chairs", "table", "tables", "cabinet",
                        "cabinets", "shelf", "shelving", "sofa", "cupboard", "locker", "workbench",
                        "office furniture", "fitting", "fittings", "fixture", "fixtures")
                .and("EQUIPMENT", "machine", "machinery", "plant", "tool", "tools", "generator",
                        "aircon", "air conditioner", "hvac", "medical equipment", "lab equipment",
                        "kitchen equipment", "safety equipment", "instrument", "instruments",
                        "appliance", "appliances")
                .and("OTHER", "misc", "miscellaneous", "n/a", "na", "unknown", "unclassified",
                        "uncategorised", "uncategorized", "general")
                .build());

        table.put("AssetStatus", aliases(
                "IN_USE", "in service", "active", "deployed", "assigned", "issued", "allocated",
                "operational", "live", "working", "checked out", "in production", "use")
                .and("IN_STOCK", "stock", "available", "spare", "unassigned", "in store", "store",
                        "warehouse", "stored", "idle", "unallocated", "on hand", "inventory",
                        "not in use", "free")
                .and("MAINTENANCE", "servicing", "in maintenance", "under maintenance",
                        "scheduled maintenance", "maintenance due", "being serviced")
                .and("UNDER_REPAIR", "repair", "in repair", "broken", "faulty", "defective",
                        "out of order", "not working", "awaiting repair", "rma")
                .and("RETIRED", "decommissioned", "end of life", "eol", "withdrawn", "inactive",
                        "out of service", "obsolete", "written off", "write off")
                .and("DISPOSED", "disposal", "sold", "scrapped", "scrap", "destroyed", "recycled",
                        "donated", "traded in")
                .and("MISSING", "lost", "stolen", "not found", "unaccounted", "untraceable")
                .and("RESERVED", "on hold", "hold", "booked", "earmarked", "pending assignment")
                .and("PENDING_PROCUREMENT", "on order", "ordered", "requested", "awaiting delivery",
                        "procurement", "to be purchased", "pending purchase")
                .build());

        table.put("AssetCondition", aliases(
                "NEW", "brand new", "unused", "as new", "sealed", "mint")
                .and("EXCELLENT", "very good", "excellent condition", "a", "grade a", "like new")
                .and("GOOD", "ok", "okay", "fine", "serviceable", "working", "b", "grade b",
                        "satisfactory", "normal", "average")
                .and("FAIR", "used", "worn", "acceptable", "c", "grade c", "moderate", "usable")
                .and("POOR", "bad", "very worn", "d", "grade d", "end of life", "deteriorated")
                .and("DAMAGED", "broken", "faulty", "defective", "cracked", "not working",
                        "needs repair", "beyond repair")
                .and("SCRAP", "write off", "written off", "junk", "for disposal", "unusable",
                        "condemned")
                .build());

        table.put("DepreciationMethod", aliases(
                "STRAIGHT_LINE", "straight line", "sl", "linear", "straightline", "prime cost",
                "equal instalment", "fixed instalment")
                .and("DECLINING_BALANCE", "reducing balance", "diminishing balance", "db",
                        "declining", "reducing", "written down value", "wdv", "double declining")
                .and("UNITS_OF_PRODUCTION", "units of output", "usage based", "production units",
                        "activity", "uop", "per unit")
                .and("SUM_OF_YEARS_DIGITS", "sum of years", "syd", "sum of the years digits",
                        "sum of digits")
                .build());

        table.put("ContractType", aliases(
                "PURCHASE", "buy", "purchase order", "po", "acquisition", "sale", "procurement")
                .and("LEASE", "leasing", "rental", "rent", "hire", "hire purchase", "operating lease",
                        "finance lease")
                .and("MAINTENANCE", "amc", "support", "support contract", "service contract",
                        "servicing", "upkeep")
                .and("SERVICE_LEVEL_AGREEMENT", "sla", "service level", "service agreement",
                        "service level agreement")
                .and("WARRANTY", "guarantee", "extended warranty", "manufacturer warranty")
                .and("INSURANCE", "policy", "insurance policy", "cover", "coverage")
                .and("OTHER", "misc", "miscellaneous", "general", "n/a", "na", "unknown")
                .build());

        table.put("ContractStatus", aliases(
                "ACTIVE", "live", "in force", "current", "running", "in effect", "signed", "open")
                .and("DRAFT", "pending", "unsigned", "in review", "proposed", "new")
                .and("EXPIRING_SOON", "expiring", "due for renewal", "renewal due", "ending soon")
                .and("EXPIRED", "lapsed", "ended", "out of date", "past", "closed")
                .and("TERMINATED", "cancelled", "canceled", "ended early", "void", "revoked")
                .and("RENEWED", "extended", "renewal", "rolled over")
                .build());

        table.put("SupplierStatus", aliases(
                "ACTIVE", "approved", "current", "live", "enabled", "in use", "preferred", "yes")
                .and("INACTIVE", "disabled", "dormant", "archived", "not in use", "closed", "no",
                        "former", "ceased")
                .and("SUSPENDED", "on hold", "hold", "paused", "under review", "frozen")
                .and("BLACKLISTED", "blacklist", "banned", "barred", "blocked", "debarred",
                        "do not use")
                .build());

        table.put("LicenseType", aliases(
                "PERPETUAL", "permanent", "one off", "one time", "outright", "lifetime", "owned")
                .and("SUBSCRIPTION", "saas", "annual", "monthly", "recurring", "term", "rental",
                        "subscription based")
                .and("VOLUME", "volume licence", "volume license", "bulk", "multi seat",
                        "site licence", "site license", "site")
                .and("OPEN_SOURCE", "oss", "free", "gpl", "mit", "apache", "foss", "community")
                .and("TRIAL", "evaluation", "eval", "demo", "pilot", "poc", "test")
                .and("ENTERPRISE", "ela", "enterprise agreement", "corporate", "organisation wide",
                        "organization wide")
                .and("OEM", "bundled", "preinstalled", "pre installed", "with hardware")
                .build());

        table.put("LicenseStatus", aliases(
                "ACTIVE", "live", "current", "valid", "in use", "assigned", "enabled")
                .and("EXPIRING_SOON", "expiring", "due for renewal", "renewal due", "ending soon")
                .and("EXPIRED", "lapsed", "out of date", "ended", "invalid", "past")
                .and("SUSPENDED", "on hold", "hold", "paused", "disabled", "frozen")
                .and("CANCELLED", "canceled", "terminated", "revoked", "withdrawn", "closed")
                .build());

        table.put("EmployeeStatus", aliases(
                "ACTIVE", "current", "employed", "working", "permanent", "full time", "part time",
                "in post", "yes")
                .and("ONBOARDING", "new", "new starter", "new joiner", "starting", "probation",
                        "pre hire", "induction")
                .and("ON_LEAVE", "leave", "absent", "sabbatical", "maternity", "paternity",
                        "sick leave", "suspended", "career break")
                .and("OFFBOARDING", "leaving", "notice", "on notice", "resigned", "exiting",
                        "serving notice")
                .and("TERMINATED", "left", "leaver", "former", "ex employee", "dismissed",
                        "inactive", "separated", "retired", "no")
                .build());

        table.put("DepartmentStatus", aliases(
                "ACTIVE", "current", "live", "open", "in use", "enabled", "yes")
                .and("INACTIVE", "disabled", "dormant", "closed", "not in use", "no")
                .and("ARCHIVED", "archive", "historic", "historical", "retired", "dissolved",
                        "merged")
                .build());

        Map<String, Map<String, String>> frozen = new LinkedHashMap<>();
        table.forEach((k, v) -> frozen.put(k, Map.copyOf(v)));
        return Map.copyOf(frozen);
    }

    // ── Table construction ────────────────────────────────────────────────────

    private static AliasBuilder aliases(String constant, String... values) {
        return new AliasBuilder().and(constant, values);
    }

    private static final class AliasBuilder {
        private final Map<String, String> map = new LinkedHashMap<>();

        AliasBuilder and(String constant, String... values) {
            for (String value : values) {
                String key = normalise(value);
                if (key.isEmpty()) continue;
                // First declaration wins, so a word claimed by two constants keeps the
                // meaning it was given first rather than flipping on a reordering.
                map.putIfAbsent(key, constant);
            }
            return this;
        }

        Map<String, String> build() {
            return map;
        }
    }
}
