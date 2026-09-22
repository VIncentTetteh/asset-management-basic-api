package com.assetiq.services.impl;

import com.assetiq.dto.AssetDto;
import com.assetiq.dto.PromoteDeviceRequest;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.DeviceStatus;
import com.assetiq.models.DiscoveredDevice;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.DiscoveredDeviceRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.AssetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Promoting a discovered device, and listing devices by status. */
class NetworkDiscoveryPromoteTest {

    private final OrganisationRepository organisationRepository = mock(OrganisationRepository.class);
    private final DiscoveredDeviceRepository deviceRepo = mock(DiscoveredDeviceRepository.class);
    private final AssetService assetService = mock(AssetService.class);
    private NetworkDiscoveryServiceImpl service;
    private Organisation org;
    private DiscoveredDevice device;

    @BeforeEach
    void setUp() {
        service = new NetworkDiscoveryServiceImpl(organisationRepository, deviceRepo, assetService);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        device = new DiscoveredDevice();
        device.setId(UUID.randomUUID());
        device.setIpAddress("10.0.0.7");
        device.setHostname("printer-3f");
        device.setDeviceType("PRINTER");
        device.setOpenPorts("9100,80");
        device.setStatus(DeviceStatus.ONLINE);
        when(deviceRepo.findByIdAndOrganisationAndDeletedAtIsNull(device.getId(), org)).thenReturn(Optional.of(device));
        when(assetService.create(any())).thenAnswer(inv -> {
            AssetDto d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void promotionUsesTheChosenNameCategoryAndLocationThroughTheAssetService() {
        UUID category = UUID.randomUUID();
        UUID location = UUID.randomUUID();

        var result = service.promote(device.getId(), new PromoteDeviceRequest(" 3rd floor printer ", category, location));

        ArgumentCaptor<AssetDto> dto = ArgumentCaptor.forClass(AssetDto.class);
        verify(assetService).create(dto.capture());
        assertThat(dto.getValue().getName()).isEqualTo("3rd floor printer");
        assertThat(dto.getValue().getCategoryId()).isEqualTo(category);
        assertThat(dto.getValue().getLocationId()).isEqualTo(location);
        assertThat(dto.getValue().getStatus()).isEqualTo(AssetStatus.IN_USE);
        assertThat(dto.getValue().getDescription())
                .contains("IP address: 10.0.0.7", "Hostname: printer-3f", "Device type: PRINTER", "Open ports: 9100,80");
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.PROMOTED);
        assertThat(device.getPromotedAssetId()).isEqualTo(result.get("assetId"));
    }

    @Test
    void withoutABodyTheHostnameNamesTheAsset() {
        service.promote(device.getId(), null);
        ArgumentCaptor<AssetDto> dto = ArgumentCaptor.forClass(AssetDto.class);
        verify(assetService).create(dto.capture());
        assertThat(dto.getValue().getName()).isEqualTo("printer-3f");

        device.setHostname(null);
        assertThat(NetworkDiscoveryServiceImpl.promotedAsset(device, null).getName()).isEqualTo("10.0.0.7");
    }

    @Test
    void listNarrowsToOneStatus() {
        var pageable = PageRequest.of(0, 20);
        when(deviceRepo.findByOrganisationAndStatusAndDeletedAtIsNullOrderByLastSeenAtDesc(eq(org), eq(DeviceStatus.OFFLINE), any()))
                .thenReturn(Page.empty());

        service.list(pageable, DeviceStatus.OFFLINE);

        verify(deviceRepo).findByOrganisationAndStatusAndDeletedAtIsNullOrderByLastSeenAtDesc(org, DeviceStatus.OFFLINE, pageable);
        verify(deviceRepo, never()).findByOrganisationAndDeletedAtIsNullOrderByLastSeenAtDesc(any(), any());
    }
}
