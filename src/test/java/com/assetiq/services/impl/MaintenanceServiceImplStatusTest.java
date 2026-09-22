package com.assetiq.services.impl;

import com.assetiq.dto.MaintenanceRecordDto;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.MaintenanceStatus;
import com.assetiq.enums.MaintenanceType;
import com.assetiq.models.Asset;
import com.assetiq.models.MaintenanceRecord;
import com.assetiq.models.Organisation;
import com.assetiq.models.Supplier;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.MaintenanceRecordRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.SupplierRepository;
import com.assetiq.services.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Maintenance status keeps the asset in step and PUT replaces fully")
class MaintenanceServiceImplStatusTest {

    @Mock MaintenanceRecordRepository recordRepository;
    @Mock AssetRepository assetRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock NotificationService notificationService;

    private MaintenanceServiceImpl service;
    private Organisation org;
    private Asset asset;

    @BeforeEach
    void setUp() {
        service = new MaintenanceServiceImpl(recordRepository, assetRepository, supplierRepository,
                organisationRepository, notificationService);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        asset = new Asset();
        asset.setId(UUID.randomUUID());
        asset.setName("Generator");
        asset.setOrganisation(org);
        asset.setCurrency("GHS");
        asset.setStatus(AssetStatus.MAINTENANCE);
        when(assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(asset.getId(), org)).thenReturn(Optional.of(asset));
        when(recordRepository.save(any(MaintenanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private MaintenanceRecord record(MaintenanceStatus status) {
        MaintenanceRecord r = new MaintenanceRecord();
        r.setId(UUID.randomUUID());
        r.setAsset(asset);
        r.setOrganisation(org);
        r.setMaintenanceType(MaintenanceType.CORRECTIVE);
        r.setStatus(status);
        when(recordRepository.findByIdAndDeletedAtIsNull(r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void completingTheLastOpenRecordReturnsAnUnassignedAssetToStock() {
        MaintenanceRecord r = record(MaintenanceStatus.IN_PROGRESS);
        when(recordRepository.findByAssetIdAndDeletedAtIsNull(asset.getId())).thenReturn(Set.of(r));

        MaintenanceRecordDto out = service.completeMaintenanceRecord(r.getId());

        assertThat(out.getStatus()).isEqualTo(MaintenanceStatus.COMPLETED);
        assertThat(out.getPerformedDate()).isEqualTo(LocalDate.now());
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.IN_STOCK);
    }

    @Test
    void completingKeepsMaintenanceWhileAnotherRecordIsOpen() {
        asset.setAssignedUser(new User());
        MaintenanceRecord r = record(MaintenanceStatus.SCHEDULED);
        MaintenanceRecord other = record(MaintenanceStatus.IN_PROGRESS);
        when(recordRepository.findByAssetIdAndDeletedAtIsNull(asset.getId())).thenReturn(Set.of(r, other));

        service.completeMaintenanceRecord(r.getId());

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.MAINTENANCE);
    }

    @Test
    void completingAClosedRecordIsRejected() {
        MaintenanceRecord r = record(MaintenanceStatus.CANCELLED);
        assertThatThrownBy(() -> service.completeMaintenanceRecord(r.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancellingViaPutReleasesTheAssetAndKeepsStatusWhenOmitted() {
        asset.setAssignedUser(new User());
        MaintenanceRecord r = record(MaintenanceStatus.SCHEDULED);
        when(recordRepository.findByAssetIdAndDeletedAtIsNull(asset.getId())).thenReturn(Set.of(r));

        MaintenanceRecordDto put = new MaintenanceRecordDto();
        put.setAssetId(asset.getId());
        put.setMaintenanceType(MaintenanceType.CORRECTIVE);
        put.setStatus(MaintenanceStatus.CANCELLED);
        service.updateMaintenanceRecord(r.getId(), put);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.IN_USE);

        put.setStatus(null);
        MaintenanceRecordDto again = service.updateMaintenanceRecord(r.getId(), put);
        assertThat(again.getStatus()).isEqualTo(MaintenanceStatus.CANCELLED);
    }

    @Test
    void putSetsAndClearsTheVendor() {
        MaintenanceRecord r = record(MaintenanceStatus.SCHEDULED);
        Supplier vendor = new Supplier();
        vendor.setId(UUID.randomUUID());
        when(supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(vendor.getId(), org)).thenReturn(Optional.of(vendor));

        MaintenanceRecordDto put = new MaintenanceRecordDto();
        put.setAssetId(asset.getId());
        put.setMaintenanceType(MaintenanceType.CORRECTIVE);
        put.setVendorId(vendor.getId());
        put.setCost(new BigDecimal("10.00"));
        assertThat(service.updateMaintenanceRecord(r.getId(), put).getVendorId()).isEqualTo(vendor.getId());

        put.setVendorId(null);
        assertThat(service.updateMaintenanceRecord(r.getId(), put).getVendorId()).isNull();
    }

    @Test
    void historicalCompletedRecordDoesNotMoveTheAsset() {
        asset.setStatus(AssetStatus.IN_USE);
        MaintenanceRecordDto dto = new MaintenanceRecordDto();
        dto.setAssetId(asset.getId());
        dto.setMaintenanceType(MaintenanceType.ROUTINE);
        dto.setStatus(MaintenanceStatus.COMPLETED);
        MaintenanceRecordDto created = service.createMaintenanceRecord(dto);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.IN_USE);
        assertThat(created.getPerformedDate()).isEqualTo(java.time.LocalDate.now());
    }

    @Test
    void disposedAssetCannotGetMaintenance() {
        asset.setStatus(AssetStatus.DISPOSED);
        MaintenanceRecordDto dto = new MaintenanceRecordDto();
        dto.setAssetId(asset.getId());
        dto.setMaintenanceType(MaintenanceType.ROUTINE);
        assertThatThrownBy(() -> service.createMaintenanceRecord(dto)).isInstanceOf(IllegalStateException.class);
    }
}
