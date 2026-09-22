package com.assetiq.controllers.v1;

import com.assetiq.dto.PurchaseOrderDto;
import com.assetiq.enums.POStatus;
import com.assetiq.services.PurchaseOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.assetiq.security.annotation.RequireFreshMfa;
import com.assetiq.validation.OnCreate;

import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService poService;

    public PurchaseOrderController(PurchaseOrderService poService) {
        this.poService = poService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_EXPENSES','MANAGE_PROCUREMENT')")
    public ResponseEntity<PurchaseOrderDto> createPurchaseOrder(
            @Validated(OnCreate.class) @RequestBody PurchaseOrderDto poDto) {
        PurchaseOrderDto createdPo = poService.createPurchaseOrder(poDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPo);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_PROCUREMENT','MANAGE_PROCUREMENT')")
    public ResponseEntity<PurchaseOrderDto> getPurchaseOrderById(@PathVariable UUID id) {
        PurchaseOrderDto po = poService.getPurchaseOrderById(id);
        return ResponseEntity.ok(po);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_PROCUREMENT','MANAGE_PROCUREMENT')")
    public ResponseEntity<Set<PurchaseOrderDto>> getPurchaseOrders(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) POStatus status) {

        if (departmentId != null) {
            return ResponseEntity.ok(poService.getPurchaseOrdersByDepartment(departmentId));
        } else if (supplierId != null) {
            return ResponseEntity.ok(poService.getPurchaseOrdersBySupplier(supplierId));
        } else if (status != null) {
            return ResponseEntity.ok(poService.getPurchaseOrdersByStatus(status));
        }
        // H8: No filter → return all for this tenant org
        return ResponseEntity.ok(poService.getPurchaseOrdersByOrganisation(null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_EXPENSES','MANAGE_PROCUREMENT')")
    public ResponseEntity<PurchaseOrderDto> updatePurchaseOrder(@PathVariable UUID id,
            @Validated(OnCreate.class) @RequestBody PurchaseOrderDto poDto) {
        PurchaseOrderDto updatedPo = poService.updatePurchaseOrder(id, poDto);
        return ResponseEntity.ok(updatedPo);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_EXPENSES','MANAGE_PROCUREMENT')")
    public ResponseEntity<PurchaseOrderDto> patchPurchaseOrder(@PathVariable UUID id,
            @Valid @RequestBody PurchaseOrderDto poDto) {
        PurchaseOrderDto updatedPo = poService.patchPurchaseOrder(id, poDto);
        return ResponseEntity.ok(updatedPo);
    }

    /** DRAFT -> SUBMITTED. The caller becomes the maker and cannot approve the order. */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_EXPENSES','MANAGE_PROCUREMENT')")
    public ResponseEntity<PurchaseOrderDto> submitPurchaseOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(poService.submitPurchaseOrder(id));
    }

    @PostMapping("/{id}/approve")
    @RequireFreshMfa
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_EXPENSES','APPROVE_PROCUREMENT','APPROVE_REQUESTS')")
    // C4 fix: approver resolved from SecurityContext in service — no approvedById
    // param
    public ResponseEntity<PurchaseOrderDto> approvePurchaseOrder(@PathVariable UUID id) {
        PurchaseOrderDto approvedPo = poService.approvePurchaseOrder(id);
        return ResponseEntity.ok(approvedPo);
    }

    @PostMapping("/{id}/reject")
    @RequireFreshMfa
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_EXPENSES','APPROVE_PROCUREMENT','REJECT_REQUESTS')")
    public ResponseEntity<PurchaseOrderDto> rejectPurchaseOrder(@PathVariable UUID id) {
        PurchaseOrderDto rejectedPo = poService.rejectPurchaseOrder(id);
        return ResponseEntity.ok(rejectedPo);
    }

    /** APPROVED -> DELIVERED: goods received; the budget commitment becomes spend. */
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_PROCUREMENT','APPROVE_PROCUREMENT')")
    public ResponseEntity<PurchaseOrderDto> receivePurchaseOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(poService.receivePurchaseOrder(id));
    }

    /** DRAFT/SUBMITTED/APPROVED -> CANCELLED; an approved order's commitment is released. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_PROCUREMENT','APPROVE_PROCUREMENT')")
    public ResponseEntity<PurchaseOrderDto> cancelPurchaseOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(poService.cancelPurchaseOrder(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_EXPENSES','MANAGE_PROCUREMENT')")
    public ResponseEntity<Void> deletePurchaseOrder(@PathVariable UUID id) {
        poService.deletePurchaseOrder(id);
        return ResponseEntity.noContent().build();
    }
}
