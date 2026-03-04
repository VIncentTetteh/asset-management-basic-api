package com.example.demo.services.impl;

import com.example.demo.dto.PurchaseOrderDto;
import com.example.demo.enums.POStatus;
import com.example.demo.models.PurchaseOrder;
import com.example.demo.models.Organisation;
import com.example.demo.models.Department;
import com.example.demo.models.Supplier;
import com.example.demo.repositories.*;
import com.example.demo.services.PurchaseOrderService;
import com.example.demo.services.TenantAwareService;
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

    private final PurchaseOrderRepository poRepository;
    private final DepartmentRepository departmentRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository poRepository,
            OrganisationRepository organisationRepository,
            DepartmentRepository departmentRepository,
            SupplierRepository supplierRepository,
            UserRepository userRepository) {
        super(organisationRepository);
        this.poRepository = poRepository;
        this.departmentRepository = departmentRepository;
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
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

        return mapToDto(poRepository.save(po));
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
    public PurchaseOrderDto approvePurchaseOrder(UUID id) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = poRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));

        // C4 fix: resolve approver from authenticated user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            userRepository.findByEmailAndOrganisationId(auth.getName(), org.getId())
                    .ifPresent(po::setApprovedBy);
        }
        po.setStatus(POStatus.APPROVED);
        po.setApprovedAt(Instant.now());

        return mapToDto(poRepository.save(po));
    }

    @Override
    public PurchaseOrderDto rejectPurchaseOrder(UUID id) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = poRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        po.setStatus(POStatus.REJECTED);
        return mapToDto(poRepository.save(po));
    }

    @Override
    public void deletePurchaseOrder(UUID id) {
        Organisation org = requireTenantOrg();
        PurchaseOrder po = poRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        po.setDeletedAt(Instant.now());
        poRepository.save(po);
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
        return dto;
    }
}
