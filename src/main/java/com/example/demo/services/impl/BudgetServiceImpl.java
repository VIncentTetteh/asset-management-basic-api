package com.example.demo.services.impl;

import com.example.demo.dto.BudgetDto;
import com.example.demo.enums.BudgetStatus;
import com.example.demo.models.Budget;
import com.example.demo.models.Organisation;
import com.example.demo.repositories.BudgetRepository;
import com.example.demo.repositories.DepartmentRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.BudgetService;
import com.example.demo.services.TenantAwareService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BudgetServiceImpl extends TenantAwareService implements BudgetService {

    private final BudgetRepository budgetRepository;
    private final DepartmentRepository departmentRepository;

    public BudgetServiceImpl(OrganisationRepository organisationRepository,
                             BudgetRepository budgetRepository,
                             DepartmentRepository departmentRepository) {
        super(organisationRepository);
        this.budgetRepository = budgetRepository;
        this.departmentRepository = departmentRepository;
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
    public BudgetDto getById(UUID id) {
        Organisation org = requireTenantOrg();
        return toDto(budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + id)));
    }

    @Override
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
        if (dto.getCurrency() != null) budget.setCurrency(dto.getCurrency());
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
    public BudgetDto recordSpend(UUID budgetId, BigDecimal amount) {
        Organisation org = requireTenantOrg();
        Budget budget = budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(budgetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + budgetId));

        budget.setSpentAmount(budget.getSpentAmount().add(amount));

        // Auto-mark exceeded
        if (budget.getSpentAmount().compareTo(budget.getTotalAmount()) > 0
                && budget.getStatus() == BudgetStatus.ACTIVE) {
            budget.setStatus(BudgetStatus.EXCEEDED);
        }

        return toDto(budgetRepository.save(budget));
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
        budget.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : "USD");
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
        return d;
    }
}
