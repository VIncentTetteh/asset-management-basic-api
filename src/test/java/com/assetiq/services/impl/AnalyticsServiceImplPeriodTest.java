package com.assetiq.services.impl;

import com.assetiq.enums.BudgetStatus;
import com.assetiq.models.Budget;
import com.assetiq.models.Organisation;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Two figures that used to mislead, pinned so they cannot drift back.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsServiceImpl - period handling")
class AnalyticsServiceImplPeriodTest {

    @Mock AssetRepository assetRepository;
    @Mock MaintenanceRecordRepository maintenanceRecordRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock BudgetRepository budgetRepository;
    @Mock DisposalRecordRepository disposalRecordRepository;

    private AnalyticsServiceImpl service;
    private Organisation org;

    @BeforeEach
    void setUp() {
        service = new AnalyticsServiceImpl(assetRepository, maintenanceRecordRepository,
                purchaseOrderRepository, budgetRepository, disposalRecordRepository,
                MoneyTestSupport.aggregatorWithRates(Map.of()));
        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setBillingCurrency("GHS");
        lenient().when(assetRepository.findAllByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of());
        lenient().when(maintenanceRecordRepository.findByOrganisationAndDeletedAtIsNull(org))
                .thenReturn(new LinkedHashSet<>());
        lenient().when(disposalRecordRepository
                .findByOrganisationAndDisposalDateBetweenAndDeletedAtIsNull(eq(org), any(), any()))
                .thenReturn(Set.of());
    }

    @Test
    @DisplayName("budget totals cover the reported period only, and exclude drafts")
    void budgetsAreScopedToThePeriodAndExcludeDrafts() {
        when(budgetRepository.findOverlapping(eq(org), any(), any())).thenReturn(List.of(
                budget(BudgetStatus.ACTIVE, "1000", "400", "100"),
                budget(BudgetStatus.DRAFT, "50000", "0", "0")));

        Map<String, Object> r = service.getFinancialAnalytics("year", org);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(budgetRepository).findOverlapping(eq(org), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDate.now().minusYears(1));
        assertThat(to.getValue()).isEqualTo(LocalDate.now());

        assertThat(r.get("budgetsInPeriod")).isEqualTo(1);
        assertThat(money(r, "totalBudget")).isEqualTo("1000.00");
        assertThat(money(r, "totalActualSpend")).isEqualTo("400.00");
        assertThat(money(r, "totalCommittedSpend")).isEqualTo("100.00");
        // Commitments count against the budget: (400 + 100) / 1000.
        assertThat(r.get("budgetUtilization")).isEqualTo(50.0);
    }

    @Test
    @DisplayName("assets needing maintenance is asked of the whole register, not just the period")
    void overdueMaintenanceIgnoresTheReportingPeriod() {
        when(maintenanceRecordRepository.countAssetsNeedingMaintenance(eq(org), any())).thenReturn(7L);

        // Narrowing the window from a year to a week must not shrink the backlog:
        // a job last touched three years ago is still overdue today.
        assertThat(service.getMaintenanceAnalytics("year", org).get("assetsNeedingMaintenance"))
                .isEqualTo(7L);
        assertThat(service.getMaintenanceAnalytics("week", org).get("assetsNeedingMaintenance"))
                .isEqualTo(7L);
    }

    private Budget budget(BudgetStatus status, String total, String spent, String committed) {
        Budget b = new Budget();
        b.setOrganisation(org);
        b.setCurrency("GHS");
        b.setStatus(status);
        b.setTotalAmount(new BigDecimal(total));
        b.setSpentAmount(new BigDecimal(spent));
        b.setCommittedAmount(new BigDecimal(committed));
        return b;
    }

    private static String money(Map<String, Object> r, String key) {
        return ((BigDecimal) r.get(key)).toPlainString();
    }
}
