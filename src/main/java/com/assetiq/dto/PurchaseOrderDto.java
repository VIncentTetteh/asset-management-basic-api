package com.assetiq.dto;

import com.assetiq.enums.POStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class PurchaseOrderDto {
    private UUID id;

    @NotBlank(message = "PO number is required")
    private String poNumber;

    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.01", message = "Total amount must be greater than zero")
    private BigDecimal totalAmount;

    private String currency;

    private POStatus status;

    private UUID approvedById;

    private UUID requestedById;

    private UUID rejectedById;

    private Instant approvedAt;

    private Instant rejectedAt;

    private String remarks;

    private UUID organisationId;

    @NotNull(message = "Department ID is required")
    private UUID departmentId;

    @NotNull(message = "Supplier ID is required")
    private UUID supplierId;

    /** Optional: Budget to auto-deduct from when this PO is approved. */
    private UUID linkedBudgetId;

    private Instant createdAt;

    private Instant updatedAt;
}
