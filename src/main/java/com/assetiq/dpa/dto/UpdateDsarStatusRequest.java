package com.assetiq.dpa.dto;

import com.assetiq.dpa.model.DsarRequest;

import java.util.UUID;

/**
 * Body of PATCH /dpa/dsar/{id}/status. The response summary can hold personal
 * data, so it travels in the body rather than the URL (where access logs and
 * proxies keep it).
 *
 * @param status           the new status (required here or as the legacy query parameter)
 * @param responseSummary  null leaves it unchanged; "" clears it
 * @param assignedToUserId a user of the organisation to assign, or null to leave unchanged
 * @param clearAssignee    true removes the assignee
 */
public record UpdateDsarStatusRequest(
        DsarRequest.Status status,
        String responseSummary,
        UUID assignedToUserId,
        Boolean clearAssignee
) {}
