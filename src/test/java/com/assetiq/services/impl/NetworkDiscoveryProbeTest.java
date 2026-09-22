package com.assetiq.services.impl;

import com.assetiq.enums.DeviceStatus;
import com.assetiq.models.DiscoveredDevice;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.DiscoveredDeviceRepository;
import com.assetiq.repositories.OrganisationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NetworkDiscoveryProbeTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

    @Test
    void rescanKeepsAPromotedDevicePromoted() {
        DiscoveredDevice d = new DiscoveredDevice();
        d.setStatus(DeviceStatus.PROMOTED);
        d.setPromotedAssetId(UUID.randomUUID());

        NetworkDiscoveryServiceImpl.applyProbe(d, true, "srv", 12, false, null, NOW);
        assertThat(d.getStatus()).isEqualTo(DeviceStatus.PROMOTED);

        NetworkDiscoveryServiceImpl.applyProbe(d, false, null, 0, false, null, NOW);
        assertThat(d.getStatus()).isEqualTo(DeviceStatus.PROMOTED);
    }

    @Test
    void unpromotedDeviceFollowsReachability() {
        DiscoveredDevice d = new DiscoveredDevice();
        NetworkDiscoveryServiceImpl.applyProbe(d, true, null, 5, false, null, NOW);
        assertThat(d.getStatus()).isEqualTo(DeviceStatus.ONLINE);
        NetworkDiscoveryServiceImpl.applyProbe(d, false, null, 5, false, null, NOW);
        assertThat(d.getStatus()).isEqualTo(DeviceStatus.OFFLINE);
        assertThat(d.getResponseTimeMs()).isNull();
    }

    @Test
    void portsAndTypeComeFromThisProbeOnly() {
        DiscoveredDevice d = new DiscoveredDevice();
        NetworkDiscoveryServiceImpl.applyProbe(d, true, null, 5, true, List.of(22, 80), NOW);
        assertThat(d.getOpenPorts()).isEqualTo("22,80");
        assertThat(d.getDeviceType()).isEqualTo("Linux Server");

        NetworkDiscoveryServiceImpl.applyProbe(d, false, null, 5, true, null, NOW);
        assertThat(d.getOpenPorts()).isNull();
        assertThat(d.getDeviceType()).isNull();

        NetworkDiscoveryServiceImpl.applyProbe(d, true, null, 5, true, List.of(443), NOW);
        NetworkDiscoveryServiceImpl.applyProbe(d, true, null, 5, false, null, NOW);
        assertThat(d.getOpenPorts()).isNull();
    }

    @Test
    void rescanRevivesASoftDeletedDeviceInsteadOfDuplicatingIt() {
        DiscoveredDeviceRepository repo = mock(DiscoveredDeviceRepository.class);
        NetworkDiscoveryServiceImpl service = new NetworkDiscoveryServiceImpl(mock(OrganisationRepository.class),
                repo, mock(com.assetiq.services.AssetService.class));
        Organisation org = new Organisation();
        DiscoveredDevice deleted = new DiscoveredDevice();
        deleted.setId(UUID.randomUUID());
        deleted.setDeletedAt(NOW);
        when(repo.findByIpAddressAndOrganisationAndDeletedAtIsNull("10.0.0.5", org)).thenReturn(Optional.empty());
        when(repo.findFirstByIpAddressAndOrganisationAndDeletedAtIsNotNullOrderByDeletedAtDesc("10.0.0.5", org))
                .thenReturn(Optional.of(deleted));

        DiscoveredDevice device = service.findOrReviveDevice("10.0.0.5", org);

        assertThat(device).isSameAs(deleted);
        assertThat(device.getDeletedAt()).isNull();
    }
}
