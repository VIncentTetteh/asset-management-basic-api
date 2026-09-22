package com.assetiq.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import com.assetiq.enums.LeaseStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
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
public class LeaseRecordDto {
    private UUID id;
    @NotNull(message = "Asset is required")
    private UUID assetId;
    private String assetName;
    @NotNull(message = "Lessor is required")
    private UUID lessorId;
    private String lessorName;
    @NotNull(message = "Start date is required")
    private LocalDate startDate;
    @NotNull(message = "End date is required")
    private LocalDate endDate;
    @NotNull(message = "Monthly payment is required")
    @DecimalMin(value = "0.01", message = "Monthly payment must be greater than zero")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal monthlyPayment;
    @Size(max = 3)
    private String currency;
    private Boolean autoRenew;
    @PositiveOrZero
    private Integer noticePeriodDays;
    private String notes;
    private UUID organisationId;
    private UUID departmentId;
    private LeaseStatus status;
    private Instant createdAt;
}
