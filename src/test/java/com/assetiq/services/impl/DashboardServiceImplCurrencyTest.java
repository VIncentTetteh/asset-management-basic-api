package com.assetiq.services.impl;

import com.assetiq.enums.AssetStatus;
import com.assetiq.models.Asset;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.MaintenanceRecordRepository;
import com.assetiq.repositories.PurchaseOrderRepository;
import com.assetiq.repositories.SoftwareLicenseRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.repositories.WebhookRepository;
import com.assetiq.services.money.MoneyTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Golden tests: GHS tenant with GHS, USD (rate 15), EUR (rate 16) and JPY (no rate) assets.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardServiceImpl - mixed currencies")
class DashboardServiceImplCurrencyTest {

    @Mock AssetRepository assetRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock MaintenanceRecordRepository maintenanceRecordRepository;
    @Mock UserRepository userRepository;
    @Mock WebhookRepository webhookRepository;
    @Mock SoftwareLicenseRepository softwareLicenseRepository;

    private DashboardServiceImpl service;
    private Organisation org;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(assetRepository, purchaseOrderRepository,
                maintenanceRecordRepository, userRepository, webhookRepository, softwareLicenseRepository,
                MoneyTestSupport.aggregatorWithRates(Map.of("USD", "15", "EUR", "16")));
        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setBillingCurrency("GHS");

        Asset usd = asset("USD", "100", AssetStatus.IN_USE);
        usd.setPurchaseDate(LocalDate.now().minusMonths(2));
        usd.setUsefulLifeMonths(10);
        usd.setResidualValue(BigDecimal.ZERO);
        when(assetRepository.findAllByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of(
                asset("GHS", "1000", AssetStatus.IN_USE),
                usd,
                asset("EUR", "50", AssetStatus.IN_STOCK),
                asset("JPY", "10000", AssetStatus.IN_STOCK)));
    }

    @Test
    @DisplayName("depreciation summary is converted and flags the missing rate")
    void depreciationSummary() {
        Map<String, Object> r = service.getDepreciationSummary(org);

        assertThat(money(r, "totalAssetValue")).isEqualTo("3300.00");    // 1000 + 1500 + 800
        assertThat(money(r, "netBookValue")).isEqualTo("3000.00");       // 1000 + 80*15 + 800
        assertThat(money(r, "totalDepreciation")).isEqualTo("300.00");
        assertThat(money(r, "monthlyDepreciation")).isEqualTo("150.00"); // 10 USD * 15
        assertThat(r.get("currency")).isEqualTo("GHS");
        assertThat(r.get("complete")).isEqualTo(false);
        assertThat(r.get("missingRates")).isEqualTo(List.of("JPY->GHS"));
    }

    @Test
    @DisplayName("assets by status converts group values and the total")
    void assetsByStatus() {
        Map<String, Object> r = service.getAssetsByStatus(org);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) r.get("data");
        Map<String, String> valueByStatus = new java.util.HashMap<>();
        data.forEach(m -> valueByStatus.put((String) m.get("status"), ((BigDecimal) m.get("value")).toPlainString()));

        assertThat(valueByStatus).containsEntry("IN_USE", "2500.00").containsEntry("IN_STOCK", "800.00");
        assertThat(money(r, "totalValue")).isEqualTo("3300.00");
        assertThat(r.get("complete")).isEqualTo(false);
        assertThat(r.get("missingRates")).isEqualTo(List.of("JPY->GHS"));
    }

    @Test
    @DisplayName("summary totalAssetValue is converted and labelled")
    void summary() {
        when(purchaseOrderRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(Set.of());
        when(maintenanceRecordRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(Set.of());
        when(userRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(1L);
        when(webhookRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of());
        when(softwareLicenseRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of());

        Map<String, Object> r = service.getSummary(org);

        assertThat(money(r, "totalAssetValue")).isEqualTo("3300.00");
        assertThat(r.get("currency")).isEqualTo("GHS");
        assertThat(r.get("complete")).isEqualTo(false);
    }

    private static String money(Map<String, Object> response, String key) {
        return ((BigDecimal) response.get(key)).toPlainString();
    }

    private Asset asset(String currency, String cost, AssetStatus status) {
        Asset a = new Asset();
        a.setId(UUID.randomUUID());
        a.setOrganisation(org);
        a.setStatus(status);
        a.setCurrency(currency);
        a.setPurchaseCost(new BigDecimal(cost));
        return a;
    }
}
