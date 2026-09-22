package com.assetiq.services.impl;

import com.assetiq.dto.PoLineItemDto;
import com.assetiq.dto.PurchaseOrderDto;
import com.assetiq.enums.BudgetLedgerKind;
import com.assetiq.enums.POStatus;
import com.assetiq.models.Budget;
import com.assetiq.models.Category;
import com.assetiq.models.PoLineItem;
import com.assetiq.models.PurchaseOrder;
import com.assetiq.models.Organisation;
import com.assetiq.models.Department;
import com.assetiq.models.Supplier;
import com.assetiq.models.User;
import com.assetiq.repositories.*;
import com.assetiq.enums.NotificationType;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.NotificationService;
import com.assetiq.services.PurchaseOrderService;
import com.assetiq.services.PurchaseOrderTotals;
import com.assetiq.services.TenantAwareService;
import com.assetiq.services.UserDisplayNames;
import com.assetiq.services.budget.BudgetLedgerService;
import com.assetiq.services.budget.BudgetPosting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PurchaseOrderServiceImpl extends TenantAwareService implements PurchaseOrderService {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderServiceImpl.class);

    private final PurchaseOrderRepository poRepository;
    private final PoLineItemRepository lineItemRepository;
    private final CategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;
    private final NotificationService notificationService;
    private final CurrencyResolver currencyResolver;
    private final BudgetLedgerService budgetLedger;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository poRepository,
            PoLineItemRepository lineItemRepository,
            CategoryRepository categoryRepository,
            OrganisationRepository organisationRepository,
            DepartmentRepository departmentRepository,
            SupplierRepository supplierRepository,
            UserRepository userRepository,
            BudgetRepository budgetRepository,
            NotificationService notificationService,
            CurrencyResolver currencyResolver,
            BudgetLedgerService budgetLedger) {
        super(organisationRepository);
        this.poRepository = poRepository;
        this.lineItemRepository = lineItemRepository;
        this.categoryRepository = categoryRepository;
        this.departmentRepository = departmentRepository;
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
        this.budgetRepository = budgetRepository;
        this.notificationService = notificationService;
        this.currencyResolver = currencyResolver;
        this.budgetLedger = budgetLedger;
    }

    @Override
    public PurchaseOrderDto createPurchaseOrder(PurchaseOrderDto poDto) {
        Organisation org = requireTenantOrg();

        Department department = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                poDto.getDepartmentId(), org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));

        Supplier supplier = supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                poDto.getSupplierId(), org)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found in your organisation"));

        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber(poDto.getPoNumber());
        po.setTotalAmount(poDto.getTotalAmount());
        po.setCurrency(currencyResolver.resolveOrDefault(poDto.getCurrency()));
        // Approval state is server-owned. Creation can never mint an approved,
        // delivered, rejected or cancelled order from a client-supplied status.
        po.setStatus(POStatus.DRAFT);
        po.setRemarks(poDto.getRemarks());
        po.setExpectedDeliveryDate(poDto.getExpectedDeliveryDate());
        po.setOrganisation(org);
        po.setDepartment(department);
        po.setSupplier(supplier);
        po.setRequestedBy(resolveCurrentUser(org));

        if (poDto.getLinkedBudgetId() != null) {
            po.setLinkedBudget(requireLinkableBudget(poDto.getLinkedBudgetId(), org));
        }
        requireBudgetCurrency(po);

        PurchaseOrder saved = poRepository.save(po);
        if (poDto.getLineItems() != null && !poDto.getLineItems().isEmpty()) {
            replaceLineItems(saved, org, poDto.getLineItems());
            saved = poRepository.save(saved);
        }
        logger.info("Created Purchase Order {} (PO Number: {}) with {} line(s)",
                saved.getId(), saved.getPoNumber(),
                poDto.getLineItems() == null ? 0 : poDto.getLineItems().size());
        notificationService.notifyOrgAdmins(org, NotificationType.PURCHASE_ORDER,
                "Purchase Order Created",
                "Purchase Order '" + saved.getPoNumber() + "' has been created for " + supplier.getName() + ".",
                saved.getId(), "/purchase-orders");
        return mapToDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderDto getPurchaseOrderById(UUID id) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = poRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        return mapToDto(po);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<PurchaseOrderDto> getPurchaseOrdersByOrganisation(UUID organisationId) {
        // Always scope to tenant context, ignore param
        Organisation org = requireTenantOrg();
        return poRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<PurchaseOrderDto> getPurchaseOrdersByDepartment(UUID departmentId) {
        Organisation org = requireTenantOrg();
        departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(departmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
        return poRepository.findByDepartmentIdAndDeletedAtIsNull(departmentId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<PurchaseOrderDto> getPurchaseOrdersBySupplier(UUID supplierId) {
        Organisation org = requireTenantOrg();
        supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(supplierId, org)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found in your organisation"));
        return poRepository.findBySupplierId(supplierId).stream()
                .filter(po -> po.getOrganisation().getId().equals(org.getId()))
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<PurchaseOrderDto> getPurchaseOrdersByStatus(POStatus status) {
        Organisation org = requireTenantOrg();
        return poRepository.findByOrganisationAndStatusAndDeletedAtIsNull(org, status).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    public PurchaseOrderDto updatePurchaseOrder(UUID id, PurchaseOrderDto poDto) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = requireOrder(id, org);
        requireStatus(po, "edit", POStatus.DRAFT);

        po.setPoNumber(poDto.getPoNumber());
        po.setTotalAmount(poDto.getTotalAmount());
        po.setCurrency(currencyResolver.resolveOrDefault(poDto.getCurrency()));
        po.setRemarks(poDto.getRemarks());
        // PUT is a full replacement: a null date clears it.
        po.setExpectedDeliveryDate(poDto.getExpectedDeliveryDate());
        po.setDepartment(requireDepartment(poDto.getDepartmentId(), org));
        po.setSupplier(requireSupplier(poDto.getSupplierId(), org));
        // PUT is a full replacement: a null linkedBudgetId unlinks the budget.
        po.setLinkedBudget(poDto.getLinkedBudgetId() != null
                ? requireLinkableBudget(poDto.getLinkedBudgetId(), org) : null);
        requireBudgetCurrency(po);
        // PUT is a full replacement: an absent or empty list leaves the order a lump sum.
        replaceLineItems(po, org, poDto.getLineItems() == null ? List.of() : poDto.getLineItems());

        return mapToDto(poRepository.save(po));
    }

    @Override
    public PurchaseOrderDto patchPurchaseOrder(UUID id, PurchaseOrderDto poDto) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = requireOrder(id, org);
        requireStatus(po, "edit", POStatus.DRAFT);

        if (poDto.getPoNumber() != null) {
            po.setPoNumber(poDto.getPoNumber());
        }
        if (poDto.getTotalAmount() != null) {
            po.setTotalAmount(poDto.getTotalAmount());
        }
        if (poDto.getCurrency() != null) {
            po.setCurrency(currencyResolver.resolveOrDefault(poDto.getCurrency()));
        }
        if (poDto.getRemarks() != null) {
            po.setRemarks(poDto.getRemarks());
        }
        if (poDto.getExpectedDeliveryDate() != null) {
            po.setExpectedDeliveryDate(poDto.getExpectedDeliveryDate());
        }
        if (poDto.getDepartmentId() != null) {
            po.setDepartment(requireDepartment(poDto.getDepartmentId(), org));
        }
        if (poDto.getSupplierId() != null) {
            po.setSupplier(requireSupplier(poDto.getSupplierId(), org));
        }
        if (poDto.getLinkedBudgetId() != null) {
            po.setLinkedBudget(requireLinkableBudget(poDto.getLinkedBudgetId(), org));
        }
        requireBudgetCurrency(po);
        // PATCH only touches the lines when the field is present; an empty list clears them.
        if (poDto.getLineItems() != null) {
            replaceLineItems(po, org, poDto.getLineItems());
        }

        return mapToDto(poRepository.save(po));
    }

    // ── Workflow ─────────────────────────────────────────────────────────────
    //
    //   DRAFT ──submit──▶ SUBMITTED ──approve──▶ APPROVED ──receive──▶ DELIVERED
    //     │                  │  └──reject──▶ REJECTED        │
    //     └──cancel──▶ CANCELLED ◀──cancel──┘◀────cancel─────┘
    //
    // Budget effect (when a budget is linked): approve commits the amount (funds
    // checked), receive turns the commitment into spend, cancel/delete release the
    // commitment, and deleting a delivered order reverses its spend.

    @Override
    public PurchaseOrderDto submitPurchaseOrder(UUID id) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = requireOrder(id, org);
        if (po.getStatus() == POStatus.SUBMITTED) {
            return mapToDto(po);
        }
        requireStatus(po, "submit", POStatus.DRAFT);
        requireBudgetCurrency(po);

        // The person who submits is the maker: they cannot also be the checker.
        User submitter = resolveCurrentUser(org);
        po.setRequestedBy(submitter);
        po.setStatus(POStatus.SUBMITTED);
        PurchaseOrder submitted = poRepository.save(po);
        logger.info("Purchase Order {} submitted for approval by {}", id, submitter.getEmail());
        notificationService.notifyOrgAdmins(org, NotificationType.APPROVAL,
                "Purchase Order Awaiting Approval",
                "Purchase Order '" + submitted.getPoNumber() + "' has been submitted for approval.",
                submitted.getId(), "/purchase-orders");
        return mapToDto(submitted);
    }

    @Override
    public PurchaseOrderDto approvePurchaseOrder(UUID id) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = requireOrder(id, org);

        if (po.getStatus() == POStatus.APPROVED) {
            logger.warn("Purchase Order {} is already approved", id);
            return mapToDto(po);
        }
        requireStatus(po, "approve", POStatus.SUBMITTED);

        User approver = resolveCurrentUser(org);
        if (po.getRequestedBy() != null && po.getRequestedBy().getId().equals(approver.getId())) {
            throw new IllegalStateException("A purchase order requester cannot approve their own order");
        }

        // Commit before changing the order: an insufficient-funds or currency failure
        // must leave the order SUBMITTED and the budget untouched.
        if (po.getLinkedBudget() != null) {
            budgetLedger.post(org, po.getLinkedBudget().getId(),
                    BudgetPosting.forPurchaseOrder(BudgetLedgerKind.PO_COMMIT, po));
            logger.info("Committed {} {} against budget {} for approved PO {}",
                    po.getTotalAmount(), po.getCurrency(), po.getLinkedBudget().getId(), po.getId());
        }

        po.setApprovedBy(approver);
        po.setStatus(POStatus.APPROVED);
        po.setApprovedAt(Instant.now());
        PurchaseOrder approved = poRepository.save(po);
        logger.info("Purchase Order {} approved by user {}", id, approver.getEmail());
        notificationService.notifyOrgAdmins(org, NotificationType.APPROVAL,
                "Purchase Order Approved",
                "Purchase Order '" + approved.getPoNumber() + "' has been approved.",
                approved.getId(), "/purchase-orders");
        return mapToDto(approved);
    }

    @Override
    public PurchaseOrderDto rejectPurchaseOrder(UUID id, String reason) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = requireOrder(id, org);

        if (po.getStatus() == POStatus.REJECTED) {
            logger.warn("Purchase Order {} is already rejected", id);
            return mapToDto(po);
        }
        if (po.getStatus() == POStatus.APPROVED) {
            throw new IllegalStateException(
                    "Cannot reject an already approved purchase order; cancel it instead to release its budget commitment");
        }
        requireStatus(po, "reject", POStatus.SUBMITTED);

        User rejector = resolveCurrentUser(org);
        if (po.getRequestedBy() != null && po.getRequestedBy().getId().equals(rejector.getId())) {
            throw new IllegalStateException("A purchase order requester cannot reject their own order");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        po.setStatus(POStatus.REJECTED);
        po.setRejectionReason(reason.trim());
        po.setRejectedBy(rejector);
        po.setRejectedAt(Instant.now());
        logger.info("Purchase Order {} rejected by user {}", id, rejector.getEmail());
        PurchaseOrder rejected = poRepository.save(po);
        notificationService.notifyOrgAdmins(org, NotificationType.APPROVAL,
                "Purchase Order Rejected",
                "Purchase Order '" + rejected.getPoNumber() + "' has been rejected: " + rejected.getRejectionReason(),
                rejected.getId(), "/purchase-orders");
        return mapToDto(rejected);
    }

    @Override
    public PurchaseOrderDto receivePurchaseOrder(UUID id) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = requireOrder(id, org);
        if (po.getStatus() == POStatus.DELIVERED) {
            return mapToDto(po);
        }
        requireStatus(po, "mark as received", POStatus.APPROVED);

        if (po.getLinkedBudget() != null && hasCommitment(org, po)) {
            budgetLedger.post(org, po.getLinkedBudget().getId(),
                    BudgetPosting.forPurchaseOrder(BudgetLedgerKind.PO_SPEND, po));
        }
        // No commitment entry: the order was approved before commitments existed,
        // when approval charged spend directly. It is already counted as spent.

        po.setStatus(POStatus.DELIVERED);
        PurchaseOrder received = poRepository.save(po);
        logger.info("Purchase Order {} marked as received", id);
        return mapToDto(received);
    }

    @Override
    public PurchaseOrderDto cancelPurchaseOrder(UUID id) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = requireOrder(id, org);
        if (po.getStatus() == POStatus.CANCELLED) {
            return mapToDto(po);
        }
        requireStatus(po, "cancel", POStatus.DRAFT, POStatus.SUBMITTED, POStatus.APPROVED);

        if (po.getStatus() == POStatus.APPROVED) {
            unwindBudget(org, po);
        }
        po.setStatus(POStatus.CANCELLED);
        PurchaseOrder cancelled = poRepository.save(po);
        logger.info("Purchase Order {} cancelled", id);
        notificationService.notifyOrgAdmins(org, NotificationType.PURCHASE_ORDER,
                "Purchase Order Cancelled",
                "Purchase Order '" + cancelled.getPoNumber() + "' has been cancelled.",
                cancelled.getId(), "/purchase-orders");
        return mapToDto(cancelled);
    }

    @Override
    public void deletePurchaseOrder(UUID id) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = requireOrder(id, org);
        if (po.getStatus() == POStatus.APPROVED || po.getStatus() == POStatus.DELIVERED) {
            unwindBudget(org, po);
        }
        Instant now = Instant.now();
        List<PoLineItem> lines = liveLinesOf(po.getId());
        lines.forEach(line -> line.setDeletedAt(now));
        lineItemRepository.saveAll(lines);
        po.setDeletedAt(now);
        poRepository.save(po);
        logger.info("Soft-deleted Purchase Order {} (PO Number: {}) and {} line(s)",
                id, po.getPoNumber(), lines.size());
    }

    /**
     * Takes an approved or delivered order back off its budget: an open commitment
     * is released; spend already recorded (a delivered order, or one approved before
     * commitments existed, when approval charged spend directly) is reversed.
     */
    private void unwindBudget(Organisation org, PurchaseOrder po) {
        if (po.getLinkedBudget() == null) return;
        boolean openCommitment = po.getStatus() == POStatus.APPROVED && hasCommitment(org, po);
        BudgetLedgerKind kind = openCommitment ? BudgetLedgerKind.PO_RELEASE : BudgetLedgerKind.PO_SPEND_REVERSAL;
        budgetLedger.post(org, po.getLinkedBudget().getId(), BudgetPosting.forPurchaseOrder(kind, po));
        logger.info("{} {} {} on budget {} for PO {}", kind, po.getTotalAmount(), po.getCurrency(),
                po.getLinkedBudget().getId(), po.getId());
    }

    private boolean hasCommitment(Organisation org, PurchaseOrder po) {
        return budgetLedger.hasPosted(org, BudgetPosting.keyFor(BudgetLedgerKind.PO_COMMIT, po.getId()));
    }

    private static void requireStatus(PurchaseOrder po, String action, POStatus... allowed) {
        for (POStatus status : allowed) {
            if (po.getStatus() == status) return;
        }
        throw new IllegalStateException("Cannot " + action + " a purchase order in status " + po.getStatus()
                + "; allowed from " + java.util.Arrays.toString(allowed));
    }

    /** Early, user-facing currency check (the ledger enforces it again at posting time). */
    private static void requireBudgetCurrency(PurchaseOrder po) {
        Budget budget = po.getLinkedBudget();
        if (budget == null) return;
        if (budget.getCurrency() == null || po.getCurrency() == null
                || !budget.getCurrency().equalsIgnoreCase(po.getCurrency())) {
            throw new IllegalArgumentException("Purchase order currency " + po.getCurrency()
                    + " does not match budget '" + budget.getName() + "' currency " + budget.getCurrency()
                    + "; purchase order and linked budget currencies must match");
        }
    }

    private PurchaseOrder requireOrder(UUID id, Organisation org) {
        return poRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
    }

    private Department requireDepartment(UUID departmentId, Organisation org) {
        if (departmentId == null) {
            throw new IllegalArgumentException("Department is required");
        }
        return departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(departmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
    }

    private Supplier requireSupplier(UUID supplierId, Organisation org) {
        if (supplierId == null) {
            throw new IllegalArgumentException("Supplier is required");
        }
        return supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(supplierId, org)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found in your organisation"));
    }

    private Budget requireLinkableBudget(UUID budgetId, Organisation org) {
        return budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(budgetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found in your organisation"));
    }

    private PurchaseOrderDto mapToDto(PurchaseOrder po) {
        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setId(po.getId());
        dto.setPoNumber(po.getPoNumber());
        dto.setTotalAmount(po.getTotalAmount());
        dto.setCurrency(po.getCurrency());
        dto.setStatus(po.getStatus());
        if (po.getApprovedBy() != null) {
            dto.setApprovedById(po.getApprovedBy().getId());
        }
        if (po.getRequestedBy() != null) {
            dto.setRequestedById(po.getRequestedBy().getId());
        }
        if (po.getRejectedBy() != null) {
            dto.setRejectedById(po.getRejectedBy().getId());
        }
        dto.setRequestedByName(UserDisplayNames.of(po.getRequestedBy()));
        dto.setApprovedByName(UserDisplayNames.of(po.getApprovedBy()));
        dto.setRejectedByName(UserDisplayNames.of(po.getRejectedBy()));
        dto.setApprovedAt(po.getApprovedAt());
        dto.setRejectedAt(po.getRejectedAt());
        dto.setRejectionReason(po.getRejectionReason());
        dto.setExpectedDeliveryDate(po.getExpectedDeliveryDate());
        dto.setRemarks(po.getRemarks());
        dto.setOrganisationId(po.getOrganisation().getId());
        dto.setDepartmentId(po.getDepartment().getId());
        dto.setSupplierId(po.getSupplier().getId());
        if (po.getLinkedBudget() != null) {
            dto.setLinkedBudgetId(po.getLinkedBudget().getId());
        }
        dto.setCreatedAt(po.getCreatedAt());
        dto.setUpdatedAt(po.getUpdatedAt());
        dto.setLineItems(liveLinesOf(po.getId()).stream().map(PurchaseOrderServiceImpl::mapLineToDto).toList());
        return dto;
    }

    // ── Line items ───────────────────────────────────────────────────────────
    //
    // The set is replaced wholesale rather than merged: a line is identified by its
    // position on the printed order, and diffing client-supplied ids would let a
    // caller move one order's line onto another. Replaced lines are soft-deleted so
    // the order's history survives, and the partial unique index (V56) lets the
    // replacements reuse the same line numbers in the same transaction.

    private List<PoLineItem> liveLinesOf(UUID purchaseOrderId) {
        return lineItemRepository.findByPurchaseOrderIdAndDeletedAtIsNullOrderByLineNumberAsc(purchaseOrderId);
    }

    /**
     * Replaces an order's lines and re-derives its total.
     *
     * <p>With lines, the stored total is the sum of the line totals: the two cannot
     * be allowed to disagree, because approval commits the stored total against a
     * budget. With no lines the order stays the lump sum the buyer typed.
     */
    private void replaceLineItems(PurchaseOrder po, Organisation org, List<PoLineItemDto> requested) {
        Instant now = Instant.now();
        List<PoLineItem> existing = liveLinesOf(po.getId());
        existing.forEach(line -> line.setDeletedAt(now));
        lineItemRepository.saveAll(existing);
        // Flush the soft deletes before the replacements land: both sets share the
        // (purchase_order_id, line_number) partial unique index.
        lineItemRepository.flush();

        if (requested.isEmpty()) {
            return;
        }

        List<PoLineItem> lines = new ArrayList<>();
        int lineNumber = 1;
        for (PoLineItemDto dto : requested) {
            lines.add(toLineItem(po, org, dto, lineNumber++));
        }
        lineItemRepository.saveAll(lines);
        po.setTotalAmount(PurchaseOrderTotals.orderTotalOf(requested));
    }

    private PoLineItem toLineItem(PurchaseOrder po, Organisation org, PoLineItemDto dto, int lineNumber) {
        if (dto.getDescription() == null || dto.getDescription().isBlank()) {
            throw new IllegalArgumentException("Every purchase order line needs a description");
        }
        if (dto.getQuantity() == null || dto.getQuantity().signum() <= 0) {
            throw new IllegalArgumentException("Line " + lineNumber + ": quantity must be greater than zero");
        }
        if (dto.getUnitPrice() == null || dto.getUnitPrice().signum() < 0) {
            throw new IllegalArgumentException("Line " + lineNumber + ": unit price cannot be negative");
        }

        PoLineItem line = new PoLineItem();
        line.setOrganisation(org);
        line.setPurchaseOrder(po);
        line.setLineNumber(lineNumber);
        line.setDescription(dto.getDescription().trim());
        line.setSupplierPartNumber(blankToNull(dto.getSupplierPartNumber()));
        if (dto.getCategoryId() != null) {
            line.setCategory(requireCategory(dto.getCategoryId(), org));
        }
        line.setQuantity(dto.getQuantity());
        line.setUnitPrice(dto.getUnitPrice());
        line.setTaxRate(dto.getTaxRate());
        BigDecimal net = PurchaseOrderTotals.netOf(dto.getQuantity(), dto.getUnitPrice());
        line.setTaxAmount(PurchaseOrderTotals.taxOf(net, dto.getTaxRate(), dto.getTaxAmount()));
        line.setLineTotal(PurchaseOrderTotals.lineTotalOf(dto.getQuantity(), dto.getUnitPrice(),
                dto.getTaxRate(), dto.getTaxAmount()));
        return line;
    }

    private Category requireCategory(UUID categoryId, Organisation org) {
        return categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(categoryId, org)
                .orElseThrow(() -> new IllegalArgumentException("Category not found in your organisation"));
    }

    private static PoLineItemDto mapLineToDto(PoLineItem line) {
        PoLineItemDto dto = new PoLineItemDto();
        dto.setId(line.getId());
        dto.setLineNumber(line.getLineNumber());
        dto.setDescription(line.getDescription());
        dto.setSupplierPartNumber(line.getSupplierPartNumber());
        if (line.getCategory() != null) {
            dto.setCategoryId(line.getCategory().getId());
            dto.setCategoryName(line.getCategory().getName());
        }
        dto.setQuantity(line.getQuantity());
        dto.setUnitPrice(line.getUnitPrice());
        dto.setTaxRate(line.getTaxRate());
        dto.setTaxAmount(line.getTaxAmount());
        dto.setLineTotal(line.getLineTotal());
        return dto;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private User resolveCurrentUser(Organisation org) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        return userRepository.findByEmailAndOrganisationId(auth.getName(), org.getId())
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated user is not an active member of this organisation"));
    }
}
