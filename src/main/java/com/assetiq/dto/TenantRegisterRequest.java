package com.assetiq.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class TenantRegisterRequest {
    // Organisation fields
    @NotBlank(message = "Organisation name is required")
    private String organisationName;

    @Email(message = "Contact email must be valid")
    private String organisationContactEmail;

    private String country;
    private String address;
    private String timezone;
    private String industry;
    private String registrationNumber;
    private String taxId;
    private String contactPhone;

    // Initial admin user fields
    @NotBlank(message = "Admin first name is required")
    private String adminFirstName;

    @NotBlank(message = "Admin last name is required")
    private String adminLastName;

    @NotBlank(message = "Admin email is required")
    @Email(message = "Admin email must be valid")
    private String adminEmail;

    private String adminPhone;
    private String adminJobTitle;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
