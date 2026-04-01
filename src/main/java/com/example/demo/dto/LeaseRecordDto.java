package com.example.demo.dto;

import com.example.demo.enums.LeaseStatus;
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
    private UUID assetId;
    private String assetName;
    private UUID lessorId;
    private String lessorName;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal monthlyPayment;
    private String currency;
    private Boolean autoRenew;
    private Integer noticePeriodDays;
    private String notes;
    private UUID organisationId;
    private UUID departmentId;
    private LeaseStatus status;
    private Instant createdAt;
}
