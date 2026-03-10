package com.example.demo.controllers.v1;

import com.example.demo.dto.*;
import com.example.demo.services.BillingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/plans")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<List<SubscriptionPlanDto>> listPlans() {
        return ResponseEntity.ok(billingService.listPlans());
    }

    @GetMapping("/subscription")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<OrganisationSubscriptionDto> getCurrentSubscription() {
        return ResponseEntity.ok(billingService.getCurrentSubscription());
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<BillingCheckoutResponse> initializeCheckout(@Valid @RequestBody BillingCheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.initializeCheckout(request));
    }

    @PostMapping("/checkout/verify")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<OrganisationSubscriptionDto> verifyCheckout(@RequestParam String reference) {
        return ResponseEntity.ok(billingService.verifyCheckout(reference));
    }

    @PatchMapping("/subscription/auto-renew")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<OrganisationSubscriptionDto> setAutoRenew(@Valid @RequestBody AutoRenewRequest request) {
        return ResponseEntity.ok(billingService.setAutoRenew(request.getEnabled()));
    }

    @PostMapping("/webhooks/paystack")
    public ResponseEntity<Void> paystackWebhook(
            @RequestHeader(value = "x-paystack-signature", required = false) String signature,
            @RequestBody String payload) {
        try {
            billingService.handlePaystackWebhook(signature, payload);
        } catch (IllegalArgumentException e) {
            // Signature missing or invalid — log and reject so Paystack doesn't retry a bad request.
            log.warn("[WEBHOOK] Rejected Paystack webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            // Processing error — log it but return 200 so Paystack does not flood us with retries.
            // The handler is idempotent; Paystack will re-deliver if needed.
            log.error("[WEBHOOK] Error processing Paystack webhook", e);
        }
        return ResponseEntity.ok().build();
    }
}

