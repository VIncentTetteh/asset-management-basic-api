package com.assetiq.dto;

import com.assetiq.enums.CloudAssetStatus;
import com.assetiq.enums.CloudProvider;
import com.assetiq.enums.CloudResourceType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class CloudAssetDto {

    private UUID id;

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotNull
    private CloudProvider provider;

    @NotBlank
    @Size(max = 100)
    private String region;

    @NotBlank
    @Size(max = 500)
    private String resourceId;

    @NotNull
    private CloudResourceType resourceType;

    private CloudAssetStatus status;
    @Size(max = 200)
    private String accountId;
    @PositiveOrZero
    @Digits(integer = 11, fraction = 4)
    private BigDecimal monthlyCostEstimate;
    @Size(max = 10)
    private String currency;
    @Size(max = 50)
    private String environment;
    private String tags;
    private String description;
    private Instant lastSyncAt;
    private Instant createdAt;
    private Instant updatedAt;
}
