package com.example.demo.models;

import com.example.demo.enums.LicenseStatus;
import com.example.demo.enums.LicenseType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tracks software licenses owned or subscribed to by an organisation.
 * Covers licence inventory, seat usage, expiry, and compliance status —
 * required for financial institutions to avoid shadow IT and audit failures.
 */
@Entity
@Table(name = "software_license", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"license_key", "organisation_id"},
                name = "uk_license_key_per_org")
})
@Data
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SoftwareLicense extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /** Vendor / publisher name. */
    @Column(nullable = false)
    private String vendor;

    /** Unique license key or agreement number. */
    @Column(name = "license_key")
    private String licenseKey;

    /** Product or software name (may differ from license agreement name). */
    private String productName;

    /** Version covered by this license. */
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LicenseType licenseType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LicenseStatus status = LicenseStatus.ACTIVE;

    /** Total seats / installations allowed. Null = unlimited. */
    private Integer totalSeats;

    /** Currently consumed seats (tracked via assignments). */
    private Integer usedSeats = 0;

    @Column(precision = 15, scale = 2)
    private BigDecimal purchaseCost;

    @Column(precision = 15, scale = 2)
    private BigDecimal annualRenewalCost;

    private String currency = "GHS";

    private LocalDate purchaseDate;

    private LocalDate expiryDate;

    private LocalDate renewalDate;

    /** Whether the license should auto-renew. */
    private Boolean autoRenew = false;

    /** URL to license agreement document. */
    private String licenseDocumentUrl;

    /** Internal notes (e.g. contract number, compliance notes). */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Optional link to the asset this license is installed on
     * (e.g. a specific server or laptop).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Organisation organisation;
}
