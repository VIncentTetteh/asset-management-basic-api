package com.assetiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /disposals/{id}/reject: why the disposal is refused or withdrawn. */
public record DisposalRejectRequest(
        @NotBlank(message = "A rejection reason is required")
        @Size(max = 5000)
        String reason) {
}
