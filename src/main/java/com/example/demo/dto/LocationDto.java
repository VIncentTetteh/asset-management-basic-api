package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class LocationDto {
    private UUID id;

    @NotBlank(message = "Location name is required")
    private String name;

    private String building;

    private String floor;

    private String room;

    private String city;

    private String country;

    private String geoCoordinates;

    private UUID parentLocationId;

    private UUID organisationId;
}

