package com.assetiq.services.budget;

import com.assetiq.dto.BudgetLedgerEntryDto;
import com.assetiq.enums.BudgetStatus;
import com.assetiq.enums.NotificationType;
import com.assetiq.models.Budget;
import com.assetiq.models.BudgetLedgerEntry;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.BudgetLedgerEntryRepository;
import com.assetiq.repositories.BudgetRepository;
import com.assetiq.services.NotificationService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The only writer of a budget's {@code committedAmount} and {@code spentAmount}.
 *
 * <p>Every posting locks the budget row ({@code SELECT ... FOR UPDATE}), applies the
 * movement, keeps EXCEEDED in step, fires the threshold alert when spend crosses it,
 * and appends a {@link BudgetLedgerEntry} - all in the caller's transaction, so the
 * totals and their history commit or roll back together. Two approvals racing on
 * one budget therefore serialise instead of losing an update, and a workflow event
 * replayed with the same idempotency key is ignored.
 */
@Service
public class BudgetLedgerService {

    private static final Logger log = LoggerFactory.getLogger(BudgetLedgerService.class);
    private static final int DEFAULT_ALERT_THRESHOLD_PCT = 80;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BudgetRepository budgetRepository;
    private final BudgetLedgerEntryRepository ledgerRepository;
    private final NotificationService notificationService;
    private final EntityManager entityManager;

    public BudgetLedgerService(BudgetRepository budgetRepository,
                               BudgetLedgerEntryRepository ledgerRepository,
                               NotificationService notificationService,
                               EntityManager entityManager) {
        this.budgetRepository = budgetRepository;
        this.ledgerRepository = ledgerRepository;
        this.notificationService = notificationService;
        this.entityManager = entityManager;
    }

    /**
     * Locks and returns a live budget for an edit that must not race with postings
     * (a PUT/PATCH saves every column, so an unlocked edit could overwrite a
     * concurrent commitment).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Budget lockForEdit(UUID budgetId, Organisation org) {
        Budget budget = lock(budgetId, org);
        if (budget.isDeleted()) {
            throw new IllegalArgumentException("Budget not found: " + budgetId);
        }
        return budget;
    }

    /** True when {@code key} has already been posted for {@code org}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean hasPosted(Organisation org, String key) {
        return ledgerRepository.existsByOrganisationAndIdempotencyKey(org, key);
    }

    /**
     * Applies {@code posting} to the budget.
     *
     * @return the updated budget, or {@code null} when a release/reversal targets a
     *         deleted budget (nothing left to adjust)
     * @throws IllegalArgumentException the budget does not exist in this organisation
     * @throws IllegalStateException    currency mismatch, a commitment against a DRAFT or
     *                                  CLOSED budget, or insufficient available funds
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Budget post(Organisation org, UUID budgetId, BudgetPosting posting) {
        if (posting.amount() == null || posting.amount().signum() <= 0) {
            throw new IllegalArgumentException("Budget posting amount must be greater than zero");
        }
        Budget budget = lock(budgetId, org);

        if (budget.isDeleted()) {
            if (posting.kind().isCommitment()) {
                throw new IllegalArgumentException("Budget not found: " + budgetId);
            }
            log.warn("Skipping {} of {} against deleted budget {}", posting.kind(), posting.amount(), budgetId);
            return null;
        }
        if (posting.idempotencyKey() != null && hasPosted(org, posting.idempotencyKey())) {
            log.info("Budget posting {} already applied; ignoring replay", posting.idempotencyKey());
            return budget;
        }
        requireSameCurrency(budget, posting);
        if (posting.kind().isCommitment()) {
            requireOpen(budget);
        }
        if (posting.requireFunds()) {
            requireFunds(budget, posting);
        }

        BigDecimal spentBefore = nz(budget.getSpentAmount());
        budget.setCommittedAmount(move(budget, nz(budget.getCommittedAmount()),
                posting.kind().committedDirection(), posting, "committed"));
        budget.setSpentAmount(move(budget, spentBefore,
                posting.kind().spentDirection(), posting, "spent"));
        budget.reconcileExceededStatus();
        Budget saved = budgetRepository.save(budget);

        ledgerRepository.save(entryFor(org, saved, posting));
        notifyIfThresholdCrossed(org, saved, spentBefore);
        return saved;
    }

    /** The budget's ledger, oldest first. */
    @Transactional(readOnly = true)
    public List<BudgetLedgerEntryDto> entries(Budget budget, Organisation org) {
        return ledgerRepository.findByBudgetAndOrganisationOrderByCreatedAtAsc(budget, org).stream()
                .map(BudgetLedgerService::toDto)
                .toList();
    }

    // ── internals ────────────────────────────────────────────────────────────

    private Budget lock(UUID budgetId, Organisation org) {
        if (budgetId == null) {
            throw new IllegalArgumentException("Budget id is required");
        }
        Budget budget = budgetRepository.findByIdForUpdate(budgetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + budgetId));
        // The row is now locked, but this persistence context may hold a copy read
        // earlier in the transaction; refresh so the totals we add to are current.
        entityManager.refresh(budget);
        return budget;
    }

    private static void requireSameCurrency(Budget budget, BudgetPosting posting) {
        if (posting.currency() == null) return; // adjustments are in the budget's currency
        if (budget.getCurrency() == null
                || !budget.getCurrency().trim().equalsIgnoreCase(posting.currency().trim())) {
            throw new IllegalStateException(String.format(
                    "%s currency %s does not match budget '%s' currency %s; currencies must match",
                    sourceLabel(posting), posting.currency(), budget.getName(), budget.getCurrency()));
        }
    }

    private static void requireOpen(Budget budget) {
        if (budget.getStatus() == BudgetStatus.DRAFT || budget.getStatus() == BudgetStatus.CLOSED) {
            throw new IllegalStateException(String.format(
                    "Budget '%s' is %s and cannot take new commitments; activate it first",
                    budget.getName(), budget.getStatus()));
        }
    }

    private static void requireFunds(Budget budget, BudgetPosting posting) {
        BigDecimal available = budget.availableAmount();
        if (posting.amount().compareTo(available) > 0) {
            throw new IllegalStateException(String.format(
                    "Insufficient funds in budget '%s': %s %s available, %s %s required",
                    budget.getName(), money(available.max(BigDecimal.ZERO)), budget.getCurrency(),
                    money(posting.amount()), budget.getCurrency()));
        }
    }

    private static BigDecimal move(Budget budget, BigDecimal current, int direction,
                                   BudgetPosting posting, String bucket) {
        if (direction == 0) return current;
        BigDecimal next = direction > 0 ? current.add(posting.amount()) : current.subtract(posting.amount());
        if (next.signum() < 0) {
            // Only reachable for records that predate commitment tracking. Clamp rather
            // than fail, so a legacy order or expense can still be cleaned up.
            log.warn("Budget {} {} would go negative ({}) applying {}; clamping to zero",
                    budget.getId(), bucket, next, posting.kind());
            return BigDecimal.ZERO;
        }
        return next;
    }

    private BudgetLedgerEntry entryFor(Organisation org, Budget budget, BudgetPosting posting) {
        BudgetLedgerEntry entry = new BudgetLedgerEntry();
        entry.setOrganisation(org);
        entry.setBudget(budget);
        entry.setKind(posting.kind());
        entry.setAmount(posting.amount());
        entry.setCurrency(budget.getCurrency() != null ? budget.getCurrency() : posting.currency());
        entry.setSpentAfter(nz(budget.getSpentAmount()));
        entry.setCommittedAfter(nz(budget.getCommittedAmount()));
        entry.setSourceType(posting.sourceType());
        entry.setSourceId(posting.sourceId());
        entry.setNote(posting.note());
        entry.setIdempotencyKey(posting.idempotencyKey());
        entry.setActorEmail(currentActor());
        return entry;
    }

    private void notifyIfThresholdCrossed(Organisation org, Budget budget, BigDecimal spentBefore) {
        BigDecimal total = budget.getTotalAmount();
        if (total == null || total.signum() <= 0) return;
        int threshold = budget.getAlertThresholdPct() != null
                ? budget.getAlertThresholdPct() : DEFAULT_ALERT_THRESHOLD_PCT;
        BigDecimal before = pct(spentBefore, total);
        BigDecimal after = pct(nz(budget.getSpentAmount()), total);
        BigDecimal limit = BigDecimal.valueOf(threshold);
        if (before.compareTo(limit) >= 0 || after.compareTo(limit) < 0) return;
        try {
            notificationService.notifyOrgAdmins(org, NotificationType.BUDGET_THRESHOLD,
                    "Budget Threshold Reached",
                    "Budget '" + budget.getName() + "' has reached "
                            + after.setScale(1, RoundingMode.HALF_UP).toPlainString()
                            + "% utilization (threshold: " + threshold + "%).",
                    budget.getId(), "/budgets");
        } catch (RuntimeException e) {
            log.warn("Budget threshold notification suppressed for budget {}: {}", budget.getId(), e.getMessage());
        }
    }

    static BudgetLedgerEntryDto toDto(BudgetLedgerEntry e) {
        BigDecimal amount = e.getAmount();
        return BudgetLedgerEntryDto.builder()
                .id(e.getId())
                .kind(e.getKind())
                .amount(amount)
                .currency(e.getCurrency())
                .spentDelta(amount.multiply(BigDecimal.valueOf(e.getKind().spentDirection())))
                .committedDelta(amount.multiply(BigDecimal.valueOf(e.getKind().committedDirection())))
                .spentAfter(e.getSpentAfter())
                .committedAfter(e.getCommittedAfter())
                .sourceType(e.getSourceType())
                .sourceId(e.getSourceId())
                .actorEmail(e.getActorEmail())
                .note(e.getNote())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth.getName() : null;
    }

    private static String sourceLabel(BudgetPosting posting) {
        return switch (posting.sourceType()) {
            case BudgetPosting.SOURCE_PURCHASE_ORDER -> "Purchase order";
            case BudgetPosting.SOURCE_EXPENSE -> "Expense";
            default -> posting.sourceType().toLowerCase(Locale.ROOT);
        };
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal total) {
        return part.multiply(HUNDRED).divide(total, 4, RoundingMode.HALF_UP);
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
