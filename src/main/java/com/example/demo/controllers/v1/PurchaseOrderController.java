package com.example.demo.controllers.v1;

import com.example.demo.dto.PurchaseOrderDto;
import com.example.demo.enums.POStatus;
import com.example.demo.services.PurchaseOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PurchaseOrderDto> createPurchaseOrder(
            @Valid @RequestBody PurchaseOrderDto poDto) {
        PurchaseOrderDto createdPo = poService.createPurchaseOrder(poDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPo);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<PurchaseOrderDto> getPurchaseOrderById(@PathVariable UUID id) {
        PurchaseOrderDto po = poService.getPurchaseOrderById(id);
        return ResponseEntity.ok(po);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_USER')")
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
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PurchaseOrderDto> updatePurchaseOrder(@PathVariable UUID id,
            @Valid @RequestBody PurchaseOrderDto poDto) {
        PurchaseOrderDto updatedPo = poService.updatePurchaseOrder(id, poDto);
        return ResponseEntity.ok(updatedPo);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PurchaseOrderDto> patchPurchaseOrder(@PathVariable UUID id,
            @RequestBody PurchaseOrderDto poDto) {
        PurchaseOrderDto updatedPo = poService.patchPurchaseOrder(id, poDto);
        return ResponseEntity.ok(updatedPo);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    // C4 fix: approver resolved from SecurityContext in service — no approvedById
    // param
    public ResponseEntity<PurchaseOrderDto> approvePurchaseOrder(@PathVariable UUID id) {
        PurchaseOrderDto approvedPo = poService.approvePurchaseOrder(id);
        return ResponseEntity.ok(approvedPo);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PurchaseOrderDto> rejectPurchaseOrder(@PathVariable UUID id) {
        PurchaseOrderDto rejectedPo = poService.rejectPurchaseOrder(id);
        return ResponseEntity.ok(rejectedPo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deletePurchaseOrder(@PathVariable UUID id) {
        poService.deletePurchaseOrder(id);
        return ResponseEntity.noContent().build();
    }
}
