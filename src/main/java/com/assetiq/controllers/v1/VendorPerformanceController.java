package com.assetiq.controllers.v1;

import com.assetiq.dto.VendorPerformanceReviewDto;
import com.assetiq.services.VendorPerformanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vendor-reviews")
public class VendorPerformanceController {

    private final VendorPerformanceService reviewService;

    public VendorPerformanceController(VendorPerformanceService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_VENDOR_REVIEWS')")
    public ResponseEntity<VendorPerformanceReviewDto> create(@Valid @RequestBody VendorPerformanceReviewDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_VENDOR_REVIEWS','MANAGE_VENDOR_REVIEWS')")
    public ResponseEntity<VendorPerformanceReviewDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(reviewService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_VENDOR_REVIEWS','MANAGE_VENDOR_REVIEWS')")
    public ResponseEntity<List<VendorPerformanceReviewDto>> list(
            @RequestParam(required = false) UUID supplierId) {
        if (supplierId != null) {
            return ResponseEntity.ok(reviewService.listBySupplier(supplierId));
        }
        return ResponseEntity.ok(reviewService.listAll());
    }

    /** GET /api/v1/vendor-reviews/suppliers/{supplierId}/summary */
    @GetMapping("/suppliers/{supplierId}/summary")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','VIEW_VENDOR_REVIEWS','MANAGE_VENDOR_REVIEWS')")
    public ResponseEntity<Map<String, Object>> supplierSummary(@PathVariable UUID supplierId) {
        return ResponseEntity.ok(reviewService.getSupplierSummary(supplierId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_VENDOR_REVIEWS')")
    public ResponseEntity<VendorPerformanceReviewDto> update(
            @PathVariable UUID id, @Valid @RequestBody VendorPerformanceReviewDto dto) {
        return ResponseEntity.ok(reviewService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_VENDOR_REVIEWS')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        reviewService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
