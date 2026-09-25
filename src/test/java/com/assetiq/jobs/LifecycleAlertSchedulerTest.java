package com.assetiq.jobs;

import com.assetiq.enums.NotificationType;
import com.assetiq.models.Asset;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.BudgetRepository;
import com.assetiq.repositories.LeaseRecordRepository;
import com.assetiq.services.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LifecycleAlertSchedulerTest {

    @Mock AssetRepository assetRepository;
    @Mock LeaseRecordRepository leaseRecordRepository;
    @Mock BudgetRepository budgetRepository;
    @Mock NotificationService notificationService;

    private LifecycleAlertScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LifecycleAlertScheduler(
                assetRepository, leaseRecordRepository, budgetRepository, notificationService);
    }

    @Test
    void run_sendsOneKeyedWarrantyAlertAtItsExactMilestone() {
        Organisation organisation = new Organisation();
        organisation.setId(UUID.randomUUID());
        Asset asset = new Asset();
        asset.setId(UUID.randomUUID());
        asset.setName("Router");
        asset.setAssetTag("NET-1");
        asset.setOrganisation(organisation);
        asset.setWarrantyExpiryDate(LocalDate.now().plusDays(30));

        when(assetRepository.findWarrantyExpiringOn(any(LocalDate.class), any(Pageable.class)))
                .thenAnswer(invocation -> invocation.<LocalDate>getArgument(0)
                        .equals(asset.getWarrantyExpiryDate())
                        ? new PageImpl<>(List.of(asset)) : Page.empty());
        when(assetRepository.findActiveAssetsWithUsefulLife(any(Pageable.class))).thenReturn(Page.empty());
        when(assetRepository.findInsuranceExpiringOn(any(LocalDate.class), any(Pageable.class))).thenReturn(Page.empty());
        when(leaseRecordRepository.findActiveExpiringOn(any(LocalDate.class), any(Pageable.class))).thenReturn(Page.empty());
        when(budgetRepository.findActiveWithSpend(any(Pageable.class))).thenReturn(Page.empty());
        when(assetRepository.findInactiveInStock(any(Instant.class), any(Pageable.class))).thenReturn(Page.empty());

        scheduler.run();

        verify(notificationService, times(1)).notifyOrgAdminsOnce(
                eq(organisation), eq(NotificationType.WARRANTY_EXPIRY),
                anyString(), anyString(), eq(asset.getId()),
                eq("/assets/" + asset.getId()),
                eq("lifecycle:warranty:" + asset.getId() + ":" + asset.getWarrantyExpiryDate()));
    }
}
