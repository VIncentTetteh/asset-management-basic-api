package com.assetiq.dto.compliance;

import com.assetiq.models.compliance.PatchRecord;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class PatchRecordDto {

    private UUID id;
    private UUID organisationId;

    /** Fixed at create; ignored by PATCH and PUT. */
    @NotNull(groups = OnCreate.class, message = "Asset ID is required")
    private UUID assetId;

    private String assetName;

    @NotBlank(groups = OnCreate.class, message = "Patch name is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String patchName;

    @Size(max = 64)
    private String version;
    private Instant appliedAt;
    /** Read-only: stamped from the signed-in user when the patch is recorded as APPLIED. */
    @Size(max = 255)
    private String appliedByEmail;
    private Boolean testEnvironmentValidated;
    private String rollbackPlan;
    private PatchRecord.PatchStatus status;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
