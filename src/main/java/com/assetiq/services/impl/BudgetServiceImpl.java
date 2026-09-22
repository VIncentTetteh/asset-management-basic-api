package com.assetiq.services.impl;

import com.assetiq.dto.BudgetAdjustmentRequest;
import com.assetiq.dto.BudgetDto;
import com.assetiq.dto.BudgetLedgerEntryDto;
import com.assetiq.dto.BudgetSummaryDto;
import com.assetiq.dto.ExpenseDto;
import com.assetiq.dto.PagedResponseDto;
import com.assetiq.enums.BudgetStatus;
import com.assetiq.models.Budget;
import com.assetiq.models.Expense;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.BudgetRepository;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.ExpenseRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.BudgetService;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.TenantAwareService;
import com.assetiq.services.budget.BudgetLedgerService;
import com.assetiq.services.budget.BudgetPosting;
import com.assetiq.services.money.CurrencyConversion;
import com.assetiq.services.money.MoneyAccumulator;
import com.assetiq.services.money.MoneyAggregator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BudgetServiceImpl extends TenantAwareService implements BudgetService {

    private final BudgetRepository budgetRepository;
    private final DepartmentRepository departmentRepository;
    private final CurrencyResolver currencyResolver;
    private final ExpenseRepository expenseRepository;
    private final MoneyAggregator moneyAggregator;
    private final BudgetLedgerService budgetLedger;

    public BudgetServiceImpl(OrganisationRepository organisationRepository,
                             BudgetRepository budgetRepository,
                             DepartmentRepository departmentRepository,
                             CurrencyResolver currencyResolver,
                             ExpenseRepository expenseRepository,
                             MoneyAggregator moneyAggregator,
                             BudgetLedgerService budgetLedger) {
        super(organisationRepository);
        this.budgetRepository = budgetRepository;
        this.departmentRepository = departmentRepository;
        this.currencyResolver = currencyResolver;
        this.expenseRepository = expenseRepository;
        this.moneyAggregator = moneyAggregator;
        this.budgetLedger = budgetLedger;
    }

    @Override
    @Transactional
    public BudgetDto create(BudgetDto dto) {
        Organisation org = requireTenantOrg();
        Budget budget = new Budget();
        applyFields(budget, dto, org, true);
        return toDto(budgetRepository.save(budget));
    }

    @Override
    @Transactional
    public BudgetDto getById(UUID id) {
        Organisation org = requireTenantOrg();
        return toDto(budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + id)));
    }

    @Override
    @Transactional
    public List<BudgetDto> listAll() {
        Organisation org = requireTenantOrg();
        return budgetRepository.findByOrganisationAndDeletedAtIsNullOrderByPeriodStartDesc(org)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BudgetDto update(UUID id, BudgetDto dto) {
        Organisation org = requireTenantOrg();
        // Locked: the save writes every column, and must not overwrite a commitment
        // or spend posted by a concurrent approval.
        Budget budget = budgetLedger.lockForEdit(id, org);
        applyFields(budget, dto, org, false);
        return toDto(budgetRepository.save(budget));
    }

    @Override
    @Transactional
    public BudgetDto patch(UUID id, BudgetDto dto) {
        Organisation org = requireTenantOrg();
        Budget budget = budgetLedger.lockForEdit(id, org);

        if (dto.getName() != null) budget.setName(dto.getName());
        if (dto.getDescription() != null) budget.setDescription(dto.getDescription());
        if (dto.getTotalAmount() != null) budget.setTotalAmount(dto.getTotalAmount());
        if (dto.getCurrency() != null) {
            String currency = CurrencyResolver.normaliseIsoCode(dto.getCurrency());
            requireCurrencyChangeAllowed(budget, currency);
            budget.setCurrency(currency);
        }
        if (dto.getPeriodStart() != null) budget.setPeriodStart(dto.getPeriodStart());
        if (dto.getPeriodEnd() != null) budget.setPeriodEnd(dto.getPeriodEnd());
        if (dto.getStatus() != null) budget.setStatus(dto.getStatus());
        if (dto.getFiscalYear() != null) budget.setFiscalYear(dto.getFiscalYear());
        if (dto.getAlertThresholdPct() != null) budget.setAlertThresholdPct(dto.getAlertThresholdPct());
        if (dto.getDepartmentId() != null) {
            departmentRepository.findAllByOrganisationAndDeletedAtIsNull(
                            organisationRepository.findByIdAndDeletedAtIsNull(org.getId()).orElse(org))
                    .stream()
                    .filter(d -> d.getId().equals(dto.getDepartmentId()))
                    .findFirst()
                    .ifPresent(budget::setDepartment);
        }
        requireValidPeriod(budget);
        budget.reconcileExceededStatus();

        return toDto(budgetRepository.save(budget));
    }

    @Override
    @Transactional
    @Deprecated
    public BudgetDto recordSpend(UUID budgetId, BigDecimal amount) {
        return recordAdjustment(budgetId, new BudgetAdjustmentRequest(amount, "Legacy spend recording"));
    }

    @Override
    @Transactional
    public BudgetDto recordAdjustment(UUID budgetId, BudgetAdjustmentRequest request) {
        Organisation org = requireTenantOrg();
        Budget budget = budgetLedger.post(org, budgetId,
                BudgetPosting.adjustment(request.amount(), request.note()));
        if (budget == null) {
            throw new IllegalArgumentException("Budget not found: " + budgetId);
        }
        budget.setLastAdjustmentNote(request.note());
        return toDto(budgetRepository.save(budget));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BudgetLedgerEntryDto> getLedger(UUID budgetId) {
        Organisation org = requireTenantOrg();
        Budget budget = budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(budgetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + budgetId));
        return budgetLedger.entries(budget, org);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponseDto<ExpenseDto> getExpenses(UUID budgetId, int page, int size) {
        Organisation org = requireTenantOrg();
        Budget budget = budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(budgetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + budgetId));
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var resultPage = expenseRepository.findByLinkedBudgetAndOrganisationAndDeletedAtIsNull(
                budget, org, pageable);
        PagedResponseDto<ExpenseDto> response = new PagedResponseDto<>();
        response.setTotal(resultPage.getTotalElements());
        response.setLimit(safeSize);
        response.setOffset((long) safePage * safeSize);
        response.setItems(resultPage.getContent().stream()
                .map(this::expenseToDto).collect(Collectors.toList()));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetSummaryDto getSummary() {
        Organisation org = requireTenantOrg();
        List<Budget> budgets = budgetRepository
                .findByOrganisationAndDeletedAtIsNullOrderByPeriodStartDesc(org);
        // Every budget is converted into the tenant base currency; a budget whose
        // currency has no rate is excluded from all totals and reported instead.
        CurrencyConversion fx = moneyAggregator.begin(org);
        MoneyAccumulator totalAllocated = fx.newAccumulator();
        MoneyAccumulator totalSpent = fx.newAccumulator();
        MoneyAccumulator totalCommitted = fx.newAccumulator();
        LinkedHashMap<String, DepartmentTotals> byDept = new LinkedHashMap<>();
        for (Budget b : budgets) {
            BigDecimal comm = b.getCommittedAmount() != null ? b.getCommittedAmount() : BigDecimal.ZERO;
            if (fx.toBase(BigDecimal.ONE, b.getCurrency()).isEmpty()) {
                // No rate: the pair is now recorded on fx; skip the budget entirely so
                // allocated/spent/committed stay mutually consistent.
                continue;
            }
            totalAllocated.add(b.getTotalAmount(), b.getCurrency());
            totalSpent.add(b.getSpentAmount(), b.getCurrency());
            totalCommitted.add(comm, b.getCurrency());
            String key = b.getDepartment() != null ? b.getDepartment().getId().toString() : "__org__";
            String name = b.getDepartment() != null ? b.getDepartment().getName() : "Org-wide";
            DepartmentTotals dt = byDept.computeIfAbsent(key, k -> new DepartmentTotals(
                    key.equals("__org__") ? null : key, name,
                    fx.newAccumulator(), fx.newAccumulator(), fx.newAccumulator()));
            dt.allocated().add(b.getTotalAmount(), b.getCurrency());
            dt.spent().add(b.getSpentAmount(), b.getCurrency());
            dt.committed().add(comm, b.getCurrency());
        }
        List<BudgetSummaryDto.DepartmentSummary> departments = new ArrayList<>();
        for (DepartmentTotals dt : byDept.values()) {
            departments.add(BudgetSummaryDto.DepartmentSummary.builder()
                    .departmentId(dt.departmentId())
                    .departmentName(dt.departmentName())
                    .allocated(dt.allocated().amount())
                    .spent(dt.spent().amount())
                    .committed(dt.committed().amount())
                    .available(CurrencyConversion.round(dt.allocated().rawSum()
                            .subtract(dt.spent().rawSum()).subtract(dt.committed().rawSum())))
                    .build());
        }
        BigDecimal totalAvailable = totalAllocated.rawSum()
                .subtract(totalSpent.rawSum()).subtract(totalCommitted.rawSum());
        return BudgetSummaryDto.builder()
            .currency(fx.baseCurrency())
            .complete(fx.isComplete())
            .missingRates(fx.missingRates())
            .totalAllocated(totalAllocated.amount())
            .totalSpent(totalSpent.amount())
            .totalCommitted(totalCommitted.amount())
            .totalAvailable(CurrencyConversion.round(totalAvailable))
            .byDepartment(departments)
            .build();
    }

    /** Per-department running totals in the base currency. */
    private record DepartmentTotals(String departmentId, String departmentName,
                                    MoneyAccumulator allocated, MoneyAccumulator spent,
                                    MoneyAccumulator committed) {
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Organisation org = requireTenantOrg();
        Budget budget = budgetLedger.lockForEdit(id, org);
        if (budget.getCommittedAmount() != null && budget.getCommittedAmount().signum() > 0) {
            // Deleting now would strand the open purchase orders and expenses: they
            // could never be received or approved against it.
            throw new IllegalStateException("Budget '" + budget.getName() + "' has open commitments of "
                    + budget.getCommittedAmount().toPlainString() + " " + budget.getCurrency()
                    + "; approve, reject or cancel them before deleting the budget");
        }
        budget.setDeletedAt(Instant.now());
        budgetRepository.save(budget);
    }

    /**
     * Applies the client-editable fields of a create or full (PUT) update.
     *
     * <p>{@code spentAmount} and {@code committedAmount} are deliberately not
     * client-editable: they are running totals maintained by the budget ledger
     * (adjustments, purchase orders and expenses). Accepting them from the request
     * let an edit silently wipe the recorded spend.
     */
    private void applyFields(Budget budget, BudgetDto dto, Organisation org, boolean creating) {
        budget.setName(dto.getName());
        budget.setDescription(dto.getDescription());
        budget.setTotalAmount(dto.getTotalAmount());
        if (creating || dto.getCurrency() != null) {
            String currency = currencyResolver.resolveOrDefault(dto.getCurrency());
            if (!creating) requireCurrencyChangeAllowed(budget, currency);
            budget.setCurrency(currency);
        }
        budget.setPeriodStart(dto.getPeriodStart());
        budget.setPeriodEnd(dto.getPeriodEnd());
        if (dto.getStatus() != null) {
            budget.setStatus(dto.getStatus());
        } else if (creating) {
            // A budget created without an explicit status is open for spend, matching
            // the web form's default. DRAFT must be chosen deliberately.
            budget.setStatus(BudgetStatus.ACTIVE);
        }
        if (dto.getAlertThresholdPct() != null) {
            budget.setAlertThresholdPct(dto.getAlertThresholdPct());
        }
        budget.setFiscalYear(dto.getFiscalYear());
        budget.setOrganisation(org);

        budget.setDepartment(null);
        if (dto.getDepartmentId() != null) {
            departmentRepository.findAllByOrganisationAndDeletedAtIsNull(org)
                    .stream()
                    .filter(d -> d.getId().equals(dto.getDepartmentId()))
                    .findFirst()
                    .ifPresent(budget::setDepartment);
        }
        requireValidPeriod(budget);
        budget.reconcileExceededStatus();
    }

    /**
     * Spend and commitments are recorded in the budget's currency, so relabelling
     * them as another currency would silently misstate them. The currency can only
     * change while nothing has been spent or committed.
     */
    private static void requireCurrencyChangeAllowed(Budget budget, String newCurrency) {
        String current = budget.getCurrency();
        if (current == null || current.equalsIgnoreCase(newCurrency)) return;
        boolean hasSpend = budget.getSpentAmount() != null && budget.getSpentAmount().signum() != 0;
        boolean hasCommitments = budget.getCommittedAmount() != null && budget.getCommittedAmount().signum() != 0;
        if (hasSpend || hasCommitments) {
            throw new IllegalStateException("The budget currency cannot change from " + current + " to " + newCurrency
                    + " because the budget already has spend or commitments recorded in " + current
                    + ". Create a new budget in " + newCurrency + " instead.");
        }
    }

    private static void requireValidPeriod(Budget budget) {
        if (budget.getPeriodStart() != null && budget.getPeriodEnd() != null
                && budget.getPeriodEnd().isBefore(budget.getPeriodStart())) {
            throw new IllegalArgumentException("Budget period end must not be before period start");
        }
    }

    private BudgetDto toDto(Budget b) {
        BudgetDto d = new BudgetDto();
        d.setId(b.getId());
        d.setName(b.getName());
        d.setDescription(b.getDescription());
        d.setTotalAmount(b.getTotalAmount());
        d.setSpentAmount(b.getSpentAmount());
        d.setCurrency(b.getCurrency());
        d.setPeriodStart(b.getPeriodStart());
        d.setPeriodEnd(b.getPeriodEnd());
        d.setStatus(b.getStatus());
        d.setFiscalYear(b.getFiscalYear());

        BigDecimal remaining = b.getTotalAmount().subtract(b.getSpentAmount());
        d.setRemainingAmount(remaining);

        if (b.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            double pct = b.getSpentAmount()
                    .divide(b.getTotalAmount(), 4, RoundingMode.HALF_UP)
                    .doubleValue() * 100;
            d.setUtilizationPct(Math.round(pct * 100.0) / 100.0);
        } else {
            d.setUtilizationPct(0.0);
        }

        if (b.getDepartment() != null) {
            d.setDepartmentId(b.getDepartment().getId());
            d.setDepartmentName(b.getDepartment().getName());
        }
        d.setCreatedAt(b.getCreatedAt());
        d.setUpdatedAt(b.getUpdatedAt());

        // Committed amount and available headroom
        BigDecimal committed = b.getCommittedAmount() != null ? b.getCommittedAmount() : BigDecimal.ZERO;
        d.setCommittedAmount(committed);
        d.setAlertThresholdPct(b.getAlertThresholdPct() != null ? b.getAlertThresholdPct() : 80);
        d.setAvailableAmount(b.availableAmount());

        // Linear forecast: (spent / elapsed) * total_days
        LocalDate today = LocalDate.now();
        LocalDate start = b.getPeriodStart();
        LocalDate end   = b.getPeriodEnd();
        if (start != null && end != null && !today.isBefore(start)
                && b.getTotalAmount().compareTo(BigDecimal.ZERO) > 0
                && b.getSpentAmount().compareTo(BigDecimal.ZERO) > 0) {
            long totalDays   = ChronoUnit.DAYS.between(start, end) + 1;
            long elapsedDays = ChronoUnit.DAYS.between(start, today) + 1;
            if (elapsedDays > 0) {
                BigDecimal dailyRate = b.getSpentAmount().divide(
                    BigDecimal.valueOf(elapsedDays), 4, RoundingMode.HALF_UP);
                d.setForecastedSpend(dailyRate.multiply(BigDecimal.valueOf(totalDays))
                    .setScale(2, RoundingMode.HALF_UP));
            }
        }

        return d;
    }

    private ExpenseDto expenseToDto(Expense e) {
        ExpenseDto.ExpenseDtoBuilder builder = ExpenseDto.builder()
                .id(e.getId())
                .title(e.getTitle())
                .description(e.getDescription())
                .amount(e.getAmount())
                .currency(e.getCurrency())
                .category(e.getCategory())
                .expenseDate(e.getExpenseDate())
                .status(e.getStatus())
                .approvedAt(e.getApprovedAt())
                .rejectionReason(e.getRejectionReason())
                .receiptUrl(e.getReceiptUrl())
                .createdAt(e.getCreatedAt());

        if (e.getSubmittedBy() != null) {
            builder.submittedById(e.getSubmittedBy().getId());
            builder.submittedByName(e.getSubmittedBy().getFirstName() + " " + e.getSubmittedBy().getLastName());
        }

        if (e.getApprovedBy() != null) {
            builder.approvedById(e.getApprovedBy().getId());
        }

        if (e.getLinkedBudget() != null) {
            builder.linkedBudgetId(e.getLinkedBudget().getId());
            builder.linkedBudgetName(e.getLinkedBudget().getName());
        }

        if (e.getLinkedAsset() != null) {
            builder.linkedAssetId(e.getLinkedAsset().getId());
        }

        if (e.getDepartment() != null) {
            builder.departmentId(e.getDepartment().getId());
        }

        if (e.getOrganisation() != null) {
            builder.organisationId(e.getOrganisation().getId());
        }

        return builder.build();
    }
}
