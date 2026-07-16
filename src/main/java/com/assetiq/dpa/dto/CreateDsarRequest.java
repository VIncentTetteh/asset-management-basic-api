package com.assetiq.dpa.dto;

import com.assetiq.dpa.model.DsarRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDsarRequest(
        @NotBlank @Email String requesterEmail,
        @NotNull DsarRequest.RequestType requestType,
        String notes
) {}
