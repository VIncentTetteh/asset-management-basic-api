package com.assetiq.services.impl;

import com.assetiq.dto.PurchaseOrderDto;
import com.assetiq.enums.POStatus;
import com.assetiq.models.Budget;
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
import com.assetiq.services.TenantAwareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PurchaseOrderServiceImpl extends TenantAwareService implements PurchaseOrderService {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderServiceImpl.class);

    private final PurchaseOrderRepository poRepository;
    private final DepartmentRepository departmentRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;
    private final NotificationService notificationService;
    private final CurrencyResolver currencyResolver;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository poRepository,
            OrganisationRepository organisationRepository,
            DepartmentRepository departmentRepository,
            SupplierRepository supplierRepository,
            UserRepository userRepository,
            BudgetRepository budgetRepository,
            NotificationService notificationService,
            CurrencyResolver currencyResolver) {
        super(organisationRepository);
        this.poRepository = poRepository;
        this.departmentRepository = departmentRepository;
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
        this.budgetRepository = budgetRepository;
        this.notificationService = notificationService;
        this.currencyResolver = currencyResolver;
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
        po.setOrganisation(org);
        po.setDepartment(department);
        po.setSupplier(supplier);
        po.setRequestedBy(resolveCurrentUser(org));

        if (poDto.getLinkedBudgetId() != null) {
            budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(poDto.getLinkedBudgetId(), org)
                    .ifPresent(po::setLinkedBudget);
        }

        PurchaseOrder saved = poRepository.save(po);
        logger.info("Created Purchase Order {} (PO Number: {})", saved.getId(), saved.getPoNumber());
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
        PurchaseOrder po = poRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));

        if (po.getStatus() != POStatus.DRAFT) {
            throw new IllegalStateException("Cannot update a non-draft purchase order");
        }

        po.setPoNumber(poDto.getPoNumber());
        po.setTotalAmount(poDto.getTotalAmount());
        po.setCurrency(currencyResolver.resolveOrDefault(poDto.getCurrency()));
        po.setRemarks(poDto.getRemarks());

        return mapToDto(poRepository.save(po));
    }

    @Override
    public PurchaseOrderDto patchPurchaseOrder(UUID id, PurchaseOrderDto poDto) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = poRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));

        if (po.getStatus() != POStatus.DRAFT) {
            throw new IllegalStateException("Cannot update a non-draft purchase order");
        }

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
        if (poDto.getDepartmentId() != null) {
            Department department = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                    poDto.getDepartmentId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
            po.setDepartment(department);
        }
        if (poDto.getSupplierId() != null) {
            Supplier supplier = supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                    poDto.getSupplierId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Supplier not found in your organisation"));
            po.setSupplier(supplier);
        }

        return mapToDto(poRepository.save(po));
    }

    @Override
    public PurchaseOrderDto approvePurchaseOrder(UUID id) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = poRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));

        if (po.getStatus() == POStatus.APPROVED) {
            logger.warn("Purchase Order {} is already approved", id);
            return mapToDto(po);
        }

        if (po.getStatus() == POStatus.REJECTED) {
            throw new IllegalStateException("Cannot approve a rejected purchase order. Create a new one instead.");
        }

        User approver = resolveCurrentUser(org);
        if (po.getRequestedBy() != null && po.getRequestedBy().getId().equals(approver.getId())) {
            throw new IllegalStateException("A purchase order requester cannot approve their own order");
        }
        po.setApprovedBy(approver);
        logger.info("Purchase Order {} approved by user {}", id, approver.getEmail());
        po.setStatus(POStatus.APPROVED);
        po.setApprovedAt(Instant.now());

        // Auto-deduct from linked budget when PO is approved
        if (po.getLinkedBudget() != null && po.getTotalAmount() != null) {
            Budget budget = po.getLinkedBudget();
            if (budget.getCurrency() == null || po.getCurrency() == null
                    || !budget.getCurrency().equalsIgnoreCase(po.getCurrency())) {
                throw new IllegalStateException("Purchase order and linked budget currencies must match");
            }
            java.math.BigDecimal alreadySpent = budget.getSpentAmount() == null
                    ? java.math.BigDecimal.ZERO : budget.getSpentAmount();
            budget.setSpentAmount(alreadySpent.add(po.getTotalAmount()));
            budgetRepository.save(budget);
            logger.info("Auto-deducted {} {} from budget {} for approved PO {}",
                    po.getTotalAmount(), po.getCurrency(), budget.getId(), po.getId());
        }

        PurchaseOrder approved = poRepository.save(po);
        notificationService.notifyOrgAdmins(org, NotificationType.APPROVAL,
                "Purchase Order Approved",
                "Purchase Order '" + approved.getPoNumber() + "' has been approved.",
                approved.getId(), "/purchase-orders");
        return mapToDto(approved);
    }

    @Override
    public PurchaseOrderDto rejectPurchaseOrder(UUID id) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = poRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));

        if (po.getStatus() == POStatus.APPROVED) {
            throw new IllegalStateException("Cannot reject an already approved purchase order");
        }

        if (po.getStatus() == POStatus.REJECTED) {
            logger.warn("Purchase Order {} is already rejected", id);
            return mapToDto(po);
        }

        User rejector = resolveCurrentUser(org);
        if (po.getRequestedBy() != null && po.getRequestedBy().getId().equals(rejector.getId())) {
            throw new IllegalStateException("A purchase order requester cannot reject their own order");
        }
        po.setStatus(POStatus.REJECTED);
        po.setRejectedBy(rejector);
        po.setRejectedAt(Instant.now());
        logger.info("Purchase Order {} rejected by user {}", id, rejector.getEmail());
        PurchaseOrder rejected = poRepository.save(po);
        notificationService.notifyOrgAdmins(org, NotificationType.APPROVAL,
                "Purchase Order Rejected",
                "Purchase Order '" + rejected.getPoNumber() + "' has been rejected.",
                rejected.getId(), "/purchase-orders");
        return mapToDto(rejected);
    }

    @Override
    public void deletePurchaseOrder(UUID id) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = poRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        po.setDeletedAt(Instant.now());
        poRepository.save(po);
        logger.info("Soft-deleted Purchase Order {} (PO Number: {})", id, po.getPoNumber());
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
        dto.setApprovedAt(po.getApprovedAt());
        dto.setRejectedAt(po.getRejectedAt());
        dto.setRemarks(po.getRemarks());
        dto.setOrganisationId(po.getOrganisation().getId());
        dto.setDepartmentId(po.getDepartment().getId());
        dto.setSupplierId(po.getSupplier().getId());
        if (po.getLinkedBudget() != null) {
            dto.setLinkedBudgetId(po.getLinkedBudget().getId());
        }
        dto.setCreatedAt(po.getCreatedAt());
        dto.setUpdatedAt(po.getUpdatedAt());
        return dto;
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
