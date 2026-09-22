package com.assetiq.dto;

import com.assetiq.enums.UserStatus;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.Email;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;


import java.util.UUID;

@Data
public class UserDto {
    private UUID id;

    @NotBlank(groups = OnCreate.class, message = "First name is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String firstName;

    @NotBlank(groups = OnCreate.class, message = "Last name is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String lastName;

    @Email(message = "Email must be valid")
    @NotBlank(groups = OnCreate.class, message = "Email is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String phone;

    /**
     * Write-only. Required when creating a user (checked in the service); ignored
     * on PUT/PATCH, so edits no longer fail validation for want of a password.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @com.assetiq.validation.ValidPassword
    private String password;

    @Size(max = 255)
    private String employeeId;

    @Size(max = 255)
    private String jobTitle;

    private UUID roleId;

    private UserStatus status;

    private UUID organisationId;

    private UUID departmentId;

    /** Whether TOTP-based MFA is currently active for this user. Read-only in most contexts. */
    private Boolean mfaEnabled;
}

