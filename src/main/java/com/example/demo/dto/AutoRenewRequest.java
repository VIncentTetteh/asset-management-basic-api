package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AutoRenewRequest {
    @NotNull(message = "enabled is required")
    private Boolean enabled;
}

