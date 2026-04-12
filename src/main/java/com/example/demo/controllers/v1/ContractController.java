package com.example.demo.controllers.v1;

import com.example.demo.dto.ContractDto;
import com.example.demo.services.ContractService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_CONTRACTS')")
    public ResponseEntity<ContractDto> create(@Valid @RequestBody ContractDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contractService.create(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_CONTRACTS','MANAGE_CONTRACTS')")
    public ResponseEntity<ContractDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(contractService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_CONTRACTS','MANAGE_CONTRACTS')")
    public ResponseEntity<List<ContractDto>> list() {
        return ResponseEntity.ok(contractService.listAll());
    }

    /**
     * Returns contracts expiring within {@code days} days (default 30).
     * GET /api/v1/contracts/expiring-soon?days=60
     */
    @GetMapping("/expiring-soon")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','VIEW_CONTRACTS','MANAGE_CONTRACTS')")
    public ResponseEntity<List<ContractDto>> expiringSoon(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(contractService.listExpiringSoon(days));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_CONTRACTS')")
    public ResponseEntity<ContractDto> update(
            @PathVariable UUID id, @Valid @RequestBody ContractDto dto) {
        return ResponseEntity.ok(contractService.update(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_CONTRACTS')")
    public ResponseEntity<ContractDto> patch(
            @PathVariable UUID id, @RequestBody ContractDto dto) {
        return ResponseEntity.ok(contractService.patch(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_CONTRACTS')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        contractService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
