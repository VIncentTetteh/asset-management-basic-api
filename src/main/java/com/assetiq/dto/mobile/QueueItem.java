package com.assetiq.dto.mobile;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One thing on the caller's Home "to do" queue.
 *
 * @param kind     {@link #KIND_MAINTENANCE}, {@link #KIND_TRANSFER_APPROVAL} or {@link #KIND_CHECKOUT}
 * @param id       the maintenance record, transfer or checkout record id
 * @param assetId  the asset it concerns
 * @param title    the asset's name
 * @param subtitle short human context: the maintenance type, who requested the
 *                 transfer, or who has the asset
 * @param status   the record's own status (e.g. SCHEDULED, REQUESTED, ACTIVE)
 * @param dueAt    next due date or expected return date; null for approvals,
 *                 which have no due date
 * @param overdue  true when {@code dueAt} has passed
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record QueueItem(
        String kind,
        UUID id,
        UUID assetId,
        String title,
        String subtitle,
        String status,
        LocalDate dueAt,
        boolean overdue) {

    public static final String KIND_MAINTENANCE = "MAINTENANCE";
    public static final String KIND_TRANSFER_APPROVAL = "TRANSFER_APPROVAL";
    public static final String KIND_CHECKOUT = "CHECKOUT";
}
