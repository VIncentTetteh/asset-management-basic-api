package com.assetiq.dto;

import com.assetiq.enums.DepartmentStatus;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class DepartmentDto {
    private UUID id;

    @NotBlank(groups = OnCreate.class, message = "Department name is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String name;

    private String description;

    @Size(max = 255)
    private String departmentCode;

    private UUID parentDepartmentId;

    private UUID managerId;

    @Size(max = 255)
    private String costCenterCode;

    @PositiveOrZero
    @Digits(integer = 36, fraction = 2)
    private BigDecimal budgetLimit;

    /**
     * PATCH/PUT only: {@code true} removes the planning cap. An omitted
     * {@code budgetLimit} means "unchanged", so without this flag a cap could
     * only ever be changed, never taken off.
     */
    private Boolean clearBudgetLimit;

    private DepartmentStatus status;

    private UUID organisationId;

    private Instant createdAt;

    private Instant updatedAt;
}
