package com.assetiq.services.impl;

import com.assetiq.dto.BudgetDto;
import com.assetiq.dto.BudgetSummaryDto;
import com.assetiq.models.Budget;
import com.assetiq.models.Department;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.BudgetRepository;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.ExpenseRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.money.MoneyTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Golden test: GHS tenant with GHS, USD (rate 15), EUR (rate 16) and JPY (no rate) budgets.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetServiceImpl - mixed currencies")
class BudgetServiceImplCurrencyTest {

    @Mock OrganisationRepository organisationRepository;
    @Mock BudgetRepository budgetRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock CurrencyResolver currencyResolver;
    @Mock ExpenseRepository expenseRepository;

    private BudgetServiceImpl service;
    private Organisation org;

    @BeforeEach
    void setUp() {
        service = new BudgetServiceImpl(organisationRepository, budgetRepository, departmentRepository,
                currencyResolver, expenseRepository,
                MoneyTestSupport.aggregatorWithRates(Map.of("USD", "15", "EUR", "16")));
        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setBillingCurrency("GHS");
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("summary converts every budget and excludes the one without a rate")
    void summary_convertsAndExcludes() {
        Department it = new Department();
        it.setId(UUID.randomUUID());
        it.setName("IT");
        when(budgetRepository.findByOrganisationAndDeletedAtIsNullOrderByPeriodStartDesc(org))
                .thenReturn(List.of(
                        budget(it, "GHS", "5000", "1000", "500"),
                        budget(it, "USD", "200", "100", "0"),       // 3000 / 1500 / 0
                        budget(null, "EUR", "100", "50", "10"),     // 1600 / 800 / 160
                        budget(null, "JPY", "100000", "1", "1")));  // excluded

        BudgetSummaryDto s = service.getSummary();

        assertThat(s.getCurrency()).isEqualTo("GHS");
        assertThat(s.isComplete()).isFalse();
        assertThat(s.getMissingRates()).containsExactly("JPY->GHS");
        assertThat(s.getTotalAllocated()).isEqualByComparingTo("9600.00");
        assertThat(s.getTotalSpent()).isEqualByComparingTo("3300.00");
        assertThat(s.getTotalCommitted()).isEqualByComparingTo("660.00");
        assertThat(s.getTotalAvailable()).isEqualByComparingTo("5640.00");
        assertThat(s.getTotalAllocated().scale()).isEqualTo(2);

        assertThat(s.getByDepartment()).hasSize(2);
        BudgetSummaryDto.DepartmentSummary itRow = s.getByDepartment().get(0);
        assertThat(itRow.getDepartmentName()).isEqualTo("IT");
        assertThat(itRow.getAllocated()).isEqualByComparingTo("8000.00");
        assertThat(itRow.getSpent()).isEqualByComparingTo("2500.00");
        assertThat(itRow.getCommitted()).isEqualByComparingTo("500.00");
        assertThat(itRow.getAvailable()).isEqualByComparingTo("5000.00");
        BudgetSummaryDto.DepartmentSummary orgRow = s.getByDepartment().get(1);
        assertThat(orgRow.getDepartmentId()).isNull();
        assertThat(orgRow.getAllocated()).isEqualByComparingTo("1600.00");
        assertThat(orgRow.getAvailable()).isEqualByComparingTo("640.00");
    }

    @Test
    @DisplayName("patch normalises the currency code and rejects non-ISO codes")
    void patch_normalisesAndValidatesCurrency() {
        Budget existing = budget(null, "GHS", "10", "0", "0");
        UUID id = existing.getId();
        when(budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)).thenReturn(Optional.of(existing));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));

        BudgetDto ok = new BudgetDto();
        ok.setCurrency(" usd ");
        service.patch(id, ok);
        assertThat(existing.getCurrency()).isEqualTo("USD");

        BudgetDto bad = new BudgetDto();
        bad.setCurrency("DOLLARS");
        assertThatThrownBy(() -> service.patch(id, bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO-4217");
        assertThat(existing.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("an unknown 3-letter code is rejected before anything is saved")
    void patch_rejectsUnknownCode() {
        Budget existing = budget(null, "GHS", "10", "0", "0");
        when(budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(existing.getId(), org))
                .thenReturn(Optional.of(existing));
        BudgetDto bad = new BudgetDto();
        bad.setCurrency("ZZZ");

        assertThatThrownBy(() -> service.patch(existing.getId(), bad)).isInstanceOf(IllegalArgumentException.class);
        verify(budgetRepository, never()).save(any());
    }

    private Budget budget(Department dept, String currency, String total, String spent, String committed) {
        Budget b = new Budget();
        b.setId(UUID.randomUUID());
        b.setOrganisation(org);
        b.setDepartment(dept);
        b.setCurrency(currency);
        b.setTotalAmount(new BigDecimal(total));
        b.setSpentAmount(new BigDecimal(spent));
        b.setCommittedAmount(new BigDecimal(committed));
        return b;
    }
}
