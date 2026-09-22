package com.assetiq.models;

import com.assetiq.enums.OrganisationStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;



import java.util.Set;

@Entity
@Table(name = "organisation")
@Data
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Organisation extends BaseEntity {

    /** Unique case-insensitively among live tenants (V52 partial index), not globally. */
    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String registrationNumber;

    @Column(unique = true)
    private String taxId;

    private String industry;

    private String country;

    // Stored as TEXT: the column is free text with no meaningful upper bound, and
    // the migrated schema declares it TEXT. Pinning a varchar length here would
    // both fail ddl-auto=validate and invite a truncating migration.
    @Column(columnDefinition = "TEXT")
    private String address;

    /** Not unique: several tenants may share a contact mailbox (V52). */
    private String contactEmail;

    private String contactPhone;

    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrganisationStatus status = OrganisationStatus.ACTIVE;

    @OneToMany(mappedBy = "organisation", cascade = CascadeType.ALL)
    private Set<Department> departments;

    @OneToMany(mappedBy = "organisation", cascade = CascadeType.ALL)
    private Set<User> users;

    @OneToMany(mappedBy = "organisation", cascade = CascadeType.ALL)
    private Set<Asset> assets;

    @OneToMany(mappedBy = "organisation", cascade = CascadeType.ALL)
    private Set<Role> roles;

    /**
     * ISO-4217 3-letter currency code for billing (nullable).
     * Used by CurrencyResolver as the tenant's default currency.
     */
    @Column(length = 3)
    private String billingCurrency;

    @Column(name = "email_domain", unique = true, nullable = true)
    private String emailDomain;

    /** Data protection officer (Ghana DPA 2012 / GDPR), V11. */
    @Column(name = "dpo_name")
    private String dpoName;

    @Column(name = "dpo_email")
    private String dpoEmail;

    /**
     * Where the tenant's personal data is declared to reside: GH, EU, US or OTHER
     * (V11/V13; NOT NULL DEFAULT 'GH' where beforeMigrate built the table).
     */
    @Column(name = "data_residency_region", length = 10)
    private String dataResidencyRegion = "GH";

    /**
     * When a closed account becomes eligible for permanent deletion.
     *
     * <p>Set alongside {@code deletedAt} when a tenant confirms closure; access stops
     * immediately but the rows survive until this passes, so an accidental or malicious
     * closure can still be undone. NULL for live accounts.
     */
    @Column(name = "purge_after")
    private java.time.Instant purgeAfter;

}
