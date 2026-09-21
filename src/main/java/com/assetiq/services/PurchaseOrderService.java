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

    /** DRAFT -> SUBMITTED. The submitter becomes the maker, who cannot then approve or reject. */
    PurchaseOrderDto submitPurchaseOrder(UUID id);

    /** SUBMITTED -> APPROVED; commits the amount against the linked budget (funds-checked). */
    PurchaseOrderDto approvePurchaseOrder(UUID id); // C4: approver from SecurityContext

    /** SUBMITTED -> REJECTED. */
    PurchaseOrderDto rejectPurchaseOrder(UUID id);

    /** APPROVED -> DELIVERED; converts the budget commitment into spend. */
    PurchaseOrderDto receivePurchaseOrder(UUID id);

    /** DRAFT/SUBMITTED/APPROVED -> CANCELLED; releases an approved order's commitment. */
    PurchaseOrderDto cancelPurchaseOrder(UUID id);

    /** Soft delete; releases an open commitment or reverses recorded spend. */
    void deletePurchaseOrder(UUID id);
}
