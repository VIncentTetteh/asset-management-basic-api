package com.assetiq.dpa.dto;

import java.time.Instant;
import java.util.UUID;

public record ConsentRecordDto(
        UUID id,
        UUID userId,
        String purpose,
        boolean granted,
        Instant grantedAt,
        Instant revokedAt,
        Instant createdAt
) {}
