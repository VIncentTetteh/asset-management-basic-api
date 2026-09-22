package com.assetiq.dto;

import com.assetiq.enums.UserStatus;
import jakarta.validation.constraints.Email;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;


import java.util.UUID;

@Data
public class UserDto {
    private UUID id;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @Email(message = "Email must be valid")
    @NotBlank(message = "Email is required")
    private String email;

    private String phone;

    /**
     * Write-only. Required when creating a user (checked in the service); ignored
     * on PUT/PATCH, so edits no longer fail validation for want of a password.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size(min = 8, max = 128, message = "Password must be 8 to 128 characters")
    private String password;

    private String employeeId;

    private String jobTitle;

    private UUID roleId;

    private UserStatus status;

    private UUID organisationId;

    private UUID departmentId;

    /** Whether TOTP-based MFA is currently active for this user. Read-only in most contexts. */
    private Boolean mfaEnabled;
}

