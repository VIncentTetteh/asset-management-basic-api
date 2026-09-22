package com.assetiq.dto.compliance;

import com.assetiq.models.compliance.IcsAsset;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class IcsAssetDto {

    private UUID id;
    private UUID organisationId;

    @NotNull(groups = OnCreate.class, message = "Asset ID is required")
    private UUID assetId;

    private String assetName;
    private UUID securityZoneId;
    private String securityZoneName;
    @Size(max = 64)
    private String firmwareVersion;
    @Size(max = 128)
    private String protocol;
    private IcsAsset.VendorSupportStatus vendorSupportStatus;
    private Instant lastPatchedAt;
    private String knownVulnerabilities;
    private Boolean isolated;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
