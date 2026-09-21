package com.assetiq.services.impl;

import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.MaintenanceStatus;
import com.assetiq.enums.MaintenanceType;
import com.assetiq.enums.POStatus;
import com.assetiq.models.Asset;
import com.assetiq.models.Budget;
import com.assetiq.models.DisposalRecord;
import com.assetiq.models.MaintenanceRecord;
import com.assetiq.models.Organisation;
import com.assetiq.models.PurchaseOrder;
import com.assetiq.models.Supplier;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.BudgetRepository;
import com.assetiq.repositories.DisposalRecordRepository;
import com.assetiq.repositories.MaintenanceRecordRepository;
import com.assetiq.repositories.PurchaseOrderRepository;
import com.assetiq.services.money.MoneyTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Golden tests: a GHS-based tenant holding GHS, USD, EUR and JPY records.
 * Rates to GHS: USD 15, EUR 16, JPY none. Expected totals are hand-computed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsServiceImpl - mixed currencies")
class AnalyticsServiceImplCurrencyTest {

    @Mock AssetRepository assetRepository;
    @Mock MaintenanceRecordRepository maintenanceRecordRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock BudgetRepository budgetRepository;
    @Mock DisposalRecordRepository disposalRecordRepository;

    private AnalyticsServiceImpl service;
    private Organisation org;
    private Asset ghsAsset;
    private Asset usdAsset;
    private Asset eurAsset;
    private Asset jpyAsset;

    @BeforeEach
    void setUp() {
        service = new AnalyticsServiceImpl(assetRepository, maintenanceRecordRepository,
                purchaseOrderRepository, budgetRepository, disposalRecordRepository,
                MoneyTestSupport.aggregatorWithRates(Map.of("USD", "15", "EUR", "16")));
        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setBillingCurrency("GHS");

        LocalDate recent = LocalDate.now().minusDays(10);
        ghsAsset = asset("GHS", "1000", recent);
        // 100 USD over 10 months, 2 months elapsed: NBV 80 USD, 10 USD/month
        usdAsset = asset("USD", "100", LocalDate.now().minusMonths(2));
        usdAsset.setUsefulLifeMonths(10);
        usdAsset.setResidualValue(BigDecimal.ZERO);
        eurAsset = asset("EUR", "50", recent);
        jpyAsset = asset("JPY", "10000", recent);
    }

    @Test
    @DisplayName("financial analytics converts every total and flags the missing JPY rate")
    void financialAnalytics_convertsAllTotals() {
        when(assetRepository.findAllByOrganisationAndDeletedAtIsNull(org))
                .thenReturn(List.of(ghsAsset, usdAsset, eurAsset, jpyAsset));
        when(maintenanceRecordRepository.findByOrganisationAndDeletedAtIsNull(org))
                .thenReturn(new LinkedHashSet<>(List.of(
                        maintenance(usdAsset, "10"),   // 10 USD -> 150 GHS
                        maintenance(null, "25"),       // no asset -> base currency
                        maintenance(jpyAsset, "500")))); // excluded
        DisposalRecord disposal = new DisposalRecord();
        disposal.setAsset(eurAsset);
        disposal.setSaleValue(new BigDecimal("10")); // 10 EUR -> 160 GHS
        when(disposalRecordRepository.findByOrganisationAndDisposalDateBetweenAndDeletedAtIsNull(eq(org), any(), any()))
                .thenReturn(java.util.Set.of(disposal));
        when(budgetRepository.findByOrganisationAndDeletedAtIsNullOrderByPeriodStartDesc(org))
                .thenReturn(List.of(
                        budget("GHS", "5000", "1000"),
                        budget("USD", "200", "100"),   // 3000 / 1500 GHS
                        budget("JPY", "90000", "90000"))); // excluded

        Map<String, Object> r = service.getFinancialAnalytics("year", org);

        // 1000 + 100*15 + 50*16 = 3300; JPY excluded
        assertThat(money(r, "totalAssetValue")).isEqualTo("3300.00");
        // NBV: 1000 + 80*15 + 50*16 = 3000
        assertThat(money(r, "netBookValue")).isEqualTo("3000.00");
        assertThat(money(r, "totalDepreciation")).isEqualTo("300.00");
        assertThat(money(r, "monthlyDepreciation")).isEqualTo("150.00");
        assertThat(money(r, "totalMaintenance")).isEqualTo("175.00");
        assertThat(money(r, "totalAcquisition")).isEqualTo("3300.00");
        assertThat(money(r, "totalDisposal")).isEqualTo("160.00");
        assertThat(money(r, "totalBudget")).isEqualTo("8000.00");
        assertThat(money(r, "totalActualSpend")).isEqualTo("2500.00");
        assertThat(r.get("budgetUtilization")).isEqualTo(31.25);
        assertThat(r.get("currency")).isEqualTo("GHS");
        assertThat(r.get("complete")).isEqualTo(false);
        assertThat(r.get("missingRates")).isEqualTo(List.of("JPY->GHS"));
    }

    @Test
    @DisplayName("purchase-order analytics compares converted values")
    void purchaseOrderAnalytics_comparesConvertedValues() {
        Supplier acme = new Supplier();
        acme.setName("Acme");
        when(purchaseOrderRepository.findByOrganisationAndDeletedAtIsNull(org))
                .thenReturn(new LinkedHashSet<>(List.of(
                        po("GHS", "300", acme),
                        po("USD", "10", acme),    // 150 GHS
                        po("EUR", "25", null),    // 400 GHS - largest once converted
                        po("JPY", "5000", null)))); // excluded despite the largest raw amount

        Map<String, Object> r = service.getPurchaseOrderAnalytics("month", org);

        assertThat(money(r, "totalPOValue")).isEqualTo("850.00");
        assertThat(money(r, "averagePOValue")).isEqualTo("283.33");
        assertThat(money(r, "largestPO")).isEqualTo("400.00");
        assertThat(money(r, "smallestPO")).isEqualTo("150.00");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suppliers = (List<Map<String, Object>>) r.get("topSuppliers");
        assertThat(suppliers).hasSize(1);
        assertThat(suppliers.get(0).get("totalValue").toString()).isEqualTo("450.00");
        assertThat(r.get("complete")).isEqualTo(false);
        assertThat(r.get("missingRates")).isEqualTo(List.of("JPY->GHS"));
    }

    @Test
    @DisplayName("fully convertible data reports complete=true")
    void assetAnalytics_completeWhenAllRatesExist() {
        when(assetRepository.findAllByOrganisationAndDeletedAtIsNull(org))
                .thenReturn(List.of(ghsAsset, eurAsset));

        Map<String, Object> r = service.getAssetAnalytics("year", "status", org);

        assertThat(money(r, "totalValue")).isEqualTo("1800.00");
        assertThat(r.get("currency")).isEqualTo("GHS");
        assertThat(r.get("complete")).isEqualTo(true);
        assertThat(r.get("missingRates")).isEqualTo(List.of());
    }

    private static String money(Map<String, Object> response, String key) {
        return ((BigDecimal) response.get(key)).toPlainString();
    }

    private Asset asset(String currency, String cost, LocalDate purchaseDate) {
        Asset a = new Asset();
        a.setId(UUID.randomUUID());
        a.setOrganisation(org);
        a.setStatus(AssetStatus.IN_USE);
        a.setCurrency(currency);
        a.setPurchaseCost(new BigDecimal(cost));
        a.setPurchaseDate(purchaseDate);
        return a;
    }

    private MaintenanceRecord maintenance(Asset asset, String cost) {
        MaintenanceRecord m = new MaintenanceRecord();
        m.setOrganisation(org);
        m.setAsset(asset);
        m.setMaintenanceType(MaintenanceType.CORRECTIVE);
        m.setStatus(MaintenanceStatus.COMPLETED);
        m.setPerformedDate(LocalDate.now().minusDays(5));
        m.setCost(new BigDecimal(cost));
        return m;
    }

    private Budget budget(String currency, String total, String spent) {
        Budget b = new Budget();
        b.setOrganisation(org);
        b.setCurrency(currency);
        b.setTotalAmount(new BigDecimal(total));
        b.setSpentAmount(new BigDecimal(spent));
        return b;
    }

    private PurchaseOrder po(String currency, String amount, Supplier supplier) {
        PurchaseOrder po = new PurchaseOrder();
        po.setOrganisation(org);
        po.setStatus(POStatus.APPROVED);
        po.setCreatedAt(Instant.now());
        po.setCurrency(currency);
        po.setTotalAmount(new BigDecimal(amount));
        po.setSupplier(supplier);
        return po;
    }
}
