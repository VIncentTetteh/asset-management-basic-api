package com.example.demo.services.impl;

import com.example.demo.dto.PurchaseOrderDto;
import com.example.demo.enums.POStatus;
import com.example.demo.models.Budget;
import com.example.demo.models.PurchaseOrder;
import com.example.demo.models.Organisation;
import com.example.demo.models.Department;
import com.example.demo.models.Supplier;
import com.example.demo.models.User;
import com.example.demo.repositories.*;
import com.example.demo.enums.NotificationType;
import com.example.demo.services.NotificationService;
import com.example.demo.services.PurchaseOrderService;
import com.example.demo.services.TenantAwareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    public PurchaseOrderServiceImpl(PurchaseOrderRepository poRepository,
            OrganisationRepository organisationRepository,
            DepartmentRepository departmentRepository,
            SupplierRepository supplierRepository,
            UserRepository userRepository,
            BudgetRepository budgetRepository,
            NotificationService notificationService) {
        super(organisationRepository);
        this.poRepository = poRepository;
        this.departmentRepository = departmentRepository;
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
        this.budgetRepository = budgetRepository;
        this.notificationService = notificationService;
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
        po.setCurrency(poDto.getCurrency() != null ? poDto.getCurrency() : "USD");
        po.setStatus(poDto.getStatus() != null ? poDto.getStatus() : POStatus.DRAFT);
        po.setRemarks(poDto.getRemarks());
        po.setOrganisation(org);
        po.setDepartment(department);
        po.setSupplier(supplier);

        if (poDto.getLinkedBudgetId() != null) {
            budgetRepository.findByIdAndOrganisationAndDeletedAtIsNull(poDto.getLinkedBudgetId(), org)
                    .ifPresent(po::setLinkedBudget);
        }

        PurchaseOrder saved = poRepository.save(po);
        logger.info("Created Purchase Order {} (PO Number: {})", saved.getId(), saved.getPoNumber());
        notificationService.notifyOrgAdmins(org, NotificationType.PURCHASE_ORDER,
                "Purchase Order Created",
                "Purchase Order '" + saved.getPoNumber() + "' has been created for " + supplier.getName() + ".",
                saved.getId(), "/api/v1/purchase-orders/" + saved.getId());
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
        po.setCurrency(poDto.getCurrency());
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
            po.setCurrency(poDto.getCurrency());
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

        // C4 fix: resolve approver from authenticated user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            User approver = userRepository.findByEmailAndOrganisationId(auth.getName(), org.getId())
                    .orElse(null);
            if (approver != null) {
                po.setApprovedBy(approver);
                logger.info("Purchase Order {} approved by user {}", id, approver.getEmail());
            }
        }
        po.setStatus(POStatus.APPROVED);
        po.setApprovedAt(Instant.now());

        // Auto-deduct from linked budget when PO is approved
        if (po.getLinkedBudget() != null && po.getTotalAmount() != null) {
            Budget budget = po.getLinkedBudget();
            budget.setSpentAmount(budget.getSpentAmount().add(po.getTotalAmount()));
            budgetRepository.save(budget);
            logger.info("Auto-deducted {} {} from budget {} for approved PO {}",
                    po.getTotalAmount(), po.getCurrency(), budget.getId(), po.getId());
        }

        PurchaseOrder approved = poRepository.save(po);
        notificationService.notifyOrgAdmins(org, NotificationType.APPROVAL,
                "Purchase Order Approved",
                "Purchase Order '" + approved.getPoNumber() + "' has been approved.",
                approved.getId(), "/api/v1/purchase-orders/" + approved.getId());
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

        po.setStatus(POStatus.REJECTED);
        logger.info("Purchase Order {} rejected", id);
        PurchaseOrder rejected = poRepository.save(po);
        notificationService.notifyOrgAdmins(org, NotificationType.APPROVAL,
                "Purchase Order Rejected",
                "Purchase Order '" + rejected.getPoNumber() + "' has been rejected.",
                rejected.getId(), "/api/v1/purchase-orders/" + rejected.getId());
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
}
