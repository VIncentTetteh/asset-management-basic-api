package com.assetiq.services.budget;

import com.assetiq.enums.BudgetLedgerKind;
import com.assetiq.enums.BudgetStatus;
import com.assetiq.enums.NotificationType;
import com.assetiq.models.Budget;
import com.assetiq.models.BudgetLedgerEntry;
import com.assetiq.models.Organisation;
import com.assetiq.models.PurchaseOrder;
import com.assetiq.repositories.BudgetRepository;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetLedgerService")
class BudgetLedgerServiceTest {

    @Mock BudgetRepository budgetRepository;
    @Mock NotificationService notificationService;

    private LedgerFixture ledger;
    private Organisation org;
    private Budget budget;

    @BeforeEach
    void setUp() {
        ledger = new LedgerFixture(budgetRepository, notificationService);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        budget = new Budget();
        budget.setId(UUID.randomUUID());
        budget.setOrganisation(org);
        budget.setName("IT hardware");
        budget.setCurrency("GHS");
        budget.setTotalAmount(new BigDecimal("1000"));
        budget.setStatus(BudgetStatus.ACTIVE);
        LedgerFixture.lockable(budgetRepository, org, budget);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("finance@example.com", "n/a", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a commitment reserves funds, refreshes the locked row and records a ledger entry")
    void commit_reservesFundsAndRecordsEntry() {
        ledger.service.post(org, budget.getId(), BudgetPosting.forPurchaseOrder(BudgetLedgerKind.PO_COMMIT, po("400")));

        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("400");
        assertThat(budget.getSpentAmount()).isEqualByComparingTo("0");
        verify(ledger.entityManager).refresh(budget);
        BudgetLedgerEntry entry = ledger.entries.get(0);
        assertThat(entry.getKind()).isEqualTo(BudgetLedgerKind.PO_COMMIT);
        assertThat(entry.getCommittedAfter()).isEqualByComparingTo("400");
        assertThat(entry.getSpentAfter()).isEqualByComparingTo("0");
        assertThat(entry.getCurrency()).isEqualTo("GHS");
        assertThat(entry.getSourceType()).isEqualTo(BudgetPosting.SOURCE_PURCHASE_ORDER);
        assertThat(entry.getActorEmail()).isEqualTo("finance@example.com");
        assertThat(entry.getIdempotencyKey()).startsWith("PO_COMMIT:");
    }

    @Test
    @DisplayName("a funds-checked commitment beyond the available amount is refused and changes nothing")
    void commit_insufficientFunds_refused() {
        budget.setSpentAmount(new BigDecimal("700"));
        budget.setCommittedAmount(new BigDecimal("200"));

        assertThatThrownBy(() -> ledger.service.post(org, budget.getId(),
                BudgetPosting.forPurchaseOrder(BudgetLedgerKind.PO_COMMIT, po("150"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient funds")
                .hasMessageContaining("100.00 GHS available");

        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("200");
        assertThat(ledger.entries).isEmpty();
        verify(budgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("commitments against DRAFT or CLOSED budgets are refused")
    void commit_requiresOpenBudget() {
        for (BudgetStatus closed : List.of(BudgetStatus.DRAFT, BudgetStatus.CLOSED)) {
            budget.setStatus(closed);
            assertThatThrownBy(() -> ledger.service.post(org, budget.getId(),
                    BudgetPosting.forPurchaseOrder(BudgetLedgerKind.PO_COMMIT, po("10"))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(closed.name());
        }
        assertThat(ledger.entries).isEmpty();
    }

    @Test
    @DisplayName("a posting in another currency is refused")
    void post_rejectsCurrencyMismatch() {
        PurchaseOrder usd = po("10");
        usd.setCurrency("USD");

        assertThatThrownBy(() -> ledger.service.post(org, budget.getId(),
                BudgetPosting.forPurchaseOrder(BudgetLedgerKind.PO_COMMIT, usd)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currencies must match");
    }

    @Test
    @DisplayName("spend past the total marks the budget EXCEEDED and a reversal restores ACTIVE")
    void spend_setsAndClearsExceeded() {
        budget.setCommittedAmount(new BigDecimal("1200"));
        PurchaseOrder order = po("1200");

        ledger.service.post(org, budget.getId(), BudgetPosting.forPurchaseOrder(BudgetLedgerKind.PO_SPEND, order));
        assertThat(budget.getSpentAmount()).isEqualByComparingTo("1200");
        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("0");
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.EXCEEDED);

        ledger.service.post(org, budget.getId(),
                BudgetPosting.forPurchaseOrder(BudgetLedgerKind.PO_SPEND_REVERSAL, order));
        assertThat(budget.getSpentAmount()).isEqualByComparingTo("0");
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.ACTIVE);
    }

    @Test
    @DisplayName("the threshold alert fires once, when spend crosses it")
    void thresholdAlert_firesOnCrossingOnly() {
        budget.setAlertThresholdPct(50);

        ledger.service.post(org, budget.getId(), BudgetPosting.adjustment(new BigDecimal("400"), "below"));
        ledger.service.post(org, budget.getId(), BudgetPosting.adjustment(new BigDecimal("200"), "crosses"));
        ledger.service.post(org, budget.getId(), BudgetPosting.adjustment(new BigDecimal("100"), "already over"));

        verify(notificationService, times(1)).notifyOrgAdmins(eq(org), eq(NotificationType.BUDGET_THRESHOLD),
                anyString(), anyString(), eq(budget.getId()), any());
    }

    @Test
    @DisplayName("replaying the same workflow event is ignored")
    void replay_isIdempotent() {
        PurchaseOrder order = po("100");
        ledger.service.post(org, budget.getId(), BudgetPosting.forPurchaseOrder(BudgetLedgerKind.PO_COMMIT, order));
        ledger.service.post(org, budget.getId(), BudgetPosting.forPurchaseOrder(BudgetLedgerKind.PO_COMMIT, order));

        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("100");
        assertThat(ledger.entries).hasSize(1);
    }

    @Test
    @DisplayName("a release against a deleted budget is skipped; a new commitment is refused")
    void deletedBudget_skipsReleaseRefusesCommit() {
        budget.setDeletedAt(Instant.now());
        budget.setCommittedAmount(new BigDecimal("100"));

        assertThat(ledger.service.post(org, budget.getId(),
                BudgetPosting.forPurchaseOrder(BudgetLedgerKind.PO_RELEASE, po("100")))).isNull();
        assertThatThrownBy(() -> ledger.service.post(org, budget.getId(),
                BudgetPosting.forPurchaseOrder(BudgetLedgerKind.PO_COMMIT, po("5"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ledger.entries).isEmpty();
    }

    @Test
    @DisplayName("a legacy release larger than the commitment clamps at zero")
    void release_clampsAtZero() {
        budget.setCommittedAmount(new BigDecimal("30"));

        ledger.service.post(org, budget.getId(), BudgetPosting.forPurchaseOrder(BudgetLedgerKind.PO_RELEASE, po("50")));

        assertThat(budget.getCommittedAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ledger DTOs carry signed deltas")
    void toDto_signsDeltas() {
        budget.setCommittedAmount(new BigDecimal("100"));
        ledger.service.post(org, budget.getId(), BudgetPosting.forPurchaseOrder(BudgetLedgerKind.PO_SPEND, po("100")));

        var dto = BudgetLedgerService.toDto(ledger.entries.get(0));
        assertThat(dto.getCommittedDelta()).isEqualByComparingTo("-100");
        assertThat(dto.getSpentDelta()).isEqualByComparingTo("100");
    }

    private PurchaseOrder po(String amount) {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(UUID.randomUUID());
        po.setOrganisation(org);
        po.setPoNumber("PO-" + amount);
        po.setTotalAmount(new BigDecimal(amount));
        po.setCurrency("GHS");
        return po;
    }
}
