package com.assetiq.services.impl;

import com.assetiq.dto.BudgetAdjustmentRequest;
import com.assetiq.dto.BudgetDto;
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

    public BudgetServiceImpl(OrganisationRepository organisationRepository,
                             BudgetRepository budgetRepository,
                             DepartmentRepository departmentRepository,
                             CurrencyResolver currencyResolver,
                             ExpenseRepository expenseRepository,
                             MoneyAggregator moneyAggregator) {
        super(organisationRepository);
        this.budgetRepository = budgetRepository;
        this.departmentRepository = departmentRepository;
        this.currencyResolver = currencyResolver;
        this.expenseRepository = expenseRepository;
        this.moneyAggregator = moneyAggregator;
    }

    @Override
    @Transactional
    public BudgetDto create(BudgetDto dto) {
        Organisation org = requireTenantOrg();
        Budget budget = new Budget();
        applyFields(budget, dto, org);
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
        Budget budget = budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + id));
        applyFields(budget, dto, org);
        return toDto(budgetRepository.save(budget));
    }

    @Override
    @Transactional
    public BudgetDto patch(UUID id, BudgetDto dto) {
        Organisation org = requireTenantOrg();
        Budget budget = budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + id));

        if (dto.getName() != null) budget.setName(dto.getName());
        if (dto.getDescription() != null) budget.setDescription(dto.getDescription());
        if (dto.getTotalAmount() != null) budget.setTotalAmount(dto.getTotalAmount());
        if (dto.getCurrency() != null) budget.setCurrency(CurrencyResolver.normaliseIsoCode(dto.getCurrency()));
        if (dto.getPeriodStart() != null) budget.setPeriodStart(dto.getPeriodStart());
        if (dto.getPeriodEnd() != null) budget.setPeriodEnd(dto.getPeriodEnd());
        if (dto.getStatus() != null) budget.setStatus(dto.getStatus());
        if (dto.getFiscalYear() != null) budget.setFiscalYear(dto.getFiscalYear());
        if (dto.getDepartmentId() != null) {
            departmentRepository.findAllByOrganisationAndDeletedAtIsNull(
                            organisationRepository.findByIdAndDeletedAtIsNull(org.getId()).orElse(org))
                    .stream()
                    .filter(d -> d.getId().equals(dto.getDepartmentId()))
                    .findFirst()
                    .ifPresent(budget::setDepartment);
        }

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
        Budget budget = budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(budgetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + budgetId));
        budget.setSpentAmount(budget.getSpentAmount().add(request.amount()));
        budget.setLastAdjustmentNote(request.note());
        if (budget.getSpentAmount().compareTo(budget.getTotalAmount()) > 0
                && budget.getStatus() == BudgetStatus.ACTIVE) {
            budget.setStatus(BudgetStatus.EXCEEDED);
        }
        return toDto(budgetRepository.save(budget));
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
        Budget budget = budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + id));
        budget.setDeletedAt(Instant.now());
        budgetRepository.save(budget);
    }

    private void applyFields(Budget budget, BudgetDto dto, Organisation org) {
        budget.setName(dto.getName());
        budget.setDescription(dto.getDescription());
        budget.setTotalAmount(dto.getTotalAmount());
        budget.setSpentAmount(dto.getSpentAmount() != null ? dto.getSpentAmount() : BigDecimal.ZERO);
        budget.setCurrency(currencyResolver.resolveOrDefault(dto.getCurrency()));
        budget.setPeriodStart(dto.getPeriodStart());
        budget.setPeriodEnd(dto.getPeriodEnd());
        budget.setStatus(dto.getStatus() != null ? dto.getStatus() : BudgetStatus.DRAFT);
        budget.setFiscalYear(dto.getFiscalYear());
        budget.setOrganisation(org);

        if (dto.getDepartmentId() != null) {
            departmentRepository.findAllByOrganisationAndDeletedAtIsNull(org)
                    .stream()
                    .filter(d -> d.getId().equals(dto.getDepartmentId()))
                    .findFirst()
                    .ifPresent(budget::setDepartment);
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
        d.setAvailableAmount(b.getTotalAmount().subtract(b.getSpentAmount()).subtract(committed));

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
