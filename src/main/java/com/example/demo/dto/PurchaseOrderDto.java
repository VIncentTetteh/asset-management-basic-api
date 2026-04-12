package com.example.demo.dto;

import com.example.demo.enums.POStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private BigDecimal totalAmount;

    private String currency;

    private POStatus status;

    private UUID approvedById;

    private String remarks;

    @NotNull(message = "Organisation ID is required")
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

