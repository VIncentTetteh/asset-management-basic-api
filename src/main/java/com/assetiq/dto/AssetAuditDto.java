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

    /** The department's name (read-only); null for an organisation-wide audit. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String departmentName;

    @NotNull(message = "Audit date is required")
    private LocalDate auditDate;

    /** Optional on create: defaults to the current user. */
    private UUID conductedById;

    /** The auditor's display name (read-only). */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String conductedByName;

    /** Initial status on create (PLANNED when omitted); changed later via PATCH /status. */
    private AuditStatus status;

    @Size(max = 5000)
    private String remarks;

    // ── Count-sheet progress (read-only, counted from audit_item) ────────────

    /** How many assets are on this audit's count sheet. Zero means it has none yet. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private long totalItemCount;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private long verifiedItemCount;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private long discrepancyCount;

    /**
     * True only when the sheet exists and every single item on it is verified.
     * This is what the client's "Verified" seal is allowed to key off — an audit
     * with no items, or with one item still pending, is not a verified audit.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean allItemsVerified;
}

