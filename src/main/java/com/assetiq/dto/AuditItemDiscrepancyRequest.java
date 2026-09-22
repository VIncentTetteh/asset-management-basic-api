package com.assetiq.dto;

import com.assetiq.enums.AuditDiscrepancyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Flags one audit item as wrong. Both the kind of problem and a reason are
 * required: a bare flag with no explanation is what the old discrepancy_flag
 * column was, and it told nobody anything.
 */
public record AuditItemDiscrepancyRequest(
        @NotNull(message = "A discrepancy type is required")
        AuditDiscrepancyType discrepancyType,

        @NotBlank(message = "A reason is required")
        @Size(max = 5000)
        String reason,

        @Size(max = 255)
        String actualLocation
) {}
