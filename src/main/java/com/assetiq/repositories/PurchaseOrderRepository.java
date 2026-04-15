package com.assetiq.repositories;

import com.assetiq.enums.POStatus;
import com.assetiq.models.PurchaseOrder;
import com.assetiq.models.Organisation;
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

    // Tenant + soft-delete scoped
    Optional<PurchaseOrder> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    Set<PurchaseOrder> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Set<PurchaseOrder> findByDepartmentIdAndDeletedAtIsNull(UUID departmentId);

    Set<PurchaseOrder> findByOrganisationAndStatusAndDeletedAtIsNull(Organisation organisation, POStatus status);
}
