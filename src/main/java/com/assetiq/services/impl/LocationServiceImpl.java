package com.assetiq.services.impl;

import com.assetiq.dto.LocationDto;
import com.assetiq.config.CachingConfig;
import com.assetiq.models.Location;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.LocationRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.LocationService;
import com.assetiq.services.TenantAwareService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LocationServiceImpl extends TenantAwareService implements LocationService {

    private final LocationRepository locationRepository;

    public LocationServiceImpl(LocationRepository locationRepository,
            OrganisationRepository organisationRepository) {
        super(organisationRepository);
        this.locationRepository = locationRepository;
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.LOCATIONS, allEntries = true)
    public LocationDto createLocation(LocationDto locationDto, UUID organisationId) {
        // Always use tenant context, ignore param
        Organisation org = requireTenantOrg();

        Location location = new Location();
        location.setName(locationDto.getName());
        location.setBuilding(locationDto.getBuilding());
        location.setFloor(locationDto.getFloor());
        location.setRoom(locationDto.getRoom());
        location.setCity(locationDto.getCity());
        location.setCountry(locationDto.getCountry());
        location.setAddress(locationDto.getAddress());
        location.setLatitude(locationDto.getLatitude());
        location.setLongitude(locationDto.getLongitude());
        // Keep geoCoordinates in sync if lat/lng provided
        if (locationDto.getLatitude() != null && locationDto.getLongitude() != null) {
            location.setGeoCoordinates(locationDto.getLatitude() + "," + locationDto.getLongitude());
        } else {
            location.setGeoCoordinates(locationDto.getGeoCoordinates());
        }
        location.setOrganisation(org);

        if (locationDto.getParentLocationId() != null) {
            Location parentLocation = locationRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                    locationDto.getParentLocationId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Parent location not found in your organisation"));
            location.setParentLocation(parentLocation);
        }

        return mapToDto(locationRepository.save(location));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CachingConfig.CacheNames.LOCATIONS, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':one:' + #id.toString()")
    public LocationDto getLocationById(UUID id) {
        Organisation org = requireTenantOrg();
        Location location = locationRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Location not found"));
        return mapToDto(location);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CachingConfig.CacheNames.LOCATIONS, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':list'")
    public Set<LocationDto> getLocationsByOrganisation(UUID organisationId) {
        // Always scope to tenant context, ignore param
        Organisation org = requireTenantOrg();
        return locationRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CachingConfig.CacheNames.LOCATIONS, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':children:' + #parentLocationId.toString()")
    public Set<LocationDto> getSubLocations(UUID parentLocationId) {
        Organisation org = requireTenantOrg();
        locationRepository.findByIdAndOrganisationAndDeletedAtIsNull(parentLocationId, org)
                .orElseThrow(() -> new IllegalArgumentException("Parent location not found in your organisation"));
        return locationRepository.findByParentLocationIdAndDeletedAtIsNull(parentLocationId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.LOCATIONS, allEntries = true)
    public LocationDto updateLocation(UUID id, LocationDto locationDto) {
        Organisation org = requireTenantOrg();
        Location location = locationRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Location not found"));

        location.setName(locationDto.getName());
        location.setBuilding(locationDto.getBuilding());
        location.setFloor(locationDto.getFloor());
        location.setRoom(locationDto.getRoom());
        location.setCity(locationDto.getCity());
        location.setCountry(locationDto.getCountry());
        location.setAddress(locationDto.getAddress());
        location.setLatitude(locationDto.getLatitude());
        location.setLongitude(locationDto.getLongitude());
        if (locationDto.getLatitude() != null && locationDto.getLongitude() != null) {
            location.setGeoCoordinates(locationDto.getLatitude() + "," + locationDto.getLongitude());
        } else {
            location.setGeoCoordinates(locationDto.getGeoCoordinates());
        }

        if (locationDto.getParentLocationId() != null) {
            Location parentLocation = locationRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                    locationDto.getParentLocationId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Parent location not found in your organisation"));

            // Prevent self-referencing hierarchy
            if (parentLocation.getId().equals(id)) {
                throw new IllegalArgumentException("A location cannot be its own parent");
            }

            location.setParentLocation(parentLocation);
        } else {
            location.setParentLocation(null);
        }

        return mapToDto(locationRepository.save(location));
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.LOCATIONS, allEntries = true)
    public LocationDto patchLocation(UUID id, LocationDto locationDto) {
        Organisation org = requireTenantOrg();
        Location location = locationRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Location not found"));

        if (locationDto.getName() != null) {
            location.setName(locationDto.getName());
        }
        if (locationDto.getBuilding() != null) {
            location.setBuilding(locationDto.getBuilding());
        }
        if (locationDto.getFloor() != null) {
            location.setFloor(locationDto.getFloor());
        }
        if (locationDto.getRoom() != null) {
            location.setRoom(locationDto.getRoom());
        }
        if (locationDto.getCity() != null) {
            location.setCity(locationDto.getCity());
        }
        if (locationDto.getCountry() != null) {
            location.setCountry(locationDto.getCountry());
        }
        if (locationDto.getAddress() != null) {
            location.setAddress(locationDto.getAddress());
        }
        if (locationDto.getLatitude() != null) {
            location.setLatitude(locationDto.getLatitude());
        }
        if (locationDto.getLongitude() != null) {
            location.setLongitude(locationDto.getLongitude());
        }
        // Sync geoCoordinates from lat/lng when both are present
        if (location.getLatitude() != null && location.getLongitude() != null) {
            location.setGeoCoordinates(location.getLatitude() + "," + location.getLongitude());
        } else if (locationDto.getGeoCoordinates() != null) {
            location.setGeoCoordinates(locationDto.getGeoCoordinates());
        }

        if (locationDto.getParentLocationId() != null) {
            Location parentLocation = locationRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                    locationDto.getParentLocationId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Parent location not found in your organisation"));
            if (parentLocation.getId().equals(id)) {
                throw new IllegalArgumentException("A location cannot be its own parent");
            }
            location.setParentLocation(parentLocation);
        }

        return mapToDto(locationRepository.save(location));
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.LOCATIONS, allEntries = true)
    public void deleteLocation(UUID id) {
        Organisation org = requireTenantOrg();
        Location location = locationRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Location not found"));
        location.setDeletedAt(Instant.now());
        locationRepository.save(location);
    }

    private LocationDto mapToDto(Location location) {
        LocationDto dto = new LocationDto();
        dto.setId(location.getId());
        dto.setName(location.getName());
        dto.setBuilding(location.getBuilding());
        dto.setFloor(location.getFloor());
        dto.setRoom(location.getRoom());
        dto.setCity(location.getCity());
        dto.setCountry(location.getCountry());
        dto.setGeoCoordinates(location.getGeoCoordinates());
        dto.setAddress(location.getAddress());
        dto.setLatitude(location.getLatitude());
        dto.setLongitude(location.getLongitude());
        if (location.getParentLocation() != null) {
            dto.setParentLocationId(location.getParentLocation().getId());
        }
        dto.setOrganisationId(location.getOrganisation().getId());
        dto.setCreatedAt(location.getCreatedAt());
        dto.setUpdatedAt(location.getUpdatedAt());
        return dto;
    }
}
