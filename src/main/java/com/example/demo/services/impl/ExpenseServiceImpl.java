package com.example.demo.services.impl;

import com.example.demo.dto.ExpenseDto;
import com.example.demo.enums.ExpenseStatus;
import com.example.demo.enums.NotificationType;
import com.example.demo.models.*;
import com.example.demo.repositories.*;
import com.example.demo.services.ExpenseService;
import com.example.demo.services.NotificationService;
import com.example.demo.services.TenantAwareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExpenseServiceImpl extends TenantAwareService implements ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseServiceImpl.class);

    private final ExpenseRepository expenseRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;
    private final DepartmentRepository departmentRepository;
    private final NotificationService notificationService;

    public ExpenseServiceImpl(ExpenseRepository expenseRepository,
                              AssetRepository assetRepository,
                              UserRepository userRepository,
                              BudgetRepository budgetRepository,
                              DepartmentRepository departmentRepository,
                              OrganisationRepository organisationRepository,
                              NotificationService notificationService) {
        super(organisationRepository);
        this.expenseRepository = expenseRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.budgetRepository = budgetRepository;
        this.departmentRepository = departmentRepository;
        this.notificationService = notificationService;
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    @Override
    public ExpenseDto submit(ExpenseDto dto) {
        Organisation org = requireTenantOrg();
        User currentUser = resolveCurrentUser(org);

        Expense expense = new Expense();
        expense.setTitle(dto.getTitle());
        expense.setDescription(dto.getDescription());
        expense.setAmount(dto.getAmount());
        expense.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : "USD");
        expense.setCategory(dto.getCategory());
        expense.setReceiptUrl(dto.getReceiptUrl());
        expense.setSubmittedBy(currentUser);
        expense.setStatus(ExpenseStatus.SUBMITTED);
        expense.setOrganisation(org);

        if (dto.getLinkedAssetId() != null) {
            assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getLinkedAssetId(), org)
                    .ifPresent(expense::setLinkedAsset);
        }
        if (dto.getLinkedBudgetId() != null) {
            budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getLinkedBudgetId(), org)
                    .ifPresent(expense::setLinkedBudget);
        }
        if (dto.getDepartmentId() != null) {
            departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getDepartmentId(), org)
                    .ifPresent(expense::setDepartment);
        }

        Expense saved = expenseRepository.save(expense);

        notificationService.notifyOrgAdmins(org, NotificationType.EXPENSE,
                "New Expense Submitted",
                "Expense '" + saved.getTitle() + "' for " + saved.getAmount() + " " + saved.getCurrency()
                        + " has been submitted by "
                        + (currentUser != null ? currentUser.getFirstName() + " " + currentUser.getLastName() : "a user") + ".",
                saved.getId(), null);

        log.info("Expense {} submitted by {}", saved.getId(), currentUser != null ? currentUser.getEmail() : "unknown");
        return toDto(saved);
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    @Override
    public ExpenseDto approve(UUID id) {
        Organisation org = requireTenantOrg();
        Expense expense = requireExpense(id, org);

        if (expense.getStatus() == ExpenseStatus.APPROVED) {
            return toDto(expense);
        }
        if (expense.getStatus() == ExpenseStatus.REJECTED) {
            throw new IllegalStateException("Cannot approve a rejected expense.");
        }

        User approver = resolveCurrentUser(org);
        expense.setApprovedBy(approver);
        expense.setApprovedAt(Instant.now());
        expense.setStatus(ExpenseStatus.APPROVED);

        // Auto-deduct from linked budget
        if (expense.getLinkedBudget() != null) {
            Budget budget = expense.getLinkedBudget();
            budget.setSpentAmount(budget.getSpentAmount().add(expense.getAmount()));
            budgetRepository.save(budget);
            log.info("Deducted {} {} from budget {} for approved expense {}",
                    expense.getAmount(), expense.getCurrency(), budget.getId(), expense.getId());
        }

        Expense saved = expenseRepository.save(expense);

        // Notify the original submitter
        if (saved.getSubmittedBy() != null) {
            notificationService.notifyOrgAdmins(org, NotificationType.APPROVAL,
                    "Expense Approved",
                    "Your expense '" + saved.getTitle() + "' has been approved.",
                    saved.getId(), null);
        }

        log.info("Expense {} approved by {}", id, approver != null ? approver.getEmail() : "unknown");
        return toDto(saved);
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    @Override
    public ExpenseDto reject(UUID id, String reason) {
        Organisation org = requireTenantOrg();
        Expense expense = requireExpense(id, org);

        if (expense.getStatus() == ExpenseStatus.APPROVED) {
            throw new IllegalStateException("Cannot reject an already approved expense.");
        }

        expense.setStatus(ExpenseStatus.REJECTED);
        expense.setRejectionReason(reason);

        Expense saved = expenseRepository.save(expense);

        if (saved.getSubmittedBy() != null) {
            notificationService.notifyOrgAdmins(org, NotificationType.APPROVAL,
                    "Expense Rejected",
                    "Your expense '" + saved.getTitle() + "' has been rejected."
                            + (reason != null ? " Reason: " + reason : ""),
                    saved.getId(), null);
        }

        log.info("Expense {} rejected. Reason: {}", id, reason);
        return toDto(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ExpenseDto getById(UUID id) {
        Organisation org = requireTenantOrg();
        return toDto(requireExpense(id, org));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseDto> listAll() {
        Organisation org = requireTenantOrg();
        return expenseRepository.findByOrganisationAndDeletedAtIsNull(org)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseDto> listPending() {
        Organisation org = requireTenantOrg();
        return expenseRepository.findByOrganisationAndStatusAndDeletedAtIsNull(org, ExpenseStatus.SUBMITTED)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseDto> listByUser(UUID userId) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByIdAndOrganisationAndDeletedAtIsNull(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return expenseRepository.findBySubmittedByAndDeletedAtIsNull(user)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public void delete(UUID id) {
        Organisation org = requireTenantOrg();
        Expense expense = requireExpense(id, org);
        expense.setDeletedAt(Instant.now());
        expenseRepository.save(expense);
        log.info("Soft-deleted expense {}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Expense requireExpense(UUID id, Organisation org) {
        return expenseRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found: " + id));
    }

    private User resolveCurrentUser(Organisation org) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return userRepository.findByEmailAndOrganisationId(auth.getName(), org.getId()).orElse(null);
        }
        return null;
    }

    private ExpenseDto toDto(Expense e) {
        ExpenseDto dto = new ExpenseDto();
        dto.setId(e.getId());
        dto.setTitle(e.getTitle());
        dto.setDescription(e.getDescription());
        dto.setAmount(e.getAmount());
        dto.setCurrency(e.getCurrency());
        dto.setCategory(e.getCategory());
        dto.setStatus(e.getStatus());
        dto.setReceiptUrl(e.getReceiptUrl());
        dto.setRejectionReason(e.getRejectionReason());
        dto.setApprovedAt(e.getApprovedAt());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setOrganisationId(e.getOrganisation().getId());
        if (e.getSubmittedBy() != null) {
            dto.setSubmittedById(e.getSubmittedBy().getId());
            dto.setSubmittedByName(e.getSubmittedBy().getFirstName() + " " + e.getSubmittedBy().getLastName());
        }
        if (e.getApprovedBy() != null) {
            dto.setApprovedById(e.getApprovedBy().getId());
        }
        if (e.getLinkedAsset() != null) {
            dto.setLinkedAssetId(e.getLinkedAsset().getId());
        }
        if (e.getLinkedBudget() != null) {
            dto.setLinkedBudgetId(e.getLinkedBudget().getId());
        }
        if (e.getDepartment() != null) {
            dto.setDepartmentId(e.getDepartment().getId());
        }
        return dto;
    }
}
