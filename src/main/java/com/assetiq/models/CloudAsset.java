package com.assetiq.models;

import com.assetiq.enums.CloudAssetStatus;
import com.assetiq.enums.CloudProvider;
import com.assetiq.enums.CloudResourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

// Natural key is unique among live rows only: partial unique index uq_cloud_asset_org_resource_live
// (V46, WHERE deleted_at IS NULL). JPA cannot declare a partial index, so no
// @UniqueConstraint here; declaring the full one would claim soft-deleted rows count.
@Entity
@Getter
@Setter
@Table(name = "cloud_asset")
public class CloudAsset extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CloudProvider provider;

    @Column(nullable = false, length = 100)
    private String region;

    /** Cloud-native resource identifier (ARN, resource group path, GCP resource name, etc.) */
    @Column(name = "resource_id", nullable = false, length = 500)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    private CloudResourceType resourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private CloudAssetStatus status = CloudAssetStatus.UNKNOWN;

    /** Cloud account / subscription / project ID */
    @Column(name = "account_id", length = 200)
    private String accountId;

    /** Estimated monthly cost in specified currency */
    @Column(name = "monthly_cost_estimate", precision = 15, scale = 4)
    private BigDecimal monthlyCostEstimate;

    @Column(name = "currency", length = 10)
    private String currency = "USD";

    /** DEV, STAGING, PROD, etc. */
    @Column(name = "environment", length = 50)
    private String environment;

    /** JSON string of provider-specific tags/labels */
    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
}
