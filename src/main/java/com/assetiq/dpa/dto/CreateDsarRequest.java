package com.assetiq.dpa.dto;

import com.assetiq.dpa.model.DsarRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDsarRequest(
        @NotBlank @Email @Size(max = 255) String requesterEmail,
        @NotNull DsarRequest.RequestType requestType,
        String notes
) {}
