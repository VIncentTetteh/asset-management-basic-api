package com.assetiq.dto;

import com.assetiq.enums.OrganisationStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;


import java.util.UUID;

@Data
public class OrganisationDto {
    private UUID id;

    @NotBlank(message = "Organization name is required")
    private String name;

    private String registrationNumber;

    private String taxId;

    private String industry;

    private String country;

    private String address;

    @Email(message = "Contact email must be valid")
    private String contactEmail;

    private String contactPhone;

    private String timezone;

    private OrganisationStatus status;
}
