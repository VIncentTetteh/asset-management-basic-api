package com.assetiq.dto;

import com.assetiq.enums.SupplierStatus;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;


import java.time.Instant;
import java.util.UUID;

@Data
public class SupplierDto {
    private UUID id;

    @NotBlank(groups = OnCreate.class, message = "Supplier name is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String registrationNumber;

    @Size(max = 255)
    private String contactPerson;

    @Email(message = "Email must be valid")
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String phone;

    private String address;

    @Size(max = 255)
    private String taxId;

    private SupplierStatus status;

    private UUID organisationId;

    private Instant createdAt;

    private Instant updatedAt;
}
