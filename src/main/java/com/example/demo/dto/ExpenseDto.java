package com.example.demo.dto;

import com.example.demo.enums.ExpenseCategory;
import com.example.demo.enums.ExpenseStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExpenseDto {
    private UUID id;
    private String title;
    private String description;
    private BigDecimal amount;
    private String currency;
    private ExpenseCategory category;
    private UUID submittedById;
    private String submittedByName;
    private UUID approvedById;
    private Instant approvedAt;
    private String rejectionReason;
    private String receiptUrl;
    private UUID linkedAssetId;
    private UUID linkedBudgetId;
    private UUID departmentId;
    private UUID organisationId;
    private ExpenseStatus status;
    private Instant createdAt;
}
