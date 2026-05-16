package com.assetiq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


import java.time.Instant;
import java.util.UUID;

@Data
public class CategoryDto {
    private UUID id;

    @NotBlank(message = "Category name is required")
    private String name;

    private String description;

    private UUID parentCategoryId;

    private UUID depreciationPolicyId;

    private Integer defaultWarrantyPeriodMonths;

    private String assetPrefixCode;

    private UUID organisationId;

    private Instant createdAt;

    private Instant updatedAt;
}

