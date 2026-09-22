package com.assetiq.dto;

import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;


import java.time.Instant;
import java.util.UUID;

@Data
public class LocationDto {
    private UUID id;

    @NotBlank(groups = OnCreate.class, message = "Location name is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String building;

    @Size(max = 255)
    private String floor;

    @Size(max = 255)
    private String room;

    @Size(max = 255)
    private String city;

    @Size(max = 255)
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

