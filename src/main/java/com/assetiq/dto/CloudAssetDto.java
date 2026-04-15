package com.assetiq.dto;

import com.assetiq.enums.CloudAssetStatus;
import com.assetiq.enums.CloudProvider;
import com.assetiq.enums.CloudResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class CloudAssetDto {

    private UUID id;

    @NotBlank
    private String name;

    @NotNull
    private CloudProvider provider;

    @NotBlank
    private String region;

    @NotBlank
    private String resourceId;

    @NotNull
    private CloudResourceType resourceType;

    private CloudAssetStatus status;
    private String accountId;
    private BigDecimal monthlyCostEstimate;
    private String currency;
    private String environment;
    private String tags;
    private String description;
    private Instant lastSyncAt;
    private Instant createdAt;
    private Instant updatedAt;
}
