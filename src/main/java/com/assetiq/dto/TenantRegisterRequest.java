package com.assetiq.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.assetiq.validation.TimeZoneId;
import lombok.Data;


@Data
public class TenantRegisterRequest {
    // Organisation fields
    @NotBlank(message = "Organisation name is required")
    @Size(max = 255)
    private String organisationName;

    @Email(message = "Contact email must be valid")
    @Size(max = 255)
    private String organisationContactEmail;

    @Size(max = 255)
    private String country;
    private String address;
    @Size(max = 255)
    @TimeZoneId
    private String timezone;
    @Size(max = 255)
    private String industry;
    @Size(max = 255)
    private String registrationNumber;
    @Size(max = 255)
    private String taxId;
    @Size(max = 255)
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
    @com.assetiq.validation.ValidPassword
    private String password;
}
