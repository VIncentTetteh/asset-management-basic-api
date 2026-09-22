package com.assetiq.dto;

import com.assetiq.enums.OrganisationStatus;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.assetiq.validation.TimeZoneId;
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
    @TimeZoneId
    private String timezone;

    private OrganisationStatus status;

    /**
     * ISO-4217 alpha-3 currency code used as the default for this tenant's
     * money-valued records. See {@code V9__organisation_billing_currency.sql}.
     */
    @Size(max = 3)
    private String billingCurrency;

    /** Data protection officer's name; blank clears it. */
    @Size(max = 255)
    private String dpoName;

    /** Data protection officer's email; blank clears it. */
    @Email(message = "DPO email must be valid")
    @Size(max = 255)
    private String dpoEmail;

    /** Declared data residency; one of {@link #DATA_RESIDENCY_REGIONS}. */
    @Pattern(regexp = "GH|EU|US|OTHER", message = "must be one of GH, EU, US, OTHER")
    @Size(max = 10)
    private String dataResidencyRegion;

    /** The data residency values the API accepts (the web select mirrors them). */
    public static final java.util.List<String> DATA_RESIDENCY_REGIONS = java.util.List.of("GH", "EU", "US", "OTHER");
}
