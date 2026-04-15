package com.assetiq.services;

import com.assetiq.dto.LocationDto;
import java.util.Set;
import java.util.UUID;

public interface LocationService {
    LocationDto createLocation(LocationDto locationDto, UUID organisationId);
    LocationDto getLocationById(UUID id);
    Set<LocationDto> getLocationsByOrganisation(UUID organisationId);
    Set<LocationDto> getSubLocations(UUID parentLocationId);
    LocationDto updateLocation(UUID id, LocationDto locationDto);
    LocationDto patchLocation(UUID id, LocationDto locationDto);
    void deleteLocation(UUID id);
}
