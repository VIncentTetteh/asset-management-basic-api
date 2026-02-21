package com.example.demo.dto;

import com.example.demo.enums.SupplierStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class SupplierDto {
    private UUID id;

    @NotBlank(message = "Supplier name is required")
    private String name;

    private String registrationNumber;

    private String contactPerson;

    @Email(message = "Email must be valid")
    private String email;

    private String phone;

    private String address;

    private String bankDetails;

    private String taxId;

    private SupplierStatus status;

    private UUID organisationId;
}

