package com.assetiq.services.impl;

import com.assetiq.models.Location;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.LocationRepository;
import com.assetiq.repositories.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** A location that still holds assets cannot be deleted out from under them. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocationServiceImplDeleteGuardTest {

    @Mock LocationRepository locationRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock AssetRepository assetRepository;

    private LocationServiceImpl service;
    private Location location;

    @BeforeEach
    void setUp() {
        service = new LocationServiceImpl(locationRepository, organisationRepository, assetRepository);
        Organisation org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        location = new Location();
        location.setId(UUID.randomUUID());
        location.setName("Accra Warehouse");
        location.setOrganisation(org);
        when(locationRepository.findByIdAndOrganisationAndDeletedAtIsNull(location.getId(), org))
                .thenReturn(Optional.of(location));
        when(locationRepository.findByParentLocationIdAndDeletedAtIsNull(location.getId())).thenReturn(Set.of());
        when(locationRepository.save(ArgumentMatchers.any(Location.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void refusesToDeleteALocationThatStillHoldsAssets() {
        when(assetRepository.countByLocationIdAndDeletedAtIsNull(location.getId())).thenReturn(3L);

        assertThatThrownBy(() -> service.deleteLocation(location.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 assets");
        assertThat(location.getDeletedAt()).isNull();
    }

    @Test
    void anEmptyLocationIsDeleted() {
        when(assetRepository.countByLocationIdAndDeletedAtIsNull(location.getId())).thenReturn(0L);

        assertThatCode(() -> service.deleteLocation(location.getId())).doesNotThrowAnyException();
        assertThat(location.getDeletedAt()).isNotNull();
    }
}
