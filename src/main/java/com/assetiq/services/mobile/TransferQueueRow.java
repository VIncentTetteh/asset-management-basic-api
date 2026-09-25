package com.assetiq.services.mobile;

import com.assetiq.enums.TransferStatus;

import java.time.Instant;
import java.util.UUID;

/** A transfer awaiting approval as the Home queue needs it; built by a JPQL constructor expression. */
public record TransferQueueRow(UUID id, UUID assetId, String assetName, String requesterFirstName,
                               String requesterLastName, String requesterEmail, TransferStatus status,
                               Instant requestedAt) {
}
