package com.assetiq.dto.compliance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class SlaMetricDto {

    private UUID id;
    private UUID organisationId;

    @NotNull(message = "Month is required")
    @Min(1) @Max(12)
    private Integer month;

    @NotNull(message = "Year is required")
    private Integer year;

    @NotNull(message = "Uptime percent is required")
    private Double uptimePercent;

    private Integer plannedDowntimeMinutes;
    private Integer unplannedDowntimeMinutes;
    private Integer incidentCount;
    private Integer rtoMinutes;
    private Integer rpoMinutes;
    private Boolean slaBreached;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
