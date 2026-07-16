package com.assetiq.services.impl;

import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.MaintenanceStatus;
import com.assetiq.enums.MaintenanceType;
import com.assetiq.enums.POStatus;
import com.assetiq.models.Asset;
import com.assetiq.models.MaintenanceRecord;
import com.assetiq.models.Organisation;
import com.assetiq.models.PurchaseOrder;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.BudgetRepository;
import com.assetiq.repositories.DisposalRecordRepository;
import com.assetiq.repositories.MaintenanceRecordRepository;
import com.assetiq.repositories.PurchaseOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsServiceImpl")
class AnalyticsServiceImplTest {

    @Mock AssetRepository assetRepository;
    @Mock MaintenanceRecordRepository maintenanceRecordRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock BudgetRepository budgetRepository;
    @Mock DisposalRecordRepository disposalRecordRepository;

    AnalyticsServiceImpl service;
    Organisation org;

    @BeforeEach
    void setUp() {
        service = new AnalyticsServiceImpl(
                assetRepository,
                maintenanceRecordRepository,
                purchaseOrderRepository,
                budgetRepository,
                disposalRecordRepository);

        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setName("Analytics Test Org");
    }

    @Test
    @DisplayName("asset analytics period filters records by purchase date")
    void assetAnalytics_appliesPeriodWindow() {
        when(assetRepository.findAllByOrganisationAndDeletedAtIsNull(org))
                .thenReturn(List.of(
                        asset(LocalDate.now().minusDays(2), "1000"),
                        asset(LocalDate.now().minusMonths(2), "5000")));

        Map<String, Object> result = service.getAssetAnalytics("week", "status", org);

        assertThat(result.get("total")).isEqualTo(1L);
        assertThat(result.get("totalValue").toString()).isEqualTo("1000.00");
    }

    @Test
    @DisplayName("purchase order analytics period filters records by creation date")
    void purchaseOrderAnalytics_appliesPeriodWindow() {
        when(purchaseOrderRepository.findByOrganisationAndDeletedAtIsNull(org))
                .thenReturn(Set.of(
                        purchaseOrder(Instant.now().minusSeconds(2 * 86_400L), "1200"),
                        purchaseOrder(Instant.now().minusSeconds(70 * 86_400L), "4800")));

        Map<String, Object> result = service.getPurchaseOrderAnalytics("week", org);

        assertThat(result.get("totalPOs")).isEqualTo(1L);
        assertThat(result.get("totalPOValue").toString()).isEqualTo("1200.00");
    }

    @Test
    @DisplayName("maintenance analytics period filters records by performed date")
    void maintenanceAnalytics_appliesPeriodWindow() {
        when(maintenanceRecordRepository.findByOrganisationAndDeletedAtIsNull(org))
                .thenReturn(Set.of(
                        maintenanceRecord(LocalDate.now().minusDays(2), "300"),
                        maintenanceRecord(LocalDate.now().minusMonths(2), "700")));

        Map<String, Object> result = service.getMaintenanceAnalytics("week", org);

        assertThat(result.get("period")).isEqualTo("week");
        assertThat(result.get("totalMaintenanceRecords")).isEqualTo(1L);
        assertThat(result.get("totalMaintenanceCost").toString()).isEqualTo("300.00");
    }

    private Asset asset(LocalDate purchaseDate, String cost) {
        Asset asset = new Asset();
        asset.setOrganisation(org);
        asset.setStatus(AssetStatus.IN_USE);
        asset.setPurchaseDate(purchaseDate);
        asset.setPurchaseCost(new BigDecimal(cost));
        return asset;
    }

    private PurchaseOrder purchaseOrder(Instant createdAt, String amount) {
        PurchaseOrder po = new PurchaseOrder();
        po.setOrganisation(org);
        po.setStatus(POStatus.APPROVED);
        po.setCreatedAt(createdAt);
        po.setTotalAmount(new BigDecimal(amount));
        return po;
    }

    private MaintenanceRecord maintenanceRecord(LocalDate performedDate, String cost) {
        MaintenanceRecord record = new MaintenanceRecord();
        record.setOrganisation(org);
        record.setMaintenanceType(MaintenanceType.PREVENTIVE);
        record.setStatus(MaintenanceStatus.COMPLETED);
        record.setPerformedDate(performedDate);
        record.setCost(new BigDecimal(cost));
        return record;
    }
}
