package com.example.demo.services.impl;

import com.example.demo.dto.LocationDto;
import com.example.demo.models.Location;
import com.example.demo.models.Organisation;
import com.example.demo.repositories.LocationRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.LocationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LocationServiceImpl implements LocationService {

    private final LocationRepository locationRepository;
    private final OrganisationRepository organisationRepository;

    public LocationServiceImpl(LocationRepository locationRepository, OrganisationRepository organisationRepository) {
        this.locationRepository = locationRepository;
        this.organisationRepository = organisationRepository;
    }

    @Override
    public LocationDto createLocation(LocationDto locationDto, UUID organisationId) {
        Organisation organisation = organisationRepository.findById(organisationId)
            .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));

        Location location = new Location();
        location.setName(locationDto.getName());
        location.setBuilding(locationDto.getBuilding());
        location.setFloor(locationDto.getFloor());
        location.setRoom(locationDto.getRoom());
        location.setCity(locationDto.getCity());
        location.setCountry(locationDto.getCountry());
        location.setGeoCoordinates(locationDto.getGeoCoordinates());
        location.setOrganisation(organisation);

        if (locationDto.getParentLocationId() != null) {
            Location parentLocation = locationRepository.findById(locationDto.getParentLocationId())
                .orElseThrow(() -> new IllegalArgumentException("Parent location not found"));
            location.setParentLocation(parentLocation);
        }

        Location savedLocation = locationRepository.save(location);
        return mapToDto(savedLocation);
    }

    @Override
    @Transactional(readOnly = true)
    public LocationDto getLocationById(UUID id) {
        Location location = locationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Location not found"));
        return mapToDto(location);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<LocationDto> getLocationsByOrganisation(UUID organisationId) {
        return locationRepository.findByOrganisationId(organisationId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<LocationDto> getSubLocations(UUID parentLocationId) {
        return locationRepository.findByParentLocationId(parentLocationId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public LocationDto updateLocation(UUID id, LocationDto locationDto) {
        Location location = locationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Location not found"));

        location.setName(locationDto.getName());
        location.setBuilding(locationDto.getBuilding());
        location.setFloor(locationDto.getFloor());
        location.setRoom(locationDto.getRoom());
        location.setCity(locationDto.getCity());
        location.setCountry(locationDto.getCountry());
        location.setGeoCoordinates(locationDto.getGeoCoordinates());

        Location updatedLocation = locationRepository.save(location);
        return mapToDto(updatedLocation);
    }

    @Override
    public void deleteLocation(UUID id) {
        locationRepository.deleteById(id);
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
        if (location.getParentLocation() != null) {
            dto.setParentLocationId(location.getParentLocation().getId());
        }
        dto.setOrganisationId(location.getOrganisation().getId());
        return dto;
    }
}

