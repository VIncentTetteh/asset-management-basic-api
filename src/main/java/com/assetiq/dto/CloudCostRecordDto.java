package com.assetiq.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One recorded monthly cost of a cloud asset (read-only view of a cost record). */
@Data
public class CloudCostRecordDto {
    private UUID id;
    /** Billing month as {@code YYYY-MM}. */
    private String billingMonth;
    private BigDecimal amount;
    private String currency;
    /** Sub-service (e.g. "EC2 Compute"), or null for the asset as a whole. */
    private String serviceName;
    private Instant createdAt;
    private Instant updatedAt;
}
