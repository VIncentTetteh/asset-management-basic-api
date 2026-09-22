package com.assetiq.dto.compliance;

import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class SlaMetricDto {

    private UUID id;
    private UUID organisationId;

    @NotNull(groups = OnCreate.class, message = "Month is required")
    @Min(1) @Max(12)
    private Integer month;

    @NotNull(groups = OnCreate.class, message = "Year is required")
    @Min(2000)
    @Max(2100)
    private Integer year;

    @NotNull(groups = OnCreate.class, message = "Uptime percent is required")
    @DecimalMin("0")
    @DecimalMax("100")
    private Double uptimePercent;

    @PositiveOrZero
    private Integer plannedDowntimeMinutes;
    @PositiveOrZero
    private Integer unplannedDowntimeMinutes;
    @PositiveOrZero
    private Integer incidentCount;
    @PositiveOrZero
    private Integer rtoMinutes;
    @PositiveOrZero
    private Integer rpoMinutes;
    private Boolean slaBreached;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
