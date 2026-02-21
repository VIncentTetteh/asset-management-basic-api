package com.example.demo.controllers.v1;

import com.example.demo.dto.SupplierDto;
import com.example.demo.services.SupplierService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<SupplierDto> createSupplier(@Valid @RequestBody SupplierDto supplierDto,
                                                     @RequestParam UUID organisationId) {
        SupplierDto createdSupplier = supplierService.createSupplier(supplierDto, organisationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSupplier);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupplierDto> getSupplierById(@PathVariable UUID id) {
        SupplierDto supplier = supplierService.getSupplierById(id);
        return ResponseEntity.ok(supplier);
    }

    @GetMapping
    public ResponseEntity<Set<SupplierDto>> getSuppliersByOrganisation(@RequestParam UUID organisationId) {
        Set<SupplierDto> suppliers = supplierService.getSuppliersByOrganisation(organisationId);
        return ResponseEntity.ok(suppliers);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SupplierDto> updateSupplier(@PathVariable UUID id,
                                                     @Valid @RequestBody SupplierDto supplierDto) {
        SupplierDto updatedSupplier = supplierService.updateSupplier(id, supplierDto);
        return ResponseEntity.ok(updatedSupplier);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable UUID id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-email")
    public ResponseEntity<SupplierDto> getSupplierByEmail(@RequestParam String email,
                                                         @RequestParam UUID organisationId) {
        SupplierDto supplier = supplierService.getSupplierByEmail(email, organisationId);
        return ResponseEntity.ok(supplier);
    }
}

