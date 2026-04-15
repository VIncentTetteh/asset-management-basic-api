package com.assetiq.controllers.v1;

import com.assetiq.dto.LocationDto;
import com.assetiq.services.LocationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_LOCATIONS')")
    public ResponseEntity<LocationDto> createLocation(@Valid @RequestBody LocationDto locationDto) {
        LocationDto createdLocation = locationService.createLocation(locationDto, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdLocation);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_LOCATIONS','MANAGE_LOCATIONS')")
    public ResponseEntity<LocationDto> getLocationById(@PathVariable UUID id) {
        LocationDto location = locationService.getLocationById(id);
        return ResponseEntity.ok(location);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_LOCATIONS','MANAGE_LOCATIONS')")
    public ResponseEntity<Set<LocationDto>> getLocationsByOrganisation() {
        // organisationId ignored — derived from TenantContext in service
        Set<LocationDto> locations = locationService.getLocationsByOrganisation(null);
        return ResponseEntity.ok(locations);
    }

    @GetMapping("/{parentId}/sub-locations")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_LOCATIONS','MANAGE_LOCATIONS')")
    public ResponseEntity<Set<LocationDto>> getSubLocations(@PathVariable UUID parentId) {
        Set<LocationDto> subLocations = locationService.getSubLocations(parentId);
        return ResponseEntity.ok(subLocations);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_LOCATIONS')")
    public ResponseEntity<LocationDto> updateLocation(@PathVariable UUID id,
            @Valid @RequestBody LocationDto locationDto) {
        LocationDto updatedLocation = locationService.updateLocation(id, locationDto);
        return ResponseEntity.ok(updatedLocation);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_LOCATIONS')")
    public ResponseEntity<LocationDto> patchLocation(@PathVariable UUID id,
            @RequestBody LocationDto locationDto) {
        LocationDto updatedLocation = locationService.patchLocation(id, locationDto);
        return ResponseEntity.ok(updatedLocation);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_LOCATIONS')")
    public ResponseEntity<Void> deleteLocation(@PathVariable UUID id) {
        locationService.deleteLocation(id);
        return ResponseEntity.noContent().build();
    }
}
