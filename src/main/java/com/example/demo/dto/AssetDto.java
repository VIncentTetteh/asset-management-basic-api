package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public class AssetDto {
    public UUID id;

    @NotBlank
    public String name;

    @NotBlank
    public String category;

    @NotNull
    @PositiveOrZero
    public BigDecimal purchaseCost;

    @NotNull
    @Positive
    public Integer usefulLifeInYears;

    public String state;
    public UUID departmentId;
    public UUID organisationId;
}
