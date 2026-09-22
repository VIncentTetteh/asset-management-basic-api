package com.assetiq.services.impl;

import com.assetiq.dto.NetworkScanRequestDto;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.DiscoveredDeviceRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.AssetService;
import com.assetiq.services.FeatureDisabledException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A scan is made by the server itself, so on the hosted service it would reach
 * the platform's own private network. It is off unless a deployment turns it on.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Network discovery is off unless the deployment enables it")
class NetworkDiscoveryGateTest {

    @Mock OrganisationRepository organisationRepository;
    @Mock DiscoveredDeviceRepository deviceRepo;
    @Mock AssetService assetService;

    private NetworkDiscoveryServiceImpl service;
    private Organisation org;

    @BeforeEach
    void setUp() {
        service = new NetworkDiscoveryServiceImpl(organisationRepository, deviceRepo, assetService);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        when(deviceRepo.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NetworkScanRequestDto scanOfLocalRange() {
        NetworkScanRequestDto request = new NetworkScanRequestDto();
        request.setIpAddresses(List.of("10.0.0.5"));
        return request;
    }

    @Test
    void scanningIsRefusedWhenDiscoveryIsDisabled() {
        service.setDiscoveryEnabled(false);

        assertThatThrownBy(() -> service.scan(scanOfLocalRange()))
                .isInstanceOf(FeatureDisabledException.class);
        // Nothing is probed and nothing is written.
        verify(deviceRepo, never()).save(any());
    }

    @Test
    void theSummarySaysWhetherScanningIsAvailable() {
        service.setDiscoveryEnabled(false);
        assertThat(service.summary()).containsEntry("scanEnabled", false);

        service.setDiscoveryEnabled(true);
        assertThat(service.summary()).containsEntry("scanEnabled", true);
    }

    @Test
    void theAddressGuardsStillApplyWhenDiscoveryIsEnabled() {
        service.setDiscoveryEnabled(true);
        NetworkScanRequestDto metadata = new NetworkScanRequestDto();
        metadata.setIpAddresses(List.of("169.254.169.254"));

        assertThatThrownBy(() -> service.scan(metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("link-local");
    }
}
