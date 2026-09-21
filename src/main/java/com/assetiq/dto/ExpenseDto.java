package com.assetiq.dto;

import com.assetiq.enums.ExpenseCategory;
import com.assetiq.enums.ExpenseStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID submittedById;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String submittedByName;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID approvedById;

    /** Actual date the expense was incurred (distinct from createdAt submission date). */
    private LocalDate expenseDate;

    /** Budget name resolved server-side — avoids a second round-trip on the frontend. */
    private String linkedBudgetName;

    private Instant approvedAt;
    private String rejectionReason;
    private String receiptUrl;
    private UUID linkedAssetId;
    private UUID linkedBudgetId;
    private UUID departmentId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID organisationId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private ExpenseStatus status;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant createdAt;
}
