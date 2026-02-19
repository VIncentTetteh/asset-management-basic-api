package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class DepartmentDto {
    public UUID id;

    @NotBlank
    public String name;
    public UUID organisationId;
}
