package com.assetiq.dto.compliance;

import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class SecurityZoneDto {

    private UUID id;
    private UUID organisationId;

    @NotBlank(groups = OnCreate.class, message = "Name is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String name;

    @NotNull(groups = OnCreate.class, message = "Purdue level is required")
    @Min(0) @Max(5)
    private Integer purdueLevel;

    private String description;
    @Size(max = 255)
    private String allowedProtocols;
    @PositiveOrZero
    private Integer assetCount;
    @Size(max = 255)
    private String networkRange;
    private Instant createdAt;
    private Instant updatedAt;
}
