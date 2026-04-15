package com.assetiq.dto.compliance;

import com.assetiq.models.compliance.PciSaqRecord;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class PciSaqRecordDto {

    private UUID id;
    private UUID organisationId;

    @NotBlank(message = "Requirement number is required")
    private String requirementNumber;

    private String requirementText;
    private PciSaqRecord.ComplianceAnswer complianceStatus;
    private String compensatingControl;
    private String evidenceUrl;
    private Instant targetDate;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
