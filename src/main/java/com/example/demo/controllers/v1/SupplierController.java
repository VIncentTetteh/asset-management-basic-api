package com.example.demo.controllers.v1;

import com.example.demo.dto.SupplierDto;
import com.example.demo.services.SupplierService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_SUPPLIERS')")
    public ResponseEntity<SupplierDto> createSupplier(@Valid @RequestBody SupplierDto supplierDto) {
        SupplierDto createdSupplier = supplierService.createSupplier(supplierDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSupplier);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_SUPPLIERS','MANAGE_SUPPLIERS')")
    public ResponseEntity<SupplierDto> getSupplierById(@PathVariable UUID id) {
        SupplierDto supplier = supplierService.getSupplierById(id);
        return ResponseEntity.ok(supplier);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_SUPPLIERS','MANAGE_SUPPLIERS')")
    public ResponseEntity<Set<SupplierDto>> getSuppliersByOrganisation() {
        Set<SupplierDto> suppliers = supplierService.getSuppliersByOrganisation();
        return ResponseEntity.ok(suppliers);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_SUPPLIERS')")
    public ResponseEntity<SupplierDto> updateSupplier(@PathVariable UUID id,
                                                     @Valid @RequestBody SupplierDto supplierDto) {
        SupplierDto updatedSupplier = supplierService.updateSupplier(id, supplierDto);
        return ResponseEntity.ok(updatedSupplier);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_SUPPLIERS')")
    public ResponseEntity<SupplierDto> patchSupplier(@PathVariable UUID id,
            @RequestBody SupplierDto supplierDto) {
        SupplierDto updatedSupplier = supplierService.patchSupplier(id, supplierDto);
        return ResponseEntity.ok(updatedSupplier);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_SUPPLIERS')")
    public ResponseEntity<Void> deleteSupplier(@PathVariable UUID id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-email")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_SUPPLIERS','MANAGE_SUPPLIERS')")
    public ResponseEntity<SupplierDto> getSupplierByEmail(@RequestParam String email) {
        SupplierDto supplier = supplierService.getSupplierByEmail(email);
        return ResponseEntity.ok(supplier);
    }
}
