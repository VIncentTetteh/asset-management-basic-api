package com.assetiq.dto;

import com.assetiq.enums.OrganisationStatus;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;


import java.util.UUID;

@Data
public class OrganisationDto {
    private UUID id;

    @NotBlank(groups = OnCreate.class, message = "Organization name is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String registrationNumber;

    @Size(max = 255)
    private String taxId;

    @Size(max = 255)
    private String industry;

    @Size(max = 255)
    private String country;

    private String address;

    @Email(message = "Contact email must be valid")
    @Size(max = 255)
    private String contactEmail;

    @Size(max = 255)
    private String contactPhone;

    @Size(max = 255)
    private String timezone;

    private OrganisationStatus status;

    /**
     * ISO-4217 alpha-3 currency code used as the default for this tenant's
     * money-valued records. See {@code V9__organisation_billing_currency.sql}.
     */
    @Size(max = 3)
    private String billingCurrency;
}
