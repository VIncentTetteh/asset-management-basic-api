package com.assetiq.services.budget;

import com.assetiq.models.Budget;
import com.assetiq.models.BudgetLedgerEntry;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.BudgetLedgerEntryRepository;
import com.assetiq.repositories.BudgetRepository;
import com.assetiq.services.NotificationService;
import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * A real {@link BudgetLedgerService} over mocked repositories, with an in-memory
 * ledger. Service tests use it so they assert the budget figures and ledger rows a
 * workflow actually produces, not just that a mock was called.
 */
public final class LedgerFixture {

    public final List<BudgetLedgerEntry> entries = new ArrayList<>();
    public final BudgetLedgerEntryRepository ledgerRepository = mock(BudgetLedgerEntryRepository.class);
    public final EntityManager entityManager = mock(EntityManager.class);
    public final BudgetLedgerService service;

    public LedgerFixture(BudgetRepository budgetRepository, NotificationService notificationService) {
        lenient().when(ledgerRepository.save(any(BudgetLedgerEntry.class))).thenAnswer(inv -> {
            BudgetLedgerEntry entry = inv.getArgument(0);
            entries.add(entry);
            return entry;
        });
        lenient().when(ledgerRepository.existsByOrganisationAndIdempotencyKey(any(), any()))
                .thenAnswer(inv -> entries.stream()
                        .anyMatch(e -> inv.getArgument(1).equals(e.getIdempotencyKey())));
        lenient().when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new BudgetLedgerService(budgetRepository, ledgerRepository, notificationService, entityManager);
    }

    /** Makes {@code budget} lockable by the ledger. */
    public static void lockable(BudgetRepository budgetRepository, Organisation org, Budget budget) {
        lenient().when(budgetRepository.findByIdForUpdate(eq(budget.getId()), eq(org)))
                .thenReturn(Optional.of(budget));
    }

    /** The kinds posted so far, in order. */
    public List<String> kinds() {
        return entries.stream().map(e -> e.getKind().name()).toList();
    }
}
