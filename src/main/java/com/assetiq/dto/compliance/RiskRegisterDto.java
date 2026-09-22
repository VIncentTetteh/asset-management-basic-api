package com.assetiq.dto.compliance;

import com.assetiq.models.compliance.ComplianceFramework;
import com.assetiq.models.compliance.RiskRegister;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class RiskRegisterDto {

    private UUID id;
    private UUID organisationId;
    private ComplianceFramework framework;
    @Size(max = 32)
    private String riskId;

    @NotBlank(groups = OnCreate.class, message = "Title is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String title;

    private String description;

    @NotNull(groups = OnCreate.class, message = "Likelihood is required")
    @Min(1) @Max(5)
    private Integer likelihood;

    @NotNull(groups = OnCreate.class, message = "Impact is required")
    @Min(1) @Max(5)
    private Integer impact;

    private Integer riskScore;
    private RiskRegister.RiskTreatment treatment;
    private String mitigationPlan;
    @Min(1)
    @Max(25)
    private Integer residualRisk;
    private RiskRegister.RiskStatus status;
    private UUID ownerId;
    private String ownerEmail;
    private Instant reviewDate;
    private Instant createdAt;
    private Instant updatedAt;
}
