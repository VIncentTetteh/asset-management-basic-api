package com.example.demo.dto;

import com.example.demo.enums.DepartmentStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class DepartmentDto {
    private UUID id;

    @NotBlank(message = "Department name is required")
    private String name;

    private String departmentCode;

    private UUID parentDepartmentId;

    private UUID managerId;

    private String costCenterCode;

    private BigDecimal budgetLimit;

    private DepartmentStatus status;

    private UUID organisationId;
}
