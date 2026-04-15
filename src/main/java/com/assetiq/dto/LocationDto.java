package com.assetiq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


import java.time.Instant;
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

    private Double latitude;

    private Double longitude;

    private String address;

    private UUID parentLocationId;

    private UUID organisationId;

    private Instant createdAt;

    private Instant updatedAt;
}

