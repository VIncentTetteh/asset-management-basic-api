package com.assetiq.imports;

import java.util.Locale;
import java.util.Set;
import java.util.Optional;

/**
 * The record types that can be bulk-imported.
 *
 * <p>The slug is the URL segment, and it is part of the public contract shared with the
 * web wizard — {@code licenses} rather than {@code licences} because that is the
 * existing web route. Older and alternative spellings resolve too, so a hand-written
 * URL still works.</p>
 */
public enum ImportEntityType {

    ASSETS("assets", "Assets",
            "Hardware, software, vehicles and equipment on the register.",
            Set.of("CREATE_ASSET", "MANAGE_ORGANIZATION_SETTINGS"),
            Set.of()),
    SUPPLIERS("suppliers", "Suppliers",
            "Vendors you buy from, with their contact and tax details.",
            Set.of("MANAGE_SUPPLIERS"),
            Set.of("vendors")),
    EMPLOYEES("employees", "Employees",
            "People assets are assigned to or checked out by.",
            Set.of("MANAGE_EMPLOYEES"),
            Set.of("staff", "people")),
    LOCATIONS("locations", "Locations",
            "Sites, buildings, floors and rooms assets live in.",
            Set.of("MANAGE_LOCATIONS"),
            Set.of("sites")),
    DEPARTMENTS("departments", "Departments",
            "Cost centres and org units that own assets and budgets.",
            Set.of("MANAGE_DEPARTMENTS"),
            Set.of("depts")),
    CATEGORIES("categories", "Categories",
            "The asset classification tree and its depreciation defaults.",
            Set.of("MANAGE_CATEGORIES"),
            Set.of()),
    SOFTWARE_LICENCES("licenses", "Software licences",
            "Licence entitlements, seat counts and renewal dates.",
            Set.of("MANAGE_SOFTWARE_LICENSES"),
            Set.of("licences", "software-licences", "software-licenses", "software")),
    CONTRACTS("contracts", "Contracts",
            "Purchase, lease, maintenance, SLA, warranty and insurance agreements.",
            Set.of("MANAGE_CONTRACTS"),
            Set.of());

    /**
     * Authorities that grant an import of any type. Deliberately only the two admin
     * roles: a user who may import suppliers must not thereby be able to import
     * employees, so every other grant is per type.
     */
    public static final Set<String> ADMIN_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_ORG_ADMIN");

    private final String slug;
    private final String label;
    private final String description;
    private final Set<String> writeAuthorities;
    private final Set<String> slugAliases;

    ImportEntityType(String slug, String label, String description,
                     Set<String> writeAuthorities, Set<String> slugAliases) {
        this.slug = slug;
        this.label = label;
        this.description = description;
        this.writeAuthorities = writeAuthorities;
        this.slugAliases = slugAliases;
    }

    public String slug() { return slug; }
    public String label() { return label; }
    public String description() { return description; }

    /**
     * The entity-specific authorities that permit importing this type, on top of
     * {@link #ADMIN_AUTHORITIES}. These mirror the write permission the type's own REST
     * controller requires, so an import is never a weaker door than the API.
     */
    public Set<String> writeAuthorities() { return writeAuthorities; }

    /**
     * Resolve a URL segment. Accepts the slug, the enum name, and the common
     * American spelling of the licence slug so a hand-written URL still works.
     */
    public static Optional<ImportEntityType> fromSlug(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String candidate = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (ImportEntityType type : values()) {
            if (type.slug.equals(candidate)
                    || type.slugAliases.contains(candidate)
                    || type.name().toLowerCase(Locale.ROOT).replace('_', '-').equals(candidate)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
