package com.example.demo.dto.compliance;

import com.example.demo.models.compliance.PatchRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class PatchRecordDto {

    private UUID id;
    private UUID organisationId;

    @NotNull(message = "Asset ID is required")
    private UUID assetId;

    private String assetName;

    @NotBlank(message = "Patch name is required")
    private String patchName;

    private String version;
    private Instant appliedAt;
    private String appliedByEmail;
    private Boolean testEnvironmentValidated;
    private String rollbackPlan;
    private PatchRecord.PatchStatus status;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
