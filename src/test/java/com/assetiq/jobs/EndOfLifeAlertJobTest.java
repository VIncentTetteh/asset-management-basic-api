package com.assetiq.jobs;

import com.assetiq.enums.NotificationType;
import com.assetiq.models.Contract;
import com.assetiq.models.Organisation;
import com.assetiq.models.SoftwareLicense;
import com.assetiq.repositories.ContractRepository;
import com.assetiq.repositories.SoftwareLicenseRepository;
import com.assetiq.services.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EndOfLifeAlertJob")
class EndOfLifeAlertJobTest {

    @Mock SoftwareLicenseRepository licenseRepository;
    @Mock ContractRepository contractRepository;
    @Mock NotificationService notificationService;

    private EndOfLifeAlertJob job;
    private Organisation org;

    @BeforeEach
    void setUp() {
        job = new EndOfLifeAlertJob(licenseRepository, contractRepository, notificationService);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setName("Test Org");
    }

    @Test
    @DisplayName("dispatches an idempotent alert for a license at the 30-day milestone")
    void licenseExpiry_dispatchesIdempotentNotification() {
        SoftwareLicense license = new SoftwareLicense();
        license.setId(UUID.randomUUID());
        license.setName("Core Banking DB");
        license.setVendor("Vendor");
        license.setExpiryDate(LocalDate.now().plusDays(30));
        license.setOrganisation(org);

        when(licenseRepository.findExpiringOn(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(license)));
        when(contractRepository.findExpiringOn(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        job.run();

        verify(notificationService).notifyOrgAdminsOnce(
                eq(org), eq(NotificationType.DEPRECATION), anyString(), anyString(),
                eq(license.getId()), eq("/licenses"),
                eq("expiry:license:" + license.getId() + ":" + license.getExpiryDate()));
    }

    @Test
    @DisplayName("dispatches an idempotent alert for a contract at the 30-day milestone")
    void contractExpiry_dispatchesIdempotentNotification() {
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID());
        contract.setTitle("Network Support");
        contract.setContractNumber("NET-1");
        contract.setEndDate(LocalDate.now().plusDays(30));
        contract.setOrganisation(org);

        when(licenseRepository.findExpiringOn(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(contractRepository.findExpiringOn(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(contract)));

        job.run();

        verify(notificationService).notifyOrgAdminsOnce(
                eq(org), eq(NotificationType.MAINTENANCE), anyString(), anyString(),
                eq(contract.getId()), eq("/contracts"),
                eq("expiry:contract:" + contract.getId() + ":" + contract.getEndDate()));
    }

    @Test
    @DisplayName("does not dispatch when no license or contract reaches the milestone")
    void noExpiry_noNotificationDispatched() {
        when(licenseRepository.findExpiringOn(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(contractRepository.findExpiringOn(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        job.run();

        verifyNoInteractions(notificationService);
    }
}
