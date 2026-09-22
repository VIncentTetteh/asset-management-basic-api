package com.assetiq.dto;

import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class RoleDto {
    private UUID id;

    @NotBlank(groups = OnCreate.class, message = "Role name is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String name;

    private String description;

    /** Flat list of permission names, e.g. ["VIEW_ASSETS", "MANAGE_ROLES"]. */
    private List<String> permissions;

    private UUID organisationId;

    /** Read-only: true if this is a built-in role that cannot be modified or deleted. */
    private Boolean systemRole;

    /**
     * When true the bearer receives every Permission enum value.
     * Only a SYSTEM_ADMIN may set this on role creation; org admins cannot.
     */
    private Boolean grantAllPermissions;
}

