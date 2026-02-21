package com.example.demo.controllers.v1;

import com.example.demo.dto.PurchaseOrderDto;
import com.example.demo.enums.POStatus;
import com.example.demo.services.PurchaseOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<PurchaseOrderDto> createPurchaseOrder(@Valid @RequestBody PurchaseOrderDto poDto) {
        PurchaseOrderDto createdPo = poService.createPurchaseOrder(poDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPo);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrderDto> getPurchaseOrderById(@PathVariable UUID id) {
        PurchaseOrderDto po = poService.getPurchaseOrderById(id);
        return ResponseEntity.ok(po);
    }

    @GetMapping
    public ResponseEntity<Set<PurchaseOrderDto>> getPurchaseOrders(
            @RequestParam(required = false) UUID organisationId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) POStatus status) {

        if (organisationId != null) {
            return ResponseEntity.ok(poService.getPurchaseOrdersByOrganisation(organisationId));
        } else if (departmentId != null) {
            return ResponseEntity.ok(poService.getPurchaseOrdersByDepartment(departmentId));
        } else if (supplierId != null) {
            return ResponseEntity.ok(poService.getPurchaseOrdersBySupplier(supplierId));
        } else if (status != null) {
            return ResponseEntity.ok(poService.getPurchaseOrdersByStatus(status));
        }
        return ResponseEntity.badRequest().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<PurchaseOrderDto> updatePurchaseOrder(@PathVariable UUID id,
                                                               @Valid @RequestBody PurchaseOrderDto poDto) {
        PurchaseOrderDto updatedPo = poService.updatePurchaseOrder(id, poDto);
        return ResponseEntity.ok(updatedPo);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PurchaseOrderDto> approvePurchaseOrder(@PathVariable UUID id,
                                                                @RequestParam UUID approvedById) {
        PurchaseOrderDto approvedPo = poService.approvePurchaseOrder(id, approvedById);
        return ResponseEntity.ok(approvedPo);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<PurchaseOrderDto> rejectPurchaseOrder(@PathVariable UUID id) {
        PurchaseOrderDto rejectedPo = poService.rejectPurchaseOrder(id);
        return ResponseEntity.ok(rejectedPo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePurchaseOrder(@PathVariable UUID id) {
        poService.deletePurchaseOrder(id);
        return ResponseEntity.noContent().build();
    }
}

