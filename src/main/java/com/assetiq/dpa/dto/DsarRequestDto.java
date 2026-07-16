package com.assetiq.dpa.dto;

import com.assetiq.dpa.model.DsarRequest;

import java.time.Instant;
import java.util.UUID;

public record DsarRequestDto(
        UUID id,
        String requesterEmail,
        DsarRequest.RequestType requestType,
        DsarRequest.Status status,
        Instant submittedAt,
        Instant dueAt,
        Instant completedAt,
        UUID assignedToUserId,
        String notes,
        String responseSummary
) {}
