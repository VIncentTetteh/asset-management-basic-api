package com.assetiq.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;



import java.util.Set;

@Entity
@Table(name = "location")
@Data
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Location extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String building;

    private String floor;

    private String room;

    private String city;

    private String country;

    @Column(columnDefinition = "TEXT")
    private String geoCoordinates; // Format: "latitude,longitude" (legacy — prefer latitude/longitude fields)

    private Double latitude;

    private Double longitude;

    @Column(columnDefinition = "TEXT")
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_location_id")
    private Location parentLocation;

    @OneToMany(mappedBy = "parentLocation")
    private Set<Location> subLocations;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Organisation organisation;

    @OneToMany(mappedBy = "location")
    private Set<Asset> assets;

}

