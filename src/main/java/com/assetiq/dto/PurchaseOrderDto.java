package com.assetiq.dto;

import com.assetiq.enums.POStatus;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class PurchaseOrderDto {
    private UUID id;

    @NotBlank(groups = OnCreate.class, message = "PO number is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String poNumber;

    @NotNull(groups = OnCreate.class, message = "Total amount is required")
    @DecimalMin(value = "0.01", message = "Total amount must be greater than zero")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal totalAmount;

    @Size(max = 3)
    private String currency;

    private POStatus status;

    private UUID approvedById;

    private UUID requestedById;

    private UUID rejectedById;

    private Instant approvedAt;

    private Instant rejectedAt;

    private String remarks;

    private UUID organisationId;

    @NotNull(groups = OnCreate.class, message = "Department ID is required")
    private UUID departmentId;

    @NotNull(groups = OnCreate.class, message = "Supplier ID is required")
    private UUID supplierId;

    /** Optional: Budget to auto-deduct from when this PO is approved. */
    private UUID linkedBudgetId;

    private Instant createdAt;

    private Instant updatedAt;
}
