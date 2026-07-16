package com.assetiq.dpa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateConsentRequest(
        @NotBlank @Size(max = 100) String purpose,
        boolean granted,
        String ipAddress,
        String userAgent
) {}
