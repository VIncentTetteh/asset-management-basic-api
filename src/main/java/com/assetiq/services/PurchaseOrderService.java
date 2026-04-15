package com.assetiq.services;

import com.assetiq.dto.PurchaseOrderDto;
import com.assetiq.enums.POStatus;
import java.util.Set;
import java.util.UUID;

public interface PurchaseOrderService {
    PurchaseOrderDto createPurchaseOrder(PurchaseOrderDto poDto);

    PurchaseOrderDto getPurchaseOrderById(UUID id);

    Set<PurchaseOrderDto> getPurchaseOrdersByOrganisation(UUID organisationId);

    Set<PurchaseOrderDto> getPurchaseOrdersByDepartment(UUID departmentId);

    Set<PurchaseOrderDto> getPurchaseOrdersBySupplier(UUID supplierId);

    Set<PurchaseOrderDto> getPurchaseOrdersByStatus(POStatus status);

    PurchaseOrderDto updatePurchaseOrder(UUID id, PurchaseOrderDto poDto);
    PurchaseOrderDto patchPurchaseOrder(UUID id, PurchaseOrderDto poDto);

    PurchaseOrderDto approvePurchaseOrder(UUID id); // C4: approver from SecurityContext

    PurchaseOrderDto rejectPurchaseOrder(UUID id);

    void deletePurchaseOrder(UUID id);
}
