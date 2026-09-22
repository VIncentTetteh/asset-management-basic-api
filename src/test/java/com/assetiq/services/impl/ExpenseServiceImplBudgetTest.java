package com.assetiq.services.impl;

import com.assetiq.enums.BudgetStatus;
import com.assetiq.enums.ExpenseStatus;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.Budget;
import com.assetiq.models.Expense;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.*;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.NotificationService;
import com.assetiq.services.budget.LedgerFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExpenseServiceImpl - budget effects of the workflow")
class ExpenseServiceImplBudgetTest {

    @Mock ExpenseRepository expenseRepository;
    @Mock AssetRepository assetRepository;
    @Mock UserRepository userRepository;
    @Mock BudgetRepository budgetRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock NotificationService notificationService;
    @Mock CurrencyResolver currencyResolver;

    private ExpenseServiceImpl service;
    private LedgerFixture ledger;
    private Organisation org;
    private Budget budget;
    private Expense expense;

    @BeforeEach
    void setUp() {
        ledger = new LedgerFixture(budgetRepository, notificationService);
        service = new ExpenseServiceImpl(expenseRepository, assetRepository, userRepository, budgetRepository,
                departmentRepository, organisationRepository, notificationService, currencyResolver, ledger.service);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        budget = new Budget();
        budget.setId(UUID.randomUUID());
        budget.setOrganisation(org);
        budget.setName("Travel");
        budget.setCurrency("GHS");
        budget.setTotalAmount(new BigDecimal("1000"));
        budget.setStatus(BudgetStatus.ACTIVE);
        LedgerFixture.lockable(budgetRepository, org, budget);

        User submitter = user("submitter@example.com");
        expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setOrganisation(org);
        expense.setTitle("Taxi");
        expense.setAmount(new BigDecimal("120"));
        expense.setCurrency("GHS");
        expense.setSubmittedBy(submitter);
        expense.setLinkedBudget(budget);
        when(expenseRepository.findByIdAndOrganisationAndDeletedAtIsNull(expense.getId(), org))
                .thenReturn(Optional.of(expense));

        User approver = user("approver@example.com");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(approver.getEmail(), "n/a", List.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("deleting a submitted expense releases its commitment")
    void deleteSubmitted_releasesCommitment() {
        expense.setStatus(ExpenseStatus.SUBMITTED);
        budget.setCommittedAmount(new BigDecimal("120"));

        service.delete(expense.getId());

        assertThat(expense.getDeletedAt()).isNotNull();
        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("0");
        assertThat(ledger.kinds()).containsExactly("EXPENSE_RELEASE");
    }

    @Test
    @DisplayName("deleting an approved expense reverses its spend")
    void deleteApproved_reversesSpend() {
        expense.setStatus(ExpenseStatus.APPROVED);
        budget.setSpentAmount(new BigDecimal("120"));

        service.delete(expense.getId());

        assertThat(budget.getSpentAmount()).isEqualByComparingTo("0");
        assertThat(ledger.kinds()).containsExactly("EXPENSE_SPEND_REVERSAL");
    }

    @Test
    @DisplayName("deleting a rejected expense touches no budget")
    void deleteRejected_noEffect() {
        expense.setStatus(ExpenseStatus.REJECTED);
        service.delete(expense.getId());
        assertThat(ledger.entries).isEmpty();
    }

    @Test
    @DisplayName("approve moves the commitment into spend; reject releases it")
    void approveAndReject() {
        expense.setStatus(ExpenseStatus.SUBMITTED);
        budget.setCommittedAmount(new BigDecimal("120"));

        service.approve(expense.getId());
        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("0");
        assertThat(budget.getSpentAmount()).isEqualByComparingTo("120");

        Expense other = new Expense();
        other.setId(UUID.randomUUID());
        other.setOrganisation(org);
        other.setAmount(new BigDecimal("30"));
        other.setCurrency("GHS");
        other.setStatus(ExpenseStatus.SUBMITTED);
        other.setLinkedBudget(budget);
        budget.setCommittedAmount(new BigDecimal("30"));
        when(expenseRepository.findByIdAndOrganisationAndDeletedAtIsNull(other.getId(), org))
                .thenReturn(Optional.of(other));

        service.reject(other.getId(), "no receipt");
        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("0");
        assertThat(ledger.kinds()).containsExactly("EXPENSE_SPEND", "EXPENSE_RELEASE");
    }

    @Test
    @DisplayName("only submitted expenses can be approved")
    void approveDraft_refused() {
        expense.setStatus(ExpenseStatus.DRAFT);
        assertThatThrownBy(() -> service.approve(expense.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
        assertThat(ledger.entries).isEmpty();
    }

    @Test
    @DisplayName("submitting against a closed budget is refused")
    void submitAgainstClosedBudget_refused() {
        budget.setStatus(BudgetStatus.CLOSED);
        when(budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(budget.getId(), org))
                .thenReturn(Optional.of(budget));
        when(currencyResolver.resolveOrDefault("GHS")).thenReturn("GHS");
        com.assetiq.dto.ExpenseDto dto = new com.assetiq.dto.ExpenseDto();
        dto.setTitle("Fuel");
        dto.setAmount(new BigDecimal("10"));
        dto.setCurrency("GHS");
        dto.setLinkedBudgetId(budget.getId());

        assertThatThrownBy(() -> service.submit(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    @DisplayName("submitting more than the budget has available is refused (409), as PO approval is")
    void submitOverAvailable_refused() {
        budget.setCommittedAmount(new BigDecimal("900"));
        when(budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(budget.getId(), org))
                .thenReturn(Optional.of(budget));
        when(currencyResolver.resolveOrDefault("GHS")).thenReturn("GHS");
        com.assetiq.dto.ExpenseDto dto = new com.assetiq.dto.ExpenseDto();
        dto.setTitle("Laptop");
        dto.setAmount(new BigDecimal("100.01"));
        dto.setCurrency("GHS");
        dto.setLinkedBudgetId(budget.getId());

        assertThatThrownBy(() -> service.submit(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient funds");
        assertThat(ledger.entries).isEmpty();

        dto.setAmount(new BigDecimal("100.00"));
        service.submit(dto);
        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("an unknown department or asset is a field error, not silently dropped")
    void submitUnknownLinks_refused() {
        when(currencyResolver.resolveOrDefault(any())).thenReturn("GHS");
        com.assetiq.dto.ExpenseDto dto = new com.assetiq.dto.ExpenseDto();
        dto.setTitle("Fuel");
        dto.setAmount(new BigDecimal("10"));
        dto.setDepartmentId(UUID.randomUUID());
        assertThatThrownBy(() -> service.submit(dto))
                .isInstanceOf(com.assetiq.exceptions.FieldValidationException.class)
                .extracting("field").isEqualTo("departmentId");

        dto.setDepartmentId(null);
        dto.setLinkedAssetId(UUID.randomUUID());
        assertThatThrownBy(() -> service.submit(dto))
                .isInstanceOf(com.assetiq.exceptions.FieldValidationException.class)
                .extracting("field").isEqualTo("linkedAssetId");
    }

    private User user(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setOrganisation(org);
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByEmailAndOrganisationId(email, org.getId())).thenReturn(Optional.of(user));
        return user;
    }
}
