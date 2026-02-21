package com.example.demo.repositories;

import com.example.demo.enums.POStatus;
import com.example.demo.models.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Optional<PurchaseOrder> findByPoNumber(String poNumber);
    Set<PurchaseOrder> findByOrganisationId(UUID organisationId);
    Set<PurchaseOrder> findByDepartmentId(UUID departmentId);
    Set<PurchaseOrder> findBySupplierId(UUID supplierId);
    Set<PurchaseOrder> findByStatus(POStatus status);
}

