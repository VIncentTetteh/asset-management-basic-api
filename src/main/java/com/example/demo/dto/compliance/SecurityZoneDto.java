package com.example.demo.dto.compliance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class SecurityZoneDto {

    private UUID id;
    private UUID organisationId;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Purdue level is required")
    @Min(0) @Max(5)
    private Integer purdueLevel;

    private String description;
    private String allowedProtocols;
    private Integer assetCount;
    private String networkRange;
    private Instant createdAt;
    private Instant updatedAt;
}
