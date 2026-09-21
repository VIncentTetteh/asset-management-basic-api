package com.assetiq.controllers.v1;

import com.assetiq.dto.*;
import com.assetiq.security.WebhookReplayProtector;
import com.assetiq.security.WebhookSignatureValidator;
import com.assetiq.services.BillingService;
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
    private final WebhookSignatureValidator webhookSignatureValidator;
    private final WebhookReplayProtector webhookReplayProtector;

    public BillingController(BillingService billingService,
                             WebhookSignatureValidator webhookSignatureValidator,
                             WebhookReplayProtector webhookReplayProtector) {
        this.billingService             = billingService;
        this.webhookSignatureValidator  = webhookSignatureValidator;
        this.webhookReplayProtector     = webhookReplayProtector;
    }

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanDto>> listPlans() {
        return ResponseEntity.ok(billingService.listPlans());
    }

    @GetMapping("/subscription")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','VIEW_REPORTS')")
    public ResponseEntity<OrganisationSubscriptionDto> getCurrentSubscription() {
        return ResponseEntity.ok(billingService.getCurrentSubscription());
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<BillingCheckoutResponse> initializeCheckout(@Valid @RequestBody BillingCheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.initializeCheckout(request));
    }

    @PostMapping("/checkout/verify")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<OrganisationSubscriptionDto> verifyCheckout(@RequestParam String reference) {
        return ResponseEntity.ok(billingService.verifyCheckout(reference));
    }

    @PatchMapping("/subscription/auto-renew")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<OrganisationSubscriptionDto> setAutoRenew(@Valid @RequestBody AutoRenewRequest request) {
        return ResponseEntity.ok(billingService.setAutoRenew(request.getEnabled()));
    }

    @PostMapping("/subscription/change-plan")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<PlanChangeResponse> changePlan(@Valid @RequestBody BillingCheckoutRequest request) {
        return ResponseEntity.ok(billingService.changePlan(request));
    }

    @DeleteMapping("/subscription/scheduled-change")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<OrganisationSubscriptionDto> cancelScheduledChange() {
        return ResponseEntity.ok(billingService.cancelScheduledChange());
    }

    @PostMapping("/webhooks/paystack")
    public ResponseEntity<Void> paystackWebhook(
            @RequestHeader(value = "x-paystack-signature", required = false) String signature,
            @RequestBody String payload) {
        
        // Step 1: Validate webhook signature FIRST
        if (signature == null || signature.isBlank()) {
            log.warn("[WEBHOOK] Rejected Paystack webhook: missing X-Paystack-Signature header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        if (!webhookSignatureValidator.isValidPaystackSignature(payload, signature)) {
            log.warn("[WEBHOOK] Rejected Paystack webhook: invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // Step 3: Replay protection — reject already-seen event IDs
        if (webhookReplayProtector.isReplay(payload)) {
            log.info("[WEBHOOK] Duplicate event ignored (replay protection)");
            return ResponseEntity.ok().build();  // 200 so Paystack stops retrying
        }

        // Step 2: Signature is valid, process webhook
        try {
            billingService.handlePaystackWebhook(signature, payload);
            log.info("[WEBHOOK] Paystack webhook processed successfully");
        } catch (IllegalArgumentException e) {
            log.warn("[WEBHOOK] Webhook validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            // A processing failure must reach the gateway as a failure. Returning 200 here
            // (as this used to) told Paystack the event was handled, and since replay
            // protection had already marked it seen, a paid renewal was lost for good.
            // Release the event so the gateway's retry is processed, and let it retry.
            webhookReplayProtector.release(payload);
            log.error("[WEBHOOK] Error processing Paystack webhook; released for retry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok().build();
    }
}
