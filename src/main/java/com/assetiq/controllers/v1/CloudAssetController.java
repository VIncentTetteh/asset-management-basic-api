package com.assetiq.controllers.v1;

import com.assetiq.dto.CloudAssetDto;
import com.assetiq.dto.CloudCostSummaryDto;
import com.assetiq.dto.PagedResponseDto;
import com.assetiq.enums.CloudProvider;
import com.assetiq.services.CloudAssetService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_ASSETS','EDIT_ASSET','MANAGE_CLOUD_ASSETS')")
    public ResponseEntity<CloudAssetDto> create(@Valid @RequestBody CloudAssetDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cloudAssetService.create(dto));
    }

    /**
     * GET /api/v1/cloud-assets?provider=AWS&environment=PROD
     * List cloud assets with optional filters.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_ASSETS','VIEW_CLOUD_ASSETS','MANAGE_CLOUD_ASSETS')")
    public ResponseEntity<PagedResponseDto<CloudAssetDto>> list(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long offset,
            @PageableDefault(size = 20) Pageable pageable) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : pageable.getPageSize();
        long effectiveOffset = (offset != null && offset >= 0)
                ? offset
                : (long) pageable.getPageNumber() * effectiveLimit;

        Pageable effectivePageable = PageRequest.of((int) (effectiveOffset / effectiveLimit), effectiveLimit, pageable.getSort());
        Page<CloudAssetDto> page = cloudAssetService.list(provider, environment, effectivePageable);

        PagedResponseDto<CloudAssetDto> response = new PagedResponseDto<>();
        response.setTotal(page.getTotalElements());
        response.setLimit(effectiveLimit);
        response.setOffset(effectiveOffset);
        response.setItems(page.getContent());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/cloud-assets/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_ASSETS','VIEW_CLOUD_ASSETS','MANAGE_CLOUD_ASSETS')")
    public ResponseEntity<CloudAssetDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(cloudAssetService.getById(id));
    }

    /**
     * PUT /api/v1/cloud-assets/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_ASSETS','EDIT_ASSET','MANAGE_CLOUD_ASSETS')")
    public ResponseEntity<CloudAssetDto> update(@PathVariable UUID id, @Valid @RequestBody CloudAssetDto dto) {
        return ResponseEntity.ok(cloudAssetService.update(id, dto));
    }

    /**
     * DELETE /api/v1/cloud-assets/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_ASSETS','EDIT_ASSET','MANAGE_CLOUD_ASSETS')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        cloudAssetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/cloud-assets/cost-summary
     * Cost breakdown by provider and environment.
     */
    @GetMapping("/cost-summary")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_ASSETS','VIEW_CLOUD_ASSETS','MANAGE_CLOUD_ASSETS','VIEW_REPORTS')")
    public ResponseEntity<CloudCostSummaryDto> costSummary() {
        return ResponseEntity.ok(cloudAssetService.getCostSummary());
    }

    /**
     * POST /api/v1/cloud-assets/{id}/cost
     * Record a monthly cost entry.
     * Body: { "billingMonth": "2025-01", "amount": 120.50, "serviceName": "EC2 Compute" }
     */
    @PostMapping("/{id}/cost")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_ASSETS','EDIT_ASSET','MANAGE_CLOUD_ASSETS')")
    public ResponseEntity<Void> recordCost(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String billingMonth = (String) body.get("billingMonth");
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String serviceName = (String) body.getOrDefault("serviceName", null);
        cloudAssetService.recordMonthlyCost(id, billingMonth, amount, serviceName);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/cloud-assets/sync
     * Trigger an immediate asset discovery scan for a specific cloud provider.
     *
     * <p>Body: {@code { "provider": "AWS", "regions": ["us-east-1", "eu-west-1"] }}
     * <br>{@code regions} is optional — omit to use each provider's configured default.
     * <br>Supported providers: {@code AWS}, {@code AZURE}, {@code GCP}.
     *
     * <p>The operation runs synchronously and may take several seconds for large accounts.
     * For background scans use the scheduled sync instead.
     */
    @PostMapping("/sync")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_CLOUD_ASSETS')")
    public ResponseEntity<Map<String, Object>> sync(
            @RequestBody(required = false) Map<String, Object> body) {

        if (body == null || !body.containsKey("provider")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Request body must include a 'provider' field (e.g. AWS, AZURE, GCP)."));
        }

        CloudProvider provider;
        try {
            provider = CloudProvider.valueOf(body.get("provider").toString().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Unknown provider '" + body.get("provider") + "'. Valid values: AWS, AZURE, GCP."));
        }

        @SuppressWarnings("unchecked")
        List<String> regions = (body.containsKey("regions"))
                ? (List<String>) body.get("regions")
                : null;

        int upserted = cloudAssetService.syncFromCloud(provider, regions);
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "provider", provider.name(),
                "assetsUpserted", upserted,
                "message", provider.name() + " asset sync completed. " + upserted + " asset(s) upserted."));
    }

    /**
     * POST /api/v1/cloud-assets/sync/all
     * Trigger asset discovery across ALL configured cloud providers simultaneously.
     *
     * <p>Body (optional): {@code { "regions": ["us-east-1", "europe-west1"] }}
     * <br>Regions are forwarded to all providers; providers that don't recognise a
     * region code will use their own defaults.
     */
    @PostMapping("/sync/all")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_CLOUD_ASSETS')")
    public ResponseEntity<Map<String, Object>> syncAll(
            @RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> regions = (body != null && body.containsKey("regions"))
                ? (List<String>) body.get("regions")
                : null;
        int upserted = cloudAssetService.syncAll(regions);
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "assetsUpserted", upserted,
                "message", "Multi-cloud sync completed. " + upserted + " total asset(s) upserted."));
    }
}
