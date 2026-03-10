package com.example.demo.models.compliance;

import com.example.demo.models.Asset;
import com.example.demo.models.BaseEntity;
import com.example.demo.models.Organisation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * ICS-specific metadata overlay for assets in operational technology environments.
 * Extends the core Asset model with OT/ICS compliance fields.
 */
@Entity
@Table(name = "ics_asset",
        indexes = @Index(name = "idx_ics_asset_org", columnList = "organisation_id"))
@Getter
@Setter
public class IcsAsset extends BaseEntity {

    public enum VendorSupportStatus { SUPPORTED, END_OF_LIFE, END_OF_SUPPORT, UNKNOWN }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false, unique = true)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_zone_id")
    private SecurityZone securityZone;

    @Column(name = "firmware_version", length = 64)
    private String firmwareVersion;

    @Column(name = "protocol", length = 128)
    private String protocol;

    @Enumerated(EnumType.STRING)
    @Column(name = "vendor_support_status", length = 24)
    private VendorSupportStatus vendorSupportStatus = VendorSupportStatus.UNKNOWN;

    @Column(name = "last_patched_at")
    private Instant lastPatchedAt;

    @Column(name = "known_vulnerabilities", columnDefinition = "TEXT")
    private String knownVulnerabilities;

    /** Whether this asset is air-gapped / network isolated */
    @Column(name = "isolated")
    private Boolean isolated = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
