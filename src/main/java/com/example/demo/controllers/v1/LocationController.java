package com.example.demo.controllers.v1;

import com.example.demo.dto.LocationDto;
import com.example.demo.services.LocationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping
    public ResponseEntity<LocationDto> createLocation(@Valid @RequestBody LocationDto locationDto,
                                                     @RequestParam UUID organisationId) {
        LocationDto createdLocation = locationService.createLocation(locationDto, organisationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdLocation);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LocationDto> getLocationById(@PathVariable UUID id) {
        LocationDto location = locationService.getLocationById(id);
        return ResponseEntity.ok(location);
    }

    @GetMapping
    public ResponseEntity<Set<LocationDto>> getLocationsByOrganisation(@RequestParam UUID organisationId) {
        Set<LocationDto> locations = locationService.getLocationsByOrganisation(organisationId);
        return ResponseEntity.ok(locations);
    }

    @GetMapping("/{parentId}/sub-locations")
    public ResponseEntity<Set<LocationDto>> getSubLocations(@PathVariable UUID parentId) {
        Set<LocationDto> subLocations = locationService.getSubLocations(parentId);
        return ResponseEntity.ok(subLocations);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LocationDto> updateLocation(@PathVariable UUID id,
                                                     @Valid @RequestBody LocationDto locationDto) {
        LocationDto updatedLocation = locationService.updateLocation(id, locationDto);
        return ResponseEntity.ok(updatedLocation);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLocation(@PathVariable UUID id) {
        locationService.deleteLocation(id);
        return ResponseEntity.noContent().build();
    }
}

