package com.assetiq.services.impl;

import com.assetiq.dto.ExpenseDto;
import com.assetiq.dto.ExpenseFilterRequest;
import com.assetiq.dto.PagedResponseDto;
import com.assetiq.enums.BudgetStatus;
import com.assetiq.enums.ExpenseStatus;
import com.assetiq.enums.NotificationType;
import com.assetiq.models.*;
import com.assetiq.repositories.*;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.ExpenseService;
import com.assetiq.services.NotificationService;
import com.assetiq.services.TenantAwareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
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
    private final CurrencyResolver currencyResolver;

    public ExpenseServiceImpl(ExpenseRepository expenseRepository,
                              AssetRepository assetRepository,
                              UserRepository userRepository,
                              BudgetRepository budgetRepository,
                              DepartmentRepository departmentRepository,
                              OrganisationRepository organisationRepository,
                              NotificationService notificationService,
                              CurrencyResolver currencyResolver) {
        super(organisationRepository);
        this.expenseRepository = expenseRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.budgetRepository = budgetRepository;
        this.departmentRepository = departmentRepository;
        this.notificationService = notificationService;
        this.currencyResolver = currencyResolver;
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
        expense.setCurrency(currencyResolver.resolveOrDefault(dto.getCurrency()));
        expense.setCategory(dto.getCategory());
        expense.setReceiptUrl(dto.getReceiptUrl());
        expense.setSubmittedBy(currentUser);
        expense.setStatus(ExpenseStatus.SUBMITTED);
        expense.setOrganisation(org);
        expense.setExpenseDate(dto.getExpenseDate() != null ? dto.getExpenseDate() : LocalDate.now());

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

        // Increment committedAmount on the linked budget to reserve funds for pending approval
        if (saved.getLinkedBudget() != null) {
            Budget b = saved.getLinkedBudget();
            BigDecimal current = b.getCommittedAmount() != null ? b.getCommittedAmount() : BigDecimal.ZERO;
            b.setCommittedAmount(current.add(saved.getAmount()));
            budgetRepository.save(b);
            log.info("Committed {} {} to budget {} for submitted expense {}",
                    saved.getAmount(), saved.getCurrency(), b.getId(), saved.getId());
        }

        try {
            notificationService.notifyOrgAdmins(org, NotificationType.EXPENSE,
                    "New Expense Submitted",
                    "Expense '" + saved.getTitle() + "' for " + saved.getAmount() + " " + saved.getCurrency()
                            + " has been submitted by "
                            + (currentUser != null ? currentUser.getFirstName() + " " + currentUser.getLastName() : "a user") + ".",
                    saved.getId(), null);
        } catch (Exception e) {
            log.warn("Expense submission notification suppressed for expense {}: {}", saved.getId(), e.getMessage());
        }

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

        // Move amount from committedAmount → spentAmount; check EXCEEDED and fire threshold alert
        if (expense.getLinkedBudget() != null) {
            Budget b = expense.getLinkedBudget();

            // Decrement committed (was reserved at submit time)
            BigDecimal comm = b.getCommittedAmount() != null ? b.getCommittedAmount() : BigDecimal.ZERO;
            b.setCommittedAmount(comm.subtract(expense.getAmount()).max(BigDecimal.ZERO));

            // Increment actual spend
            b.setSpentAmount(b.getSpentAmount().add(expense.getAmount()));

            // Mark budget as EXCEEDED when spend surpasses total
            if (b.getSpentAmount().compareTo(b.getTotalAmount()) > 0
                    && b.getStatus() == BudgetStatus.ACTIVE) {
                b.setStatus(BudgetStatus.EXCEEDED);
            }

            // Fire threshold notification when utilization crosses the configured alert pct
            if (b.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                int threshold = b.getAlertThresholdPct() != null ? b.getAlertThresholdPct() : 80;
                double utilization = b.getSpentAmount()
                        .divide(b.getTotalAmount(), 4, RoundingMode.HALF_UP)
                        .doubleValue() * 100;
                if (utilization >= threshold) {
                    try {
                        notificationService.notifyOrgAdmins(org, NotificationType.BUDGET_THRESHOLD,
                                "Budget Threshold Reached",
                                "Budget '" + b.getName() + "' has reached "
                                        + String.format("%.1f", utilization) + "% utilization"
                                        + " (threshold: " + threshold + "%).",
                                b.getId(), null);
                    } catch (Exception e) {
                        log.warn("Budget threshold notification suppressed for budget {}: {}", b.getId(), e.getMessage());
                    }
                    log.info("Budget {} threshold alert fired at {:.1f}% utilization", b.getId(), utilization);
                }
            }

            budgetRepository.save(b);
            log.info("Approved expense {}: moved {} {} from committed to spent on budget {}",
                    expense.getId(), expense.getAmount(), expense.getCurrency(), b.getId());
        }

        Expense saved = expenseRepository.save(expense);

        // Notify the original submitter
        if (saved.getSubmittedBy() != null) {
            try {
                notificationService.notifyOrgAdmins(org, NotificationType.APPROVAL,
                        "Expense Approved",
                        "Your expense '" + saved.getTitle() + "' has been approved.",
                        saved.getId(), null);
            } catch (Exception e) {
                log.warn("Expense approval notification suppressed for expense {}: {}", saved.getId(), e.getMessage());
            }
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

        // Release the reserved committedAmount back to the budget
        if (expense.getLinkedBudget() != null) {
            Budget b = expense.getLinkedBudget();
            BigDecimal comm = b.getCommittedAmount() != null ? b.getCommittedAmount() : BigDecimal.ZERO;
            b.setCommittedAmount(comm.subtract(expense.getAmount()).max(BigDecimal.ZERO));
            budgetRepository.save(b);
            log.info("Released committed {} {} back to budget {} for rejected expense {}",
                    expense.getAmount(), expense.getCurrency(), b.getId(), expense.getId());
        }

        Expense saved = expenseRepository.save(expense);

        if (saved.getSubmittedBy() != null) {
            try {
                notificationService.notifyOrgAdmins(org, NotificationType.APPROVAL,
                        "Expense Rejected",
                        "Your expense '" + saved.getTitle() + "' has been rejected."
                                + (reason != null ? " Reason: " + reason : ""),
                        saved.getId(), null);
            } catch (Exception e) {
                log.warn("Expense rejection notification suppressed for expense {}: {}", saved.getId(), e.getMessage());
            }
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
        return listPaged(new ExpenseFilterRequest(
                null, null, null, null, null, null, null, null, null, 0, 1000
        )).getItems();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponseDto<ExpenseDto> listPaged(ExpenseFilterRequest req) {
        Organisation org = requireTenantOrg();
        int pageNum  = req.page() != null && req.page()  >= 0 ? req.page()                : 0;
        int pageSize = req.size() != null && req.size()  >  0 ? Math.min(req.size(), 100) : 20;
        var pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var page     = expenseRepository.findAll(ExpenseSpecification.filtered(org, req), pageable);

        PagedResponseDto<ExpenseDto> response = new PagedResponseDto<>();
        response.setTotal(page.getTotalElements());
        response.setLimit(pageSize);
        response.setOffset((long) pageNum * pageSize);
        response.setItems(page.getContent().stream().map(this::toDto).collect(Collectors.toList()));
        return response;
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
            return userRepository.findByEmailAndOrganisationId(auth.getName(), org.getId())
                    .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                            "Authenticated user not found in organisation"));
        }
        throw new org.springframework.security.access.AccessDeniedException(
                "No authenticated user in security context");
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
        dto.setExpenseDate(e.getExpenseDate());
        dto.setLinkedBudgetName(e.getLinkedBudget() != null ? e.getLinkedBudget().getName() : null);
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
