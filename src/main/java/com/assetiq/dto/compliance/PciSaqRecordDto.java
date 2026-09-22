package com.assetiq.dto.compliance;

import com.assetiq.validation.HttpUrl;
import com.assetiq.models.compliance.PciSaqRecord;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class PciSaqRecordDto {

    private UUID id;
    private UUID organisationId;

    @NotBlank(groups = OnCreate.class, message = "Requirement number is required")
    @NullOrNotBlank
    @Size(max = 16)
    private String requirementNumber;

    private String requirementText;
    private PciSaqRecord.ComplianceAnswer complianceStatus;
    private String compensatingControl;
    @Size(max = 255)
    @HttpUrl
    private String evidenceUrl;
    private Instant targetDate;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
