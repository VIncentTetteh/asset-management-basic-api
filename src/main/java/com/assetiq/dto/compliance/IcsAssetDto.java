package com.assetiq.dto.compliance;

import com.assetiq.models.compliance.IcsAsset;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class IcsAssetDto {

    private UUID id;
    private UUID organisationId;

    @NotNull(message = "Asset ID is required")
    private UUID assetId;

    private String assetName;
    private UUID securityZoneId;
    private String securityZoneName;
    private String firmwareVersion;
    private String protocol;
    private IcsAsset.VendorSupportStatus vendorSupportStatus;
    private Instant lastPatchedAt;
    private String knownVulnerabilities;
    private Boolean isolated;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
