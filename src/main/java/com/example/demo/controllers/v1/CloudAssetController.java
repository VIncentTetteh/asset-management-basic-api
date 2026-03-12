package com.example.demo.controllers.v1;

import com.example.demo.dto.CloudAssetDto;
import com.example.demo.dto.CloudCostSummaryDto;
import com.example.demo.services.CloudAssetService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Cloud Asset Management.
 * Base path: /api/v1/cloud-assets
 */
@RestController
@RequestMapping("/api/v1/cloud-assets")
@PreAuthorize("isAuthenticated()")
public class CloudAssetController {

    private final CloudAssetService cloudAssetService;

    public CloudAssetController(CloudAssetService cloudAssetService) {
        this.cloudAssetService = cloudAssetService;
    }

    /**
     * POST /api/v1/cloud-assets
     * Register a new cloud asset.
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<CloudAssetDto> create(@Valid @RequestBody CloudAssetDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cloudAssetService.create(dto));
    }

    /**
     * GET /api/v1/cloud-assets?provider=AWS&environment=PROD
     * List cloud assets with optional filters.
     */
    @GetMapping
    public ResponseEntity<Page<CloudAssetDto>> list(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String environment,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(cloudAssetService.list(provider, environment, pageable));
    }

    /**
     * GET /api/v1/cloud-assets/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CloudAssetDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(cloudAssetService.getById(id));
    }

    /**
     * PUT /api/v1/cloud-assets/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<CloudAssetDto> update(@PathVariable UUID id, @Valid @RequestBody CloudAssetDto dto) {
        return ResponseEntity.ok(cloudAssetService.update(id, dto));
    }

    /**
     * DELETE /api/v1/cloud-assets/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        cloudAssetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/cloud-assets/cost-summary
     * Cost breakdown by provider and environment.
     */
    @GetMapping("/cost-summary")
    public ResponseEntity<CloudCostSummaryDto> costSummary() {
        return ResponseEntity.ok(cloudAssetService.getCostSummary());
    }

    /**
     * POST /api/v1/cloud-assets/{id}/cost
     * Record a monthly cost entry.
     * Body: { "billingMonth": "2025-01", "amount": 120.50, "serviceName": "EC2 Compute" }
     */
    @PostMapping("/{id}/cost")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<Void> recordCost(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String billingMonth = (String) body.get("billingMonth");
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String serviceName = (String) body.getOrDefault("serviceName", null);
        cloudAssetService.recordMonthlyCost(id, billingMonth, amount, serviceName);
        return ResponseEntity.noContent().build();
    }
}
