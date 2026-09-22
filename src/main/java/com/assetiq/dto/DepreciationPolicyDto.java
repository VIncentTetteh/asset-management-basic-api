package com.assetiq.dto;

import com.assetiq.enums.DepreciationMethod;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;


import java.math.BigDecimal;
import java.util.UUID;

@Data
public class DepreciationPolicyDto {
    private UUID id;

    @NotBlank(groups = OnCreate.class, message = "Policy name is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String name;

    private String description;

    @NotNull(groups = OnCreate.class, message = "Depreciation method is required")
    private DepreciationMethod method;

    @Min(1)
    private Integer usefulLifeMonths;

    @DecimalMin("0")
    @DecimalMax("100")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal salvageValuePercent;

    private UUID organisationId;
}

