package com.assetiq.services.impl;

import com.assetiq.dto.ExpenseDto;
import com.assetiq.enums.ExpenseStatus;
import com.assetiq.models.Budget;
import com.assetiq.models.Expense;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.BudgetRepository;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.ExpenseRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExpenseServiceImpl - budget currency guard")
class ExpenseServiceImplCurrencyTest {

    @Mock ExpenseRepository expenseRepository;
    @Mock AssetRepository assetRepository;
    @Mock UserRepository userRepository;
    @Mock BudgetRepository budgetRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock NotificationService notificationService;
    @Mock CurrencyResolver currencyResolver;

    private ExpenseServiceImpl service;
    private Organisation org;
    private User submitter;
    private Budget ghsBudget;

    @BeforeEach
    void setUp() {
        service = new ExpenseServiceImpl(expenseRepository, assetRepository, userRepository, budgetRepository,
                departmentRepository, organisationRepository, notificationService, currencyResolver);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));

        submitter = user("submitter@example.com");
        ghsBudget = new Budget();
        ghsBudget.setId(UUID.randomUUID());
        ghsBudget.setOrganisation(org);
        ghsBudget.setCurrency("GHS");
        ghsBudget.setTotalAmount(new BigDecimal("1000"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("submitting an expense in another currency than its budget is rejected")
    void submit_rejectsCurrencyMismatch() {
        authenticate(submitter);
        when(userRepository.findByEmailAndOrganisationId(submitter.getEmail(), org.getId()))
                .thenReturn(Optional.of(submitter));
        when(currencyResolver.resolveOrDefault("USD")).thenReturn("USD");
        when(budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(ghsBudget.getId(), org))
                .thenReturn(Optional.of(ghsBudget));

        assertThatThrownBy(() -> service.submit(request("USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining("GHS");

        verify(expenseRepository, never()).save(any());
        verify(budgetRepository, never()).save(any());
        assertThat(ghsBudget.getCommittedAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("an expense in the budget's currency commits funds as before")
    void submit_acceptsMatchingCurrency() {
        authenticate(submitter);
        when(userRepository.findByEmailAndOrganisationId(submitter.getEmail(), org.getId()))
                .thenReturn(Optional.of(submitter));
        when(currencyResolver.resolveOrDefault("ghs")).thenReturn("GHS");
        when(budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(ghsBudget.getId(), org))
                .thenReturn(Optional.of(ghsBudget));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        ExpenseDto result = service.submit(request("ghs"));

        assertThat(result.getCurrency()).isEqualTo("GHS");
        assertThat(ghsBudget.getCommittedAmount()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("approving a legacy mismatched expense is refused without touching the budget")
    void approve_rejectsLegacyCurrencyMismatch() {
        User approver = user("approver@example.com");
        authenticate(approver);
        when(userRepository.findByEmailAndOrganisationId(approver.getEmail(), org.getId()))
                .thenReturn(Optional.of(approver));
        Expense legacy = new Expense();
        legacy.setId(UUID.randomUUID());
        legacy.setOrganisation(org);
        legacy.setSubmittedBy(submitter);
        legacy.setStatus(ExpenseStatus.SUBMITTED);
        legacy.setAmount(new BigDecimal("50"));
        legacy.setCurrency("EUR");
        legacy.setLinkedBudget(ghsBudget);
        when(expenseRepository.findByIdAndOrganisationAndDeletedAtIsNull(legacy.getId(), org))
                .thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.approve(legacy.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currencies must match");
        verify(budgetRepository, never()).save(any());
        assertThat(ghsBudget.getSpentAmount()).isEqualByComparingTo("0");
    }

    private ExpenseDto request(String currency) {
        ExpenseDto dto = new ExpenseDto();
        dto.setTitle("Taxi");
        dto.setAmount(new BigDecimal("50"));
        dto.setCurrency(currency);
        dto.setLinkedBudgetId(ghsBudget.getId());
        return dto;
    }

    private User user(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setOrganisation(org);
        return user;
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user.getEmail(), "n/a", List.of()));
    }
}
