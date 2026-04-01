package com.example.demo.dto;

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
public class TcoDto {
    private UUID assetId;
    private String assetName;
    private String assetTag;
    private BigDecimal acquisitionCost;
    private BigDecimal totalMaintenanceCost;
    private BigDecimal totalInsuranceCost;
    private BigDecimal totalDowntimeCost;
    private BigDecimal disposalRecovery;
    private BigDecimal netTco;
    private String currency;
    private Instant calculatedAt;
    private Integer maintenanceRecordCount;
    private Long downtimeDays;
}
