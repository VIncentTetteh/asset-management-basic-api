package com.assetiq.dto;

import com.assetiq.validation.HttpUrl;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "Title is required")
    @Size(max = 255)
    private String title;
    private String description;
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal amount;
    @Size(max = 3)
    private String currency;
    @NotNull(message = "Category is required")
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
    @Size(max = 500)
    @HttpUrl
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
