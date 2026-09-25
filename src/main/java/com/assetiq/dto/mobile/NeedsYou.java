package com.assetiq.dto.mobile;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Items waiting on the caller. Each nullable count is null when the caller may
 * not read that area; {@code total} sums only the parts that are present.
 *
 * @param total                    sum of every non-null count below, notifications included
 * @param overdueMaintenance       open maintenance jobs past their next due date
 * @param pendingTransferApprovals REQUESTED transfers the caller could approve
 *                                 (requested by someone else)
 * @param overdueCheckouts         ACTIVE checkouts past their expected return date
 * @param unreadNotifications      the caller's own unread notifications
 */
// ALWAYS overrides the app-wide non_null inclusion: a withheld section must
// reach the client as an explicit null, not an absent key.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record NeedsYou(
        int total,
        Integer overdueMaintenance,
        Integer pendingTransferApprovals,
        Integer overdueCheckouts,
        long unreadNotifications) {
}
