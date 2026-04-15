package com.assetiq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


import java.util.UUID;

@Data
public class RoleDto {
    private UUID id;

    @NotBlank(message = "Role name is required")
    private String name;

    private String description;

    private String permissions; // JSON format

    private UUID organisationId;
}

