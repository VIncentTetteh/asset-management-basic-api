package com.assetiq.jobs;

import com.assetiq.enums.NotificationType;
import com.assetiq.models.Asset;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.ContractRepository;
import com.assetiq.repositories.SoftwareLicenseRepository;
import com.assetiq.services.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the rewritten EndOfLifeAlertJob that dispatches
 * in-app notifications via NotificationService.
 *
 * Uses @RequiredArgsConstructor on the job, so the constructor order is:
 *   assetRepository, licenseRepository, contractRepository, notificationService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EndOfLifeAlertJob")
class EndOfLifeAlertJobTest {

    @Mock AssetRepository           assetRepository;
    @Mock SoftwareLicenseRepository licenseRepository;
    @Mock ContractRepository        contractRepository;
    @Mock NotificationService       notificationService;

    EndOfLifeAlertJob job;
    Organisation org;

    @BeforeEach
    void setUp() {
        job = new EndOfLifeAlertJob(assetRepository, licenseRepository, contractRepository, notificationService);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setName("Test Org");
    }

    // ============================================================
    // Warranty expiry
    // ============================================================

    @Test
    @DisplayName("run() dispatches WARRANTY_EXPIRY notification for each expiring asset")
    void warrantyExpiry_dispatchesNotification() {
        Asset asset = assetWithWarranty("Laptop", LocalDate.now().plusDays(10));
        when(assetRepository.findWarrantyExpiringSoon(any())).thenReturn(List.of(asset));
        stubNoOtherAlerts();

        job.run();

        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
        verify(notificationService).notifyOrgAdmins(
                eq(org), typeCaptor.capture(), anyString(), anyString(), eq(asset.getId()), anyString());
        assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.WARRANTY_EXPIRY);
    }

    @Test
    @DisplayName("run() dispatches one notification per expiring asset")
    void warrantyExpiry_multipleAssets_dispatchesMultipleNotifications() {
        Asset a1 = assetWithWarranty("Laptop",  LocalDate.now().plusDays(5));
        Asset a2 = assetWithWarranty("Monitor", LocalDate.now().plusDays(15));
        when(assetRepository.findWarrantyExpiringSoon(any())).thenReturn(List.of(a1, a2));
        stubNoOtherAlerts();

        job.run();

        verify(notificationService, times(2))
                .notifyOrgAdmins(any(), eq(NotificationType.WARRANTY_EXPIRY), any(), any(), any(), any());
    }

    // ============================================================
    // End of useful life
    // ============================================================

    @Test
    @DisplayName("run() dispatches END_OF_LIFE for asset past its useful life")
    void assetEndOfLife_dispatchesNotification() {
        // 25 months old, 24-month useful life → already EOL
        Asset asset = assetWithUsefulLife("Old Server", LocalDate.now().minusMonths(25), 24);
        when(assetRepository.findWarrantyExpiringSoon(any())).thenReturn(Collections.emptyList());
        when(assetRepository.findActiveAssetsWithUsefulLife()).thenReturn(List.of(asset));
        when(licenseRepository.findAll()).thenReturn(Collections.emptyList());
        when(contractRepository.findAll()).thenReturn(Collections.emptyList());

        job.run();

        verify(notificationService).notifyOrgAdmins(
                eq(org), eq(NotificationType.END_OF_LIFE),
                anyString(), anyString(), eq(asset.getId()), anyString());
    }

    @Test
    @DisplayName("run() does NOT dispatch END_OF_LIFE for asset still within its useful life")
    void assetWithinUsefulLife_noEolNotification() {
        // 10 months old, 24-month useful life → still fine
        Asset asset = assetWithUsefulLife("New Laptop", LocalDate.now().minusMonths(10), 24);
        when(assetRepository.findWarrantyExpiringSoon(any())).thenReturn(Collections.emptyList());
        when(assetRepository.findActiveAssetsWithUsefulLife()).thenReturn(List.of(asset));
        when(licenseRepository.findAll()).thenReturn(Collections.emptyList());
        when(contractRepository.findAll()).thenReturn(Collections.emptyList());

        job.run();

        verify(notificationService, never())
                .notifyOrgAdmins(any(), eq(NotificationType.END_OF_LIFE), any(), any(), any(), any());
    }

    // ============================================================
    // No alerts
    // ============================================================

    @Test
    @DisplayName("run() does NOT call NotificationService when nothing is expiring")
    void noExpiry_noNotificationDispatched() {
        when(assetRepository.findWarrantyExpiringSoon(any())).thenReturn(Collections.emptyList());
        when(assetRepository.findActiveAssetsWithUsefulLife()).thenReturn(Collections.emptyList());
        when(licenseRepository.findAll()).thenReturn(Collections.emptyList());
        when(contractRepository.findAll()).thenReturn(Collections.emptyList());

        job.run();

        verifyNoInteractions(notificationService);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private Asset assetWithWarranty(String name, LocalDate warrantyExpiry) {
        Asset a = new Asset();
        a.setId(UUID.randomUUID());
        a.setName(name);
        a.setWarrantyExpiryDate(warrantyExpiry);
        a.setOrganisation(org);
        return a;
    }

    private Asset assetWithUsefulLife(String name, LocalDate purchaseDate, int usefulLifeMonths) {
        Asset a = new Asset();
        a.setId(UUID.randomUUID());
        a.setName(name);
        a.setPurchaseDate(purchaseDate);
        a.setUsefulLifeMonths(usefulLifeMonths);
        a.setOrganisation(org);
        return a;
    }

    private void stubNoOtherAlerts() {
        when(assetRepository.findActiveAssetsWithUsefulLife()).thenReturn(Collections.emptyList());
        when(licenseRepository.findAll()).thenReturn(Collections.emptyList());
        when(contractRepository.findAll()).thenReturn(Collections.emptyList());
    }
}
