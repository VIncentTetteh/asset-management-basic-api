package com.assetiq.dto;

import jakarta.validation.constraints.Size;

/** Body of PATCH /audits/{id}: the audit's remarks; null or blank clears them. */
public record AuditRemarksRequest(@Size(max = 5000) String remarks) {
}
