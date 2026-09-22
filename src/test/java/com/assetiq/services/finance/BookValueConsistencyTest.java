package com.assetiq.services.finance;

import com.assetiq.dto.AssetDto;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.DepreciationMethod;
import com.assetiq.models.Asset;
import com.assetiq.models.Category;
import com.assetiq.models.DepreciationPolicy;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.*;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.EmailService;
import com.assetiq.services.NotificationService;
import com.assetiq.services.UsageLimitService;
import com.assetiq.services.impl.AnalyticsServiceImpl;
import com.assetiq.services.impl.AssetServiceImpl;
import com.assetiq.services.impl.DashboardServiceImpl;
import com.assetiq.services.money.MoneyAggregator;
import com.assetiq.services.money.MoneyTestSupport;
import jakarta.persistence.EntityManager;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * One fixture, every surface: the dashboard summary, the depreciation summary,
 * financial analytics (total and per category) and the asset detail must all
 * report the same net book value, because they all use {@link DepreciationCalculator}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Book value agrees across dashboard, analytics and asset detail")
class BookValueConsistencyTest {

    @Mock AssetRepository assetRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock MaintenanceRecordRepository maintenanceRecordRepository;
    @Mock UserRepository userRepository;
    @Mock WebhookRepository webhookRepository;
    @Mock SoftwareLicenseRepository softwareLicenseRepository;
    @Mock BudgetRepository budgetRepository;
    @Mock DisposalRecordRepository disposalRecordRepository;
    @Mock OrganisationRepository organisationRepository;

    private Organisation org;
    private List<Asset> assets;
    private DashboardServiceImpl dashboard;
    private AnalyticsServiceImpl analytics;
    private AssetServiceImpl assetService;

    @BeforeEach
    void setUp() {
        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setBillingCurrency("GHS");
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));

        MoneyAggregator money = MoneyTestSupport.aggregatorWithRates(Map.of("USD", "15"));
        dashboard = new DashboardServiceImpl(assetRepository, purchaseOrderRepository, maintenanceRecordRepository,
                userRepository, webhookRepository, softwareLicenseRepository, money);
        analytics = new AnalyticsServiceImpl(assetRepository, maintenanceRecordRepository, purchaseOrderRepository,
                budgetRepository, disposalRecordRepository, money);
        assetService = new AssetServiceImpl(assetRepository, mock(DepartmentRepository.class), organisationRepository,
                mock(CategoryRepository.class), mock(LocationRepository.class), mock(SupplierRepository.class),
                userRepository, purchaseOrderRepository, mock(EntityManager.class), mock(UsageLimitService.class),
                mock(AuditEventRepository.class), mock(AssetTransferRepository.class), maintenanceRecordRepository,
                disposalRecordRepository, mock(NotificationService.class), mock(EmailService.class),
                mock(CurrencyResolver.class), money,
                mock(com.assetiq.repositories.CheckoutRecordRepository.class));

        Category laptops = category("Laptops", policy(DepreciationMethod.DECLINING_BALANCE, 36, "10"));
        Category furniture = category("Furniture", null);
        LocalDate today = LocalDate.now();

        assets = List.of(
                // policy fallback: DDB, 36 months, 10% salvage; bought 2+ years ago (acquisition outside "month")
                asset("GHS", "3600", today.minusMonths(26), laptops, null, null, null, AssetStatus.IN_USE),
                // own straight-line settings override the policy; in maintenance (not IN_USE)
                asset("USD", "1200", today.minusMonths(5), laptops, 24, "0", DepreciationMethod.STRAIGHT_LINE,
                        AssetStatus.MAINTENANCE),
                // sum-of-digits, fully depreciated
                asset("GHS", "900", today.minusMonths(40), furniture, 12, "100",
                        DepreciationMethod.SUM_OF_YEARS_DIGITS, AssetStatus.IN_STOCK),
                // no life anywhere: carried at cost, flagged
                asset("GHS", "500", today.minusMonths(8), furniture, null, null, null, AssetStatus.IN_USE),
                // disposed: off the books everywhere
                asset("GHS", "10000", today.minusMonths(3), furniture, 12, "0", DepreciationMethod.STRAIGHT_LINE,
                        AssetStatus.DISPOSED));
        when(assetRepository.findAllByOrganisationAndDeletedAtIsNull(org)).thenReturn(assets);
        assets.forEach(a -> when(assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(a.getId(), org))
                .thenReturn(Optional.of(a)));
        when(purchaseOrderRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(Set.of());
        when(maintenanceRecordRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(Set.of());
        when(webhookRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of());
        when(softwareLicenseRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of());
        when(budgetRepository.findByOrganisationAndDeletedAtIsNullOrderByPeriodStartDesc(org)).thenReturn(List.of());
        when(disposalRecordRepository.findByOrganisationAndDisposalDateBetweenAndDeletedAtIsNull(eq(org), any(), any()))
                .thenReturn(Set.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void everySurfaceReportsTheSameNetBookValue() {
        BigDecimal detailSum = BigDecimal.ZERO;
        BigDecimal monthlySum = BigDecimal.ZERO;
        for (Asset a : assets) {
            if (a.getStatus() == AssetStatus.DISPOSED) continue;
            AssetDto dto = assetService.get(a.getId());
            BigDecimal rate = "USD".equals(a.getCurrency()) ? new BigDecimal("15") : BigDecimal.ONE;
            detailSum = detailSum.add(dto.getCurrentBookValue().multiply(rate));
            monthlySum = monthlySum.add(dto.getMonthlyDepreciation().multiply(rate));
        }

        Map<String, Object> summary = dashboard.getSummary(org);
        Map<String, Object> depreciation = dashboard.getDepreciationSummary(org);
        Map<String, Object> financial = analytics.getFinancialAnalytics("month", org);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> byCategory = (Map<String, Map<String, Object>>)
                ((Map<String, Object>) financial.get("breakdown")).get("byCategory");
        BigDecimal categorySum = byCategory.values().stream()
                .map(m -> (BigDecimal) m.get("netBookValue"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal categoryCostSum = byCategory.values().stream()
                .map(m -> (BigDecimal) m.get("value"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal nbv = (BigDecimal) depreciation.get("netBookValue");
        assertThat(nbv).isEqualByComparingTo(detailSum);
        assertThat((BigDecimal) summary.get("netBookValue")).isEqualByComparingTo(nbv);
        assertThat((BigDecimal) financial.get("netBookValue")).isEqualByComparingTo(nbv);
        assertThat(categorySum).isEqualByComparingTo(nbv);

        assertThat((BigDecimal) depreciation.get("monthlyDepreciation")).isEqualByComparingTo(monthlySum);
        assertThat((BigDecimal) financial.get("monthlyDepreciation")).isEqualByComparingTo(monthlySum);

        // Portfolio totals ignore the acquisition period ("month") and exclude the disposed asset.
        BigDecimal cost = new BigDecimal("3600").add(new BigDecimal("18000")).add(new BigDecimal("900"))
                .add(new BigDecimal("500"));
        assertThat((BigDecimal) summary.get("totalAssetValue")).isEqualByComparingTo(cost);
        assertThat((BigDecimal) financial.get("totalAssetValue")).isEqualByComparingTo(cost);
        assertThat(categoryCostSum).isEqualByComparingTo(cost);
        assertThat(financial.get("totalAssets")).isEqualTo(4L);
        assertThat(((BigDecimal) financial.get("totalDepreciation")).add(nbv)).isEqualByComparingTo(cost);

        assertThat(depreciation.get("assetsMissingDepreciationSetup")).isEqualTo(1L);
        assertThat(depreciation.get("assetsFullyDepreciated")).isEqualTo(1L);
        assertThat(financial.get("assetsMissingDepreciationSetup")).isEqualTo(1L);
    }

    @Test
    void acquisitionFiguresAreFilteredByPeriod() {
        Map<String, Object> month = analytics.getFinancialAnalytics("month", org);
        Map<String, Object> all = analytics.getFinancialAnalytics("all", org);

        assertThat(month.get("acquisitionsInPeriod")).isEqualTo(0);
        assertThat(all.get("acquisitionsInPeriod")).isEqualTo(5);
        assertThat((BigDecimal) all.get("totalAcquisition")).isEqualByComparingTo("33000"); // incl. disposed
        assertThat((BigDecimal) month.get("netBookValue")).isEqualByComparingTo((BigDecimal) all.get("netBookValue"));
    }

    private Asset asset(String currency, String cost, LocalDate purchased, Category category, Integer life,
                        String residual, DepreciationMethod method, AssetStatus status) {
        Asset a = new Asset();
        a.setId(UUID.randomUUID());
        a.setOrganisation(org);
        a.setName("Asset " + cost);
        a.setCurrency(currency);
        a.setPurchaseCost(new BigDecimal(cost));
        a.setPurchaseDate(purchased);
        a.setCategory(category);
        a.setUsefulLifeMonths(life);
        a.setResidualValue(residual != null ? new BigDecimal(residual) : null);
        a.setDepreciationMethod(method);
        a.setStatus(status);
        return a;
    }

    private Category category(String name, DepreciationPolicy policy) {
        Category c = new Category();
        c.setId(UUID.randomUUID());
        c.setName(name);
        c.setDepreciationPolicy(policy);
        return c;
    }

    private DepreciationPolicy policy(DepreciationMethod method, int life, String salvagePct) {
        DepreciationPolicy p = new DepreciationPolicy();
        p.setMethod(method);
        p.setUsefulLifeMonths(life);
        p.setSalvageValuePercent(new BigDecimal(salvagePct));
        return p;
    }
}
