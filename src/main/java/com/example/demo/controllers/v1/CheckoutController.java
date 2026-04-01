package com.example.demo.controllers.v1;

import com.example.demo.dto.CheckoutRecordDto;
import com.example.demo.services.CheckoutService;
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
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<CheckoutRecordDto> checkOut(
            @PathVariable UUID assetId,
            @PathVariable UUID userId,
            @RequestBody(required = false) CheckoutRecordDto dto) {
        return ResponseEntity.ok(checkoutService.checkOut(assetId, userId, dto));
    }

    /** Check in (return) a previously checked-out asset. */
    @PostMapping("/{checkoutRecordId}/checkin")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<CheckoutRecordDto> checkIn(
            @PathVariable UUID checkoutRecordId,
            @RequestBody(required = false) CheckoutRecordDto dto) {
        return ResponseEntity.ok(checkoutService.checkIn(checkoutRecordId, dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<CheckoutRecordDto> getById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(checkoutService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<List<CheckoutRecordDto>> listByOrg() {
        return ResponseEntity.ok(checkoutService.listByOrg());
    }

    @GetMapping("/assets/{assetId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<List<CheckoutRecordDto>> listByAsset(@PathVariable UUID assetId) {
        return ResponseEntity.ok(checkoutService.listByAsset(assetId));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<List<CheckoutRecordDto>> listByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(checkoutService.listByUser(userId));
    }

    /** Returns all ACTIVE checkout records whose expected return date is in the past. */
    @GetMapping("/overdue")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<List<CheckoutRecordDto>> listOverdue() {
        return ResponseEntity.ok(checkoutService.listOverdue());
    }
}
