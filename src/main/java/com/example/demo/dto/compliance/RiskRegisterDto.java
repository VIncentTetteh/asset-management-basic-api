package com.example.demo.dto.compliance;

import com.example.demo.models.compliance.ComplianceFramework;
import com.example.demo.models.compliance.RiskRegister;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class RiskRegisterDto {

    private UUID id;
    private UUID organisationId;
    private ComplianceFramework framework;
    private String riskId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Likelihood is required")
    @Min(1) @Max(5)
    private Integer likelihood;

    @NotNull(message = "Impact is required")
    @Min(1) @Max(5)
    private Integer impact;

    private Integer riskScore;
    private RiskRegister.RiskTreatment treatment;
    private String mitigationPlan;
    private Integer residualRisk;
    private RiskRegister.RiskStatus status;
    private UUID ownerId;
    private String ownerEmail;
    private Instant reviewDate;
    private Instant createdAt;
    private Instant updatedAt;
}
