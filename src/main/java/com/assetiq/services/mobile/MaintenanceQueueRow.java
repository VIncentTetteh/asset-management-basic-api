package com.assetiq.services.mobile;

import com.assetiq.enums.MaintenanceStatus;
import com.assetiq.enums.MaintenanceType;

import java.time.LocalDate;
import java.util.UUID;

/** An overdue maintenance job as the Home queue needs it; built by a JPQL constructor expression. */
public record MaintenanceQueueRow(UUID id, UUID assetId, String assetName, MaintenanceType maintenanceType,
                                  MaintenanceStatus status, LocalDate nextDueDate) {
}
