package com.assetiq.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** A complimentary plan period granted by a platform operator (no payment). */
@Data
public class PlanGrantRequest {
    @NotBlank
    private String planCode;

    @Min(1)
    @Max(366)
    private int days;

    @NotBlank
    @Size(max = 300)
    private String reason;
}
