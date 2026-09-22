package com.assetiq.services.impl;

import com.assetiq.dto.DisposalRecordDto;
import com.assetiq.dto.MaintenanceRecordDto;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.DisposalMethod;
import com.assetiq.enums.MaintenanceType;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.*;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.*;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Maintenance and disposal amounts carry a currency")
class MaintenanceDisposalCurrencyTest {

    @Mock MaintenanceRecordRepository maintenanceRepository;
    @Mock DisposalRecordRepository disposalRepository;
    @Mock AssetRepository assetRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock com.assetiq.repositories.CheckoutRecordRepository checkoutRepository;

    private MaintenanceServiceImpl maintenance;
    private DisposalServiceImpl disposals;
    private Organisation org;
    private Asset asset;

    @BeforeEach
    void setUp() {
        maintenance = new MaintenanceServiceImpl(maintenanceRepository, assetRepository, supplierRepository,
                organisationRepository, notificationService, checkoutRepository,
                new AssetStateTransitionServiceImpl(assetRepository));
        disposals = new DisposalServiceImpl(disposalRepository, assetRepository, organisationRepository,
                userRepository, notificationService, new AssetStateTransitionServiceImpl(assetRepository));
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));

        asset = new Asset();
        asset.setId(UUID.randomUUID());
        asset.setOrganisation(org);
        asset.setName("Generator");
        asset.setCurrency("GHS");
        asset.setStatus(AssetStatus.IN_USE);
        when(assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(asset.getId(), org))
                .thenReturn(Optional.of(asset));
        when(maintenanceRepository.save(any(MaintenanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(disposalRepository.save(any(DisposalRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        User approver = new User();
        approver.setId(UUID.randomUUID());
        approver.setEmail("ops@example.com");
        approver.setOrganisation(org);
        approver.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByEmailAndOrganisationId(approver.getEmail(), org.getId()))
                .thenReturn(Optional.of(approver));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(approver.getEmail(), "n/a", List.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a maintenance cost without a currency defaults to the asset's")
    void maintenance_defaultsToAssetCurrency() {
        MaintenanceRecordDto result = maintenance.createMaintenanceRecord(maintenanceDto(null));
        assertThat(result.getCurrency()).isEqualTo("GHS");
    }

    @Test
    @DisplayName("a supplied maintenance currency is normalised and kept")
    void maintenance_keepsSuppliedCurrency() {
        MaintenanceRecordDto result = maintenance.createMaintenanceRecord(maintenanceDto(" usd "));
        assertThat(result.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("an invalid maintenance currency is rejected")
    void maintenance_rejectsInvalidCurrency() {
        assertThatThrownBy(() -> maintenance.createMaintenanceRecord(maintenanceDto("DOLLARS")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a legacy maintenance row without a currency reads as the asset's currency")
    void maintenance_legacyRowReadsAssetCurrency() {
        MaintenanceRecord legacy = new MaintenanceRecord();
        legacy.setAsset(asset);
        assertThat(legacy.effectiveCurrency()).isEqualTo("GHS");
        legacy.setCurrency("EUR");
        assertThat(legacy.effectiveCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("a disposal sale value defaults to the asset's currency, or keeps the supplied one")
    void disposal_currency() {
        assertThat(disposals.createDisposalRecord(disposalDto(null)).getCurrency()).isEqualTo("GHS");

        asset.setStatus(AssetStatus.IN_USE);
        assertThat(disposals.createDisposalRecord(disposalDto("usd")).getCurrency()).isEqualTo("USD");
    }

    private MaintenanceRecordDto maintenanceDto(String currency) {
        MaintenanceRecordDto dto = new MaintenanceRecordDto();
        dto.setAssetId(asset.getId());
        dto.setMaintenanceType(MaintenanceType.PREVENTIVE);
        dto.setScheduledDate(LocalDate.now());
        dto.setCost(new BigDecimal("100"));
        dto.setCurrency(currency);
        return dto;
    }

    private DisposalRecordDto disposalDto(String currency) {
        DisposalRecordDto dto = new DisposalRecordDto();
        dto.setAssetId(asset.getId());
        dto.setDisposalMethod(DisposalMethod.values()[0]);
        dto.setDisposalDate(LocalDate.now());
        dto.setSaleValue(new BigDecimal("40"));
        dto.setCurrency(currency);
        return dto;
    }
}
