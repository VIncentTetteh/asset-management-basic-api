package com.example.demo.services.impl;

import com.example.demo.dto.PurchaseOrderDto;
import com.example.demo.enums.POStatus;
import com.example.demo.models.PurchaseOrder;
import com.example.demo.models.Organisation;
import com.example.demo.models.Department;
import com.example.demo.models.Supplier;
import com.example.demo.models.User;
import com.example.demo.repositories.*;
import com.example.demo.services.PurchaseOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository poRepository;
    private final OrganisationRepository organisationRepository;
    private final DepartmentRepository departmentRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository poRepository,
                                  OrganisationRepository organisationRepository,
                                  DepartmentRepository departmentRepository,
                                  SupplierRepository supplierRepository,
                                  UserRepository userRepository) {
        this.poRepository = poRepository;
        this.organisationRepository = organisationRepository;
        this.departmentRepository = departmentRepository;
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
    }

    @Override
    public PurchaseOrderDto createPurchaseOrder(PurchaseOrderDto poDto) {
        Organisation organisation = organisationRepository.findById(poDto.getOrganisationId())
            .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));
        Department department = departmentRepository.findById(poDto.getDepartmentId())
            .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        Supplier supplier = supplierRepository.findById(poDto.getSupplierId())
            .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));

        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber(poDto.getPoNumber());
        po.setTotalAmount(poDto.getTotalAmount());
        po.setCurrency(poDto.getCurrency() != null ? poDto.getCurrency() : "USD");
        po.setStatus(poDto.getStatus() != null ? poDto.getStatus() : POStatus.DRAFT);
        po.setRemarks(poDto.getRemarks());
        po.setOrganisation(organisation);
        po.setDepartment(department);
        po.setSupplier(supplier);

        PurchaseOrder savedPo = poRepository.save(po);
        return mapToDto(savedPo);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderDto getPurchaseOrderById(UUID id) {
        PurchaseOrder po = poRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        return mapToDto(po);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<PurchaseOrderDto> getPurchaseOrdersByOrganisation(UUID organisationId) {
        return poRepository.findByOrganisationId(organisationId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<PurchaseOrderDto> getPurchaseOrdersByDepartment(UUID departmentId) {
        return poRepository.findByDepartmentId(departmentId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<PurchaseOrderDto> getPurchaseOrdersBySupplier(UUID supplierId) {
        return poRepository.findBySupplierId(supplierId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<PurchaseOrderDto> getPurchaseOrdersByStatus(POStatus status) {
        return poRepository.findByStatus(status).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public PurchaseOrderDto updatePurchaseOrder(UUID id, PurchaseOrderDto poDto) {
        PurchaseOrder po = poRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));

        if (po.getStatus() != POStatus.DRAFT) {
            throw new IllegalStateException("Cannot update a non-draft purchase order");
        }

        po.setPoNumber(poDto.getPoNumber());
        po.setTotalAmount(poDto.getTotalAmount());
        po.setCurrency(poDto.getCurrency());
        po.setRemarks(poDto.getRemarks());

        PurchaseOrder updatedPo = poRepository.save(po);
        return mapToDto(updatedPo);
    }

    @Override
    public PurchaseOrderDto approvePurchaseOrder(UUID id, UUID approvedById) {
        PurchaseOrder po = poRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        User approver = userRepository.findById(approvedById)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        po.setStatus(POStatus.APPROVED);
        po.setApprovedBy(approver);
        po.setApprovedAt(Instant.now());

        PurchaseOrder updatedPo = poRepository.save(po);
        return mapToDto(updatedPo);
    }

    @Override
    public PurchaseOrderDto rejectPurchaseOrder(UUID id) {
        PurchaseOrder po = poRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));

        po.setStatus(POStatus.REJECTED);

        PurchaseOrder updatedPo = poRepository.save(po);
        return mapToDto(updatedPo);
    }

    @Override
    public void deletePurchaseOrder(UUID id) {
        poRepository.deleteById(id);
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

