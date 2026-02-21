package com.example.demo.dto;

import com.example.demo.enums.DepreciationMethod;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class DepreciationPolicyDto {
    private UUID id;

    @NotBlank(message = "Policy name is required")
    private String name;

    private String description;

    private DepreciationMethod method;

    private Integer usefulLifeMonths;

    private BigDecimal salvageValuePercent;

    private UUID organisationId;
}

