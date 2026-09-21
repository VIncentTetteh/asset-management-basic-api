package com.assetiq.dto;

import com.assetiq.enums.AuditStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;


import java.time.LocalDate;
import java.util.UUID;

@Data
public class AssetAuditDto {
    private UUID id;

    /** Read-only: always the caller's tenant. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID organisationId;

    /** Optional: null scopes the audit to the whole organisation. */
    private UUID departmentId;

    @NotNull(message = "Audit date is required")
    private LocalDate auditDate;

    /** Optional on create: defaults to the current user. */
    private UUID conductedById;

    /** Initial status on create (PLANNED when omitted); changed later via PATCH /status. */
    private AuditStatus status;

    @Size(max = 5000)
    private String remarks;
}

