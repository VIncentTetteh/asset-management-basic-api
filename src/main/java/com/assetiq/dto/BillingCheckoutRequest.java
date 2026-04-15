package com.assetiq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BillingCheckoutRequest {
    @NotBlank(message = "Plan code is required")
    private String planCode;

    private String callbackUrl;
}
