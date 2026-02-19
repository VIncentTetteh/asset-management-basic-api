package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class OrganisationDto {
    public UUID id;

    @NotBlank
    public String name;
}
