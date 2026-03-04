package com.example.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "location")
public class Location extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String building;

    private String floor;

    private String room;

    private String city;

    private String country;

    @Column(columnDefinition = "TEXT")
    private String geoCoordinates; // Format: "latitude,longitude"

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

