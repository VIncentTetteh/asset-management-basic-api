package com.assetiq.dto;

import com.assetiq.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "Password is required")
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

