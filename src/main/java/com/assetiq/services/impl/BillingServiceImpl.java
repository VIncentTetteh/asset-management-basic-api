package com.assetiq.services.impl;

import com.assetiq.dto.*;
import com.assetiq.config.CachingConfig;
import com.assetiq.enums.PaymentStatus;
import com.assetiq.enums.SubscriptionStatus;
import com.assetiq.exceptions.PaymentGatewayException;
import com.assetiq.models.*;
import com.assetiq.repositories.*;
import com.assetiq.services.BillingService;
import com.assetiq.services.TenantAwareService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Transactional
public class BillingServiceImpl extends TenantAwareService implements BillingService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final OrganisationSubscriptionRepository organisationSubscriptionRepository;
    private final BillingPaymentRepository billingPaymentRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final PaystackGatewayService paystackGatewayService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${paystack.secret.key:}")
    private String paystackSecretKey;

    @org.springframework.beans.factory.annotation.Value("${app.billing.callback-url:}")
    private String defaultCallbackUrl;

    /**
     * P1-6: Comma-separated list of Paystack payment channels to enable for
     * this deployment. Ghana defaults include {@code mobile_money} so MTN,
     * Telecel, and AirtelTigo wallets appear on the hosted checkout.
     * Legacy fallback is {@code card,bank} so non-Ghana regions still work.
     */
    @org.springframework.beans.factory.annotation.Value("${app.billing.paystack.channels:card,mobile_money,bank,ussd}")
    private String paystackChannelsCsv;

    public BillingServiceImpl(
            OrganisationRepository organisationRepository,
            SubscriptionPlanRepository subscriptionPlanRepository,
            OrganisationSubscriptionRepository organisationSubscriptionRepository,
            BillingPaymentRepository billingPaymentRepository,
            UserRepository userRepository,
            AssetRepository assetRepository,
            PaystackGatewayService paystackGatewayService,
            ObjectMapper objectMapper) {
        super(organisationRepository);
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.organisationSubscriptionRepository = organisationSubscriptionRepository;
        this.billingPaymentRepository = billingPaymentRepository;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.paystackGatewayService = paystackGatewayService;
        this.objectMapper = objectMapper;
    }

    /**
     * Plan codes we keep in the DB for backward compatibility with existing
     * tenant subscriptions, but that should NOT be offered on the public
     * Only these subscription packages may be returned or checked out.
     */
    private static final Set<String> PUBLIC_PLAN_CODES = Set.of(
            "FREEMIUM", "BASIC", "BUSINESS", "ENTERPRISE");

    /**
     * Display order for the pricing surface. ENTERPRISE sorts last even though
     * its {@code amountMinor} is 0 (custom-quote plans render a "Contact sales"
     * CTA). All other tiers follow the ladder; any future tier that isn't on
     * this map falls to the end.
     */
    private static final Map<com.assetiq.enums.BillingPlanTier, Integer> TIER_DISPLAY_ORDER = Map.of(
            com.assetiq.enums.BillingPlanTier.FREEMIUM, 0,
            com.assetiq.enums.BillingPlanTier.BASIC, 1,
            com.assetiq.enums.BillingPlanTier.BUSINESS, 2,
            com.assetiq.enums.BillingPlanTier.ENTERPRISE, 3);

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CachingConfig.CacheNames.BILLING_PLANS, key = "'active-public'")
    public List<SubscriptionPlanDto> listPlans() {
        return subscriptionPlanRepository.findByActiveIsTrueAndDeletedAtIsNullOrderByAmountMinorAsc().stream()
                .filter(p -> PUBLIC_PLAN_CODES.contains(p.getCode()))
                .sorted(Comparator
                        .comparingInt((SubscriptionPlan p) ->
                                TIER_DISPLAY_ORDER.getOrDefault(p.getTier(), Integer.MAX_VALUE))
                        .thenComparingLong(p -> p.getAmountMinor() == null ? 0L : p.getAmountMinor()))
                .map(this::toPlanDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrganisationSubscriptionDto getCurrentSubscription() {
        Organisation org = requireTenantOrg();
        OrganisationSubscription subscription = getOrProvisionFreemiumSubscription(org);
        return toSubscriptionDto(subscription, org);
    }

    @Override
    public BillingCheckoutResponse initializeCheckout(BillingCheckoutRequest request) {
        Organisation org = requireTenantOrg();
        String actorEmail = requireActorEmail();
        SubscriptionPlan targetPlan = subscriptionPlanRepository.findByCodeAndDeletedAtIsNull(request.getPlanCode())
                .filter(SubscriptionPlan::getActive)
                .filter(plan -> PUBLIC_PLAN_CODES.contains(plan.getCode()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive plan"));

        // Reject checkout on zero-amount plans with a message the portal can
        // render verbatim.
        if (targetPlan.getTier() == com.assetiq.enums.BillingPlanTier.FREEMIUM
                || targetPlan.getAmountMinor() == null || targetPlan.getAmountMinor() <= 0) {
            if (targetPlan.getTier() == com.assetiq.enums.BillingPlanTier.ENTERPRISE) {
                throw new IllegalArgumentException(
                        "Enterprise pricing is custom — please contact sales for a quote.");
            }
            throw new IllegalArgumentException(
                    "Freemium activates automatically on sign-up — no checkout needed. " +
                    "To move an existing paid subscription back to Freemium, turn off " +
                    "auto-renew and your plan will downgrade at the end of the current period.");
        }

        String reference = generateReference(org);
        String callbackUrl = request.getCallbackUrl();
        if (callbackUrl == null || callbackUrl.isBlank()) {
            callbackUrl = defaultCallbackUrl;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", actorEmail);
        payload.put("amount", targetPlan.getAmountMinor());
        payload.put("currency", targetPlan.getCurrency());
        payload.put("reference", reference);
        payload.put("callback_url", callbackUrl);
        payload.put("metadata", Map.of(
                "organisationId", org.getId().toString(),
                "planCode", targetPlan.getCode()));
        if (targetPlan.getPaystackPlanCode() != null && !targetPlan.getPaystackPlanCode().isBlank()) {
            payload.put("plan", targetPlan.getPaystackPlanCode());
        }
        // P1-6: Attach configured payment channels so Paystack surfaces
        // MoMo (MTN / Telecel / AirtelTigo), USSD, card, and bank transfer
        // on the hosted checkout page.
        List<String> channels = resolvePaystackChannels();
        if (!channels.isEmpty()) {
            payload.put("channels", channels);
        }

        JsonNode response = paystackGatewayService.initializeTransaction(payload);
        if (!response.path("status").asBoolean(false)) {
            String paystackMessage = response.path("message").asText("").trim();
            if (paystackMessage.isBlank()) {
                throw new PaymentGatewayException("Payment gateway error: unable to initialize payment");
            }
            throw new PaymentGatewayException("Payment gateway error: " + paystackMessage);
        }

        BillingPayment payment = new BillingPayment();
        payment.setOrganisation(org);
        payment.setPlan(targetPlan);
        payment.setReference(reference);
        payment.setAmountMinor(targetPlan.getAmountMinor());
        payment.setCurrency(targetPlan.getCurrency());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setRawGatewayPayload(response.toString());
        billingPaymentRepository.save(payment);

        BillingCheckoutResponse dto = new BillingCheckoutResponse();
        dto.setAuthorizationUrl(response.path("data").path("authorization_url").asText());
        dto.setAccessCode(response.path("data").path("access_code").asText());
        dto.setReference(reference);
        return dto;
    }

    @Override
    public OrganisationSubscriptionDto verifyCheckout(String reference) {
        Organisation org = requireTenantOrg();
        BillingPayment payment = billingPaymentRepository
                .findByReferenceAndOrganisationAndDeletedAtIsNull(reference, org)
                .orElseThrow(() -> new IllegalArgumentException("Payment reference not found"));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            if (payment.getSubscription() != null) {
                return toSubscriptionDto(payment.getSubscription(), org);
            }
            return toSubscriptionDto(getOrProvisionFreemiumSubscription(org), org);
        }

        JsonNode response = paystackGatewayService.verifyTransaction(reference);
        return applyVerifiedPayment(response, payment, org);
    }

    @Override
    public OrganisationSubscriptionDto setAutoRenew(boolean enabled) {
        Organisation org = requireTenantOrg();
        OrganisationSubscription subscription = getOrProvisionFreemiumSubscription(org);

        if (subscription.getPaystackSubscriptionCode() != null && subscription.getPaystackEmailToken() != null) {
            if (enabled) {
                paystackGatewayService.enableSubscription(subscription.getPaystackSubscriptionCode(),
                        subscription.getPaystackEmailToken());
            } else {
                paystackGatewayService.disableSubscription(subscription.getPaystackSubscriptionCode(),
                        subscription.getPaystackEmailToken());
            }
        }

        subscription.setAutoRenew(enabled);
        if (!enabled) {
            subscription.setCanceledAt(Instant.now());
        } else {
            subscription.setCanceledAt(null);
        }
        organisationSubscriptionRepository.save(subscription);
        return toSubscriptionDto(subscription, org);
    }

    @Override
    public void handlePaystackWebhook(String signature, String payload) {
        verifyWebhookSignature(signature, payload);

        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid webhook payload");
        }

        String eventType = event.path("event").asText();
        JsonNode data = event.path("data");
        if ("charge.success".equals(eventType)) {
            String reference = data.path("reference").asText(null);
            if (reference == null || reference.isBlank()) {
                return;
            }
            BillingPayment payment = billingPaymentRepository.findByReferenceAndDeletedAtIsNull(reference).orElse(null);
            if (payment == null || payment.getStatus() == PaymentStatus.SUCCESS) {
                return;
            }
            applyVerifiedPayment(event, payment, payment.getOrganisation());
        } else if ("invoice.payment_failed".equals(eventType)) {
            String subscriptionCode = data.path("subscription").path("subscription_code").asText(null);
            if (subscriptionCode != null) {
                organisationSubscriptionRepository.findByPaystackSubscriptionCodeAndDeletedAtIsNull(subscriptionCode)
                        .ifPresent(s -> {
                            s.setStatus(SubscriptionStatus.PAST_DUE);
                            // Start the dunning clock on the first failure only —
                            // Paystack retries an invoice several times, and each retry
                            // fires this webhook again. Overwriting the timestamp would
                            // restart the grace period on every retry and the account
                            // would never actually reach downgrade.
                            if (s.getPastDueSince() == null) {
                                s.setPastDueSince(Instant.now());
                            }
                            organisationSubscriptionRepository.save(s);
                        });
            }
        } else if ("subscription.disable".equals(eventType)) {
            String subscriptionCode = data.path("subscription_code").asText(null);
            if (subscriptionCode != null) {
                organisationSubscriptionRepository.findByPaystackSubscriptionCodeAndDeletedAtIsNull(subscriptionCode)
                        .ifPresent(s -> {
                            s.setAutoRenew(false);
                            s.setCanceledAt(Instant.now());
                            organisationSubscriptionRepository.save(s);
                        });
            }
        }
    }

    private OrganisationSubscriptionDto applyVerifiedPayment(JsonNode response, BillingPayment payment,
            Organisation org) {
        JsonNode data = response.path("data");
        String gatewayStatus = data.path("status").asText("");
        if (!"success".equalsIgnoreCase(gatewayStatus)) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setGatewayResponse(data.path("gateway_response").asText(null));
            payment.setRawGatewayPayload(response.toString());
            billingPaymentRepository.save(payment);
            throw new IllegalStateException("Payment is not successful");
        }

        long paidAmount = data.path("amount").asLong();
        if (paidAmount != payment.getAmountMinor()) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setGatewayResponse("Amount mismatch");
            payment.setRawGatewayPayload(response.toString());
            billingPaymentRepository.save(payment);
            throw new IllegalStateException("Payment validation failed");
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaystackTransactionId(data.path("id").asLong());
        payment.setChannel(data.path("channel").asText(null));
        payment.setGatewayResponse(data.path("gateway_response").asText(null));
        payment.setPaidAt(Instant.now());
        payment.setRawGatewayPayload(response.toString());
        payment.setPaystackAuthorizationCode(data.path("authorization").path("authorization_code").asText(null));
        payment.setPaystackCustomerCode(data.path("customer").path("customer_code").asText(null));
        payment.setPaystackSubscriptionCode(data.path("subscription").path("subscription_code").asText(null));
        payment.setPaystackEmailToken(data.path("subscription").path("email_token").asText(null));
        billingPaymentRepository.save(payment);

        OrganisationSubscription subscription = getOrProvisionFreemiumSubscription(org);
        subscription.setPlan(payment.getPlan());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        // A successful payment ends any dunning sequence in progress: stop the clock so
        // a later failure starts a fresh grace period rather than resuming the old one.
        subscription.setPastDueSince(null);
        subscription.setCurrentPeriodStart(Instant.now());
        subscription.setCurrentPeriodEnd(calculatePeriodEnd(Instant.now(), payment.getPlan()));
        subscription.setNextBillingAt(subscription.getCurrentPeriodEnd());
        subscription.setAutoRenew(true);
        if (payment.getPaystackCustomerCode() != null && !payment.getPaystackCustomerCode().isBlank()) {
            subscription.setPaystackCustomerCode(payment.getPaystackCustomerCode());
        }
        if (payment.getPaystackSubscriptionCode() != null && !payment.getPaystackSubscriptionCode().isBlank()) {
            subscription.setPaystackSubscriptionCode(payment.getPaystackSubscriptionCode());
        }
        if (payment.getPaystackEmailToken() != null && !payment.getPaystackEmailToken().isBlank()) {
            subscription.setPaystackEmailToken(payment.getPaystackEmailToken());
        }

        OrganisationSubscription saved = organisationSubscriptionRepository.save(subscription);
        payment.setSubscription(saved);
        billingPaymentRepository.save(payment);
        return toSubscriptionDto(saved, org);
    }

    private OrganisationSubscription getOrProvisionFreemiumSubscription(Organisation org) {
        return organisationSubscriptionRepository.findFirstByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(org)
                .orElseGet(() -> {
                    SubscriptionPlan freemium = subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("FREEMIUM")
                            .orElseThrow(() -> new IllegalStateException("FREEMIUM plan is not configured"));
                    OrganisationSubscription s = new OrganisationSubscription();
                    s.setOrganisation(org);
                    s.setPlan(freemium);
                    s.setStatus(SubscriptionStatus.ACTIVE);
                    s.setAutoRenew(false);
                    s.setCurrentPeriodStart(Instant.now());
                    s.setCurrentPeriodEnd(Instant.now().plus(365, ChronoUnit.DAYS));
                    s.setNextBillingAt(null);
                    return organisationSubscriptionRepository.save(s);
                });
    }

    private Instant calculatePeriodEnd(Instant start, SubscriptionPlan plan) {
        if (plan.getInterval() == com.assetiq.enums.BillingInterval.ANNUALLY) {
            return start.plus(365, ChronoUnit.DAYS);
        }
        return start.plus(30, ChronoUnit.DAYS);
    }

    private void verifyWebhookSignature(String signature, String payload) {
        if (paystackSecretKey == null || paystackSecretKey.isBlank()) {
            throw new IllegalStateException("Paystack secret key is not configured");
        }
        if (signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("Missing Paystack signature");
        }
        String computed = hmacSha512(payload, paystackSecretKey);
        // Use constant-time comparison to prevent timing attacks
        if (!MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid Paystack signature");
        }
    }

    private String hmacSha512(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to validate webhook signature", e);
        }
    }

    private String requireActorEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("Authenticated user is required for billing checkout");
        }
        return auth.getName();
    }

    private String generateReference(Organisation org) {
        String orgPart = org.getId().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "BILL_" + orgPart + "_" + Instant.now().toEpochMilli();
    }

    private SubscriptionPlanDto toPlanDto(SubscriptionPlan plan) {
        SubscriptionPlanDto dto = new SubscriptionPlanDto();
        dto.setCode(plan.getCode());
        dto.setName(plan.getName());
        dto.setTier(plan.getTier());
        dto.setInterval(plan.getInterval());
        dto.setAmountMinor(plan.getAmountMinor());
        dto.setCurrency(plan.getCurrency());
        dto.setMaxAssets(plan.getMaxAssets());
        dto.setMaxEmployees(plan.getMaxEmployees());
        dto.setAnalyticsEnabled(plan.getAnalyticsEnabled());
        dto.setAuditRetentionDays(plan.getAuditRetentionDays());
        dto.setDiscountPercent(plan.getDiscountPercent());
        return dto;
    }

    private OrganisationSubscriptionDto toSubscriptionDto(OrganisationSubscription subscription, Organisation org) {
        OrganisationSubscriptionDto dto = new OrganisationSubscriptionDto();
        dto.setId(subscription.getId());
        dto.setOrganisationId(org.getId());
        dto.setPlan(toPlanDto(subscription.getPlan()));
        dto.setStatus(subscription.getStatus());
        dto.setAutoRenew(subscription.getAutoRenew());
        dto.setCurrentPeriodStart(subscription.getCurrentPeriodStart());
        dto.setCurrentPeriodEnd(subscription.getCurrentPeriodEnd());
        dto.setNextBillingAt(subscription.getNextBillingAt());
        dto.setCurrentAssetCount(assetRepository.countByOrganisationAndDeletedAtIsNull(org));
        dto.setCurrentEmployeeCount(userRepository.countByOrganisationAndDeletedAtIsNull(org));
        return dto;
    }

    /**
     * Parse the {@code app.billing.paystack.channels} CSV into a clean list of
     * lower-cased, non-blank channel names. Unknown tokens pass through — we
     * defer to Paystack's own validation rather than maintaining a blocklist
     * that goes stale the moment Paystack launches a new channel.
     */
    private List<String> resolvePaystackChannels() {
        if (paystackChannelsCsv == null || paystackChannelsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(paystackChannelsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
    }
}
