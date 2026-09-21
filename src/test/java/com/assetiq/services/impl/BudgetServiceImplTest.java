package com.assetiq.services.impl;

import com.assetiq.dto.BudgetAdjustmentRequest;
import com.assetiq.dto.BudgetDto;
import com.assetiq.dto.BudgetLedgerEntryDto;
import com.assetiq.enums.BudgetLedgerKind;
import com.assetiq.enums.BudgetStatus;
import com.assetiq.models.Budget;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.BudgetRepository;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.ExpenseRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.NotificationService;
import com.assetiq.services.budget.LedgerFixture;
import com.assetiq.services.money.MoneyTestSupport;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetServiceImpl - edits, ledger and lifecycle")
class BudgetServiceImplTest {

    @Mock OrganisationRepository organisationRepository;
    @Mock BudgetRepository budgetRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock CurrencyResolver currencyResolver;
    @Mock ExpenseRepository expenseRepository;
    @Mock NotificationService notificationService;

    private BudgetServiceImpl service;
    private LedgerFixture ledger;
    private Organisation org;
    private Budget budget;

    @BeforeEach
    void setUp() {
        ledger = new LedgerFixture(budgetRepository, notificationService);
        service = new BudgetServiceImpl(organisationRepository, budgetRepository, departmentRepository,
                currencyResolver, expenseRepository, MoneyTestSupport.aggregatorWithRates(Map.of()), ledger.service);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        lenient().when(currencyResolver.resolveOrDefault(anyString())).thenAnswer(inv -> inv.getArgument(0));

        budget = new Budget();
        budget.setId(UUID.randomUUID());
        budget.setOrganisation(org);
        budget.setName("Ops");
        budget.setCurrency("GHS");
        budget.setTotalAmount(new BigDecimal("1000"));
        budget.setSpentAmount(new BigDecimal("300"));
        budget.setCommittedAmount(new BigDecimal("200"));
        budget.setStatus(BudgetStatus.ACTIVE);
        budget.setPeriodStart(LocalDate.of(2026, 1, 1));
        budget.setPeriodEnd(LocalDate.of(2026, 12, 31));
        LedgerFixture.lockable(budgetRepository, org, budget);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a full update never resets spent or committed, and persists the alert threshold")
    void update_preservesSpentAndCommitted() {
        BudgetDto dto = dto("1500");
        dto.setSpentAmount(BigDecimal.ZERO);      // client-sent totals are ignored
        dto.setCommittedAmount(BigDecimal.ZERO);
        dto.setAlertThresholdPct(65);

        BudgetDto result = service.update(budget.getId(), dto);

        assertThat(budget.getSpentAmount()).isEqualByComparingTo("300");
        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("200");
        assertThat(budget.getAlertThresholdPct()).isEqualTo(65);
        assertThat(result.getAvailableAmount()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("an update without a status keeps the current status")
    void update_withoutStatus_keepsStatus() {
        budget.setStatus(BudgetStatus.DRAFT);
        service.update(budget.getId(), dto("1000"));
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.DRAFT);
    }

    @Test
    @DisplayName("lowering the total below spend marks the budget EXCEEDED")
    void patch_totalBelowSpend_exceeded() {
        BudgetDto patch = new BudgetDto();
        patch.setTotalAmount(new BigDecimal("250"));

        service.patch(budget.getId(), patch);

        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.EXCEEDED);
        assertThat(budget.getSpentAmount()).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("patch persists the alert threshold")
    void patch_persistsThreshold() {
        BudgetDto patch = new BudgetDto();
        patch.setAlertThresholdPct(90);
        service.patch(budget.getId(), patch);
        assertThat(budget.getAlertThresholdPct()).isEqualTo(90);
    }

    @Test
    @DisplayName("a budget created without a status is ACTIVE, with zero spend and commitments")
    void create_defaultsActive() {
        BudgetDto dto = dto("500");
        dto.setSpentAmount(new BigDecimal("99"));

        BudgetDto result = service.create(dto);

        assertThat(result.getStatus()).isEqualTo(BudgetStatus.ACTIVE);
        assertThat(result.getSpentAmount()).isEqualByComparingTo("0");
        assertThat(result.getCommittedAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a period ending before it starts is rejected")
    void create_rejectsInvertedPeriod() {
        BudgetDto dto = dto("500");
        dto.setPeriodEnd(LocalDate.of(2025, 1, 1));
        assertThatThrownBy(() -> service.create(dto)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("period");
    }

    @Test
    @DisplayName("an adjustment adds spend and writes an ADJUSTMENT ledger entry")
    void adjustment_writesLedgerEntry() {
        service.recordAdjustment(budget.getId(), new BudgetAdjustmentRequest(new BigDecimal("50"), "Invoice 12"));

        assertThat(budget.getSpentAmount()).isEqualByComparingTo("350");
        assertThat(budget.getLastAdjustmentNote()).isEqualTo("Invoice 12");
        assertThat(ledger.kinds()).containsExactly(BudgetLedgerKind.ADJUSTMENT.name());
        assertThat(ledger.entries.get(0).getNote()).isEqualTo("Invoice 12");
    }

    @Test
    @DisplayName("the ledger endpoint returns the budget's entries")
    void getLedger_returnsEntries() {
        service.recordAdjustment(budget.getId(), new BudgetAdjustmentRequest(new BigDecimal("50"), "x"));
        when(budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(budget.getId(), org))
                .thenReturn(Optional.of(budget));
        when(ledger.ledgerRepository.findByBudgetAndOrganisationOrderByCreatedAtAsc(budget, org))
                .thenReturn(ledger.entries);

        List<BudgetLedgerEntryDto> rows = service.getLedger(budget.getId());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSpentDelta()).isEqualByComparingTo("50");
        assertThat(rows.get(0).getSpentAfter()).isEqualByComparingTo("350");
    }

    @Test
    @DisplayName("a budget with open commitments cannot be deleted")
    void delete_blockedByCommitments() {
        assertThatThrownBy(() -> service.delete(budget.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("open commitments");
        assertThat(budget.getDeletedAt()).isNull();
        verify(budgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("a budget without commitments is soft-deleted")
    void delete_withoutCommitments() {
        budget.setCommittedAmount(BigDecimal.ZERO);
        service.delete(budget.getId());
        assertThat(budget.getDeletedAt()).isNotNull();
    }

    private BudgetDto dto(String total) {
        BudgetDto dto = new BudgetDto();
        dto.setName("Ops");
        dto.setTotalAmount(new BigDecimal(total));
        dto.setCurrency("GHS");
        dto.setPeriodStart(LocalDate.of(2026, 1, 1));
        dto.setPeriodEnd(LocalDate.of(2026, 12, 31));
        return dto;
    }
}
