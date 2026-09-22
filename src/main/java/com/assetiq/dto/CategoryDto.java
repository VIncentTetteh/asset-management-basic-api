package com.assetiq.dto;

import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class CategoryDto {
    private UUID id;

    @NotBlank(groups = OnCreate.class, message = "Category name is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String name;

    private String description;

    private UUID parentCategoryId;

    private UUID depreciationPolicyId;

    @PositiveOrZero
    private Integer defaultWarrantyPeriodMonths;

    @Size(max = 255)
    private String assetPrefixCode;

    private UUID organisationId;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * Update only: optional fields to clear, since a null field means "unchanged".
     * Allowed: {@code depreciationPolicyId}, {@code parentCategoryId}, {@code description},
     * {@code assetPrefixCode}, {@code defaultWarrantyPeriodMonths}.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<String> clearFields;
}

