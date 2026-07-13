package com.assetiq.controllers.v1;

import com.assetiq.dto.CheckoutRecordDto;
import com.assetiq.services.CheckoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/checkouts")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    /** Check out an asset to a user. */
    @PostMapping("/assets/{assetId}/users/{userId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','CHECKOUT_ASSET','VIEW_ASSETS')")
    public ResponseEntity<CheckoutRecordDto> checkOut(
            @PathVariable UUID assetId,
            @PathVariable UUID userId,
            @RequestBody(required = false) CheckoutRecordDto dto) {
        return ResponseEntity.ok(checkoutService.checkOut(assetId, userId, dto));
    }

    /** Check out an asset to an employee (who may not be a system user). */
    @PostMapping("/assets/{assetId}/employees/{employeeId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CHECKOUT_ASSET','MANAGE_EMPLOYEES')")
    public ResponseEntity<CheckoutRecordDto> checkOutToEmployee(
            @PathVariable UUID assetId,
            @PathVariable UUID employeeId,
            @RequestBody(required = false) CheckoutRecordDto dto) {
        return ResponseEntity.ok(checkoutService.checkOutToEmployee(assetId, employeeId, dto));
    }

    /** All checkout records for an employee, newest first. */
    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CHECKOUT_ASSET','VIEW_EMPLOYEES','MANAGE_EMPLOYEES')")
    public ResponseEntity<List<CheckoutRecordDto>> listByEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(checkoutService.listByEmployee(employeeId));
    }

    /** Check in (return) a previously checked-out asset. */
    @PostMapping("/{checkoutRecordId}/checkin")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','CHECKOUT_ASSET','VIEW_ASSETS')")
    public ResponseEntity<CheckoutRecordDto> checkIn(
            @PathVariable UUID checkoutRecordId,
            @RequestBody(required = false) CheckoutRecordDto dto) {
        return ResponseEntity.ok(checkoutService.checkIn(checkoutRecordId, dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','CHECKOUT_ASSET','VIEW_ASSETS')")
    public ResponseEntity<CheckoutRecordDto> getById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(checkoutService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','CHECKOUT_ASSET','VIEW_ASSETS')")
    public ResponseEntity<List<CheckoutRecordDto>> listByOrg() {
        return ResponseEntity.ok(checkoutService.listByOrg());
    }

    @GetMapping("/assets/{assetId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','CHECKOUT_ASSET','VIEW_ASSETS')")
    public ResponseEntity<List<CheckoutRecordDto>> listByAsset(@PathVariable UUID assetId) {
        return ResponseEntity.ok(checkoutService.listByAsset(assetId));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','CHECKOUT_ASSET','VIEW_ASSETS')")
    public ResponseEntity<List<CheckoutRecordDto>> listByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(checkoutService.listByUser(userId));
    }

    /** Returns all ACTIVE checkout records whose expected return date is in the past. */
    @GetMapping("/overdue")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CHECKOUT_ASSET')")
    public ResponseEntity<List<CheckoutRecordDto>> listOverdue() {
        return ResponseEntity.ok(checkoutService.listOverdue());
    }
}
