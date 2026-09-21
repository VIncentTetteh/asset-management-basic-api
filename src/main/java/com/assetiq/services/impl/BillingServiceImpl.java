package com.assetiq.services.impl;

import com.assetiq.dto.*;
import com.assetiq.config.CachingConfig;
import com.assetiq.enums.PaymentStatus;
import com.assetiq.enums.SubscriptionStatus;
import com.assetiq.exceptions.PaymentGatewayException;
import com.assetiq.exceptions.PaymentRejectedException;
import com.assetiq.models.*;
import com.assetiq.repositories.*;
import com.assetiq.security.SecretCryptoService;
import com.assetiq.services.BillingService;
import com.assetiq.services.TenantAwareService;
import com.assetiq.services.UsageLimitService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Transactional
public class BillingServiceImpl extends TenantAwareService implements BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingServiceImpl.class);

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final OrganisationSubscriptionRepository organisationSubscriptionRepository;
    private final BillingPaymentRepository billingPaymentRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final PaystackGatewayService paystackGatewayService;
    private final ObjectMapper objectMapper;
    private final SecretCryptoService secretCryptoService;
    private final DepartmentRepository departmentRepository;
    private final SubscriptionLifecycleService lifecycleService;
    private final UsageLimitService usageLimitService;

    /** Origins a checkout may return to: the web app's own (CORS-allowed) origins. */
    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:}")
    private List<String> allowedCallbackOrigins = List.of();

    @org.springframework.beans.factory.annotation.Value("${app.billing.dunning.grace-days:14}")
    private int graceDays = 14;

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
            ObjectMapper objectMapper,
            SecretCryptoService secretCryptoService,
            DepartmentRepository departmentRepository,
            SubscriptionLifecycleService lifecycleService,
            UsageLimitService usageLimitService) {
        super(organisationRepository);
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.organisationSubscriptionRepository = organisationSubscriptionRepository;
        this.billingPaymentRepository = billingPaymentRepository;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.paystackGatewayService = paystackGatewayService;
        this.objectMapper = objectMapper;
        this.secretCryptoService = secretCryptoService;
        this.departmentRepository = departmentRepository;
        this.lifecycleService = lifecycleService;
        this.usageLimitService = usageLimitService;
    }

    /**
     * Plan codes we keep in the DB for backward compatibility with existing
     * tenant subscriptions, but that should NOT be offered on the public
     * Only these subscription packages may be returned or checked out.
     */
    private static final Set<String> PUBLIC_PLAN_CODES = Set.of(
            "FREEMIUM", "BASIC", "BUSINESS", "BUSINESS_ANNUAL", "ENTERPRISE");

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
        SubscriptionPlan targetPlan = requirePurchasablePlan(request.getPlanCode());
        return startCheckout(org, targetPlan, request.getCallbackUrl());
    }

    private SubscriptionPlan requirePublicPlan(String planCode) {
        return subscriptionPlanRepository.findByCodeAndDeletedAtIsNull(planCode)
                .filter(SubscriptionPlan::getActive)
                .filter(plan -> PUBLIC_PLAN_CODES.contains(plan.getCode()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive plan"));
    }

    /** A public plan that is paid for through checkout (not Freemium, not custom Enterprise). */
    private SubscriptionPlan requirePurchasablePlan(String planCode) {
        SubscriptionPlan targetPlan = requirePublicPlan(planCode);
        if (targetPlan.getTier() == com.assetiq.enums.BillingPlanTier.ENTERPRISE) {
            throw new IllegalArgumentException(
                    "Enterprise pricing is custom — please contact sales for a quote.");
        }
        if (SubscriptionLifecycleService.isFree(targetPlan)) {
            throw new IllegalArgumentException(
                    "Freemium needs no checkout. To move a paid subscription to Freemium, choose " +
                    "Freemium as your plan and it will take effect at the end of the current period.");
        }
        return targetPlan;
    }

    private BillingCheckoutResponse startCheckout(Organisation org, SubscriptionPlan targetPlan,
                                                  String requestedCallbackUrl) {
        String actorEmail = requireActorEmail();
        String reference = generateReference(org);

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", actorEmail);
        payload.put("amount", targetPlan.getAmountMinor());
        payload.put("currency", targetPlan.getCurrency());
        payload.put("reference", reference);
        payload.put("callback_url", resolveCallbackUrl(requestedCallbackUrl));
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
        // Gateway payloads can contain reusable tokens and customer PII. Persist only
        // the normalized fields required for reconciliation, never the raw response.
        payment.setRawGatewayPayload(null);
        billingPaymentRepository.save(payment);

        BillingCheckoutResponse dto = new BillingCheckoutResponse();
        dto.setAuthorizationUrl(response.path("data").path("authorization_url").asText());
        dto.setAccessCode(response.path("data").path("access_code").asText());
        dto.setReference(reference);
        return dto;
    }

    /**
     * The gateway redirects the customer's browser to this URL after payment, so a
     * client-chosen value is an open redirect through a trusted payment page. Accept it
     * only on an origin the web app is served from; otherwise use the configured default.
     */
    String resolveCallbackUrl(String requested) {
        if (requested != null && !requested.isBlank()) {
            String origin = originOf(requested);
            if (origin != null && (origin.equals(originOf(defaultCallbackUrl))
                    || allowedCallbackOrigins.stream().map(String::trim).anyMatch(origin::equalsIgnoreCase))) {
                return requested;
            }
        }
        return defaultCallbackUrl;
    }

    private static String originOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !scheme.equals("http")) {
                return null;
            }
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                    + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    @Transactional(noRollbackFor = PaymentRejectedException.class)
    public OrganisationSubscriptionDto verifyCheckout(String reference) {
        Organisation org = requireTenantOrg();
        BillingPayment payment = billingPaymentRepository.lockByReference(reference)
                .filter(p -> p.getOrganisation() != null && org.getId().equals(p.getOrganisation().getId()))
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

        if (enabled) {
            enableGatewaySubscription(subscription);
            subscription.setCanceledAt(null);
        } else {
            lifecycleService.disableGatewaySubscription(subscription);
            subscription.setCanceledAt(Instant.now());
        }
        subscription.setAutoRenew(enabled);
        organisationSubscriptionRepository.save(subscription);
        return toSubscriptionDto(subscription, org);
    }

    @Override
    public PlanChangeResponse changePlan(BillingCheckoutRequest request) {
        Organisation org = requireTenantOrg();
        OrganisationSubscription subscription = getOrProvisionFreemiumSubscription(org);
        SubscriptionPlan target = requirePublicPlan(request.getPlanCode());
        if (target.getTier() == com.assetiq.enums.BillingPlanTier.ENTERPRISE) {
            throw new IllegalArgumentException(
                    "Enterprise pricing is custom — please contact sales for a quote.");
        }

        switch (lifecycleService.classify(subscription, target)) {
            case SAME -> {
                return new PlanChangeResponse(PlanChangeResponse.Action.NO_CHANGE, null,
                        toSubscriptionDto(subscription, org));
            }
            case UPGRADE -> {
                BillingCheckoutResponse checkout = startCheckout(org, target, request.getCallbackUrl());
                return new PlanChangeResponse(PlanChangeResponse.Action.CHECKOUT, checkout,
                        toSubscriptionDto(subscription, org));
            }
            default -> {
                usageLimitService.assertUsageFitsPlan(org, target);
                // Stop the current plan's recurring charge now; the cheaper plan is paid
                // for (or Freemium applied) when the period ends.
                lifecycleService.disableGatewaySubscription(subscription);
                subscription.setAutoRenew(false);
                subscription.setScheduledPlan(target);
                subscription.setScheduledChangeAt(subscription.getCurrentPeriodEnd());
                organisationSubscriptionRepository.save(subscription);
                return new PlanChangeResponse(PlanChangeResponse.Action.SCHEDULED, null,
                        toSubscriptionDto(subscription, org));
            }
        }
    }

    @Override
    public OrganisationSubscriptionDto cancelScheduledChange() {
        Organisation org = requireTenantOrg();
        OrganisationSubscription subscription = getOrProvisionFreemiumSubscription(org);
        if (subscription.getScheduledPlan() == null) {
            throw new IllegalStateException("There is no scheduled plan change to cancel.");
        }
        subscription.setScheduledPlan(null);
        subscription.setScheduledChangeAt(null);
        enableGatewaySubscription(subscription);
        subscription.setAutoRenew(true);
        subscription.setCanceledAt(null);
        organisationSubscriptionRepository.save(subscription);
        return toSubscriptionDto(subscription, org);
    }

    private void enableGatewaySubscription(OrganisationSubscription subscription) {
        if (subscription.getPaystackSubscriptionCode() != null && subscription.getPaystackEmailToken() != null) {
            paystackGatewayService.enableSubscription(subscription.getPaystackSubscriptionCode(),
                    secretCryptoService.decrypt(subscription.getPaystackEmailToken()));
        }
    }

    @Override
    @Transactional(noRollbackFor = PaymentRejectedException.class)
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
            handleChargeSuccess(event, data);
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
                            if (s.getScheduledPlan() == null) {
                                s.setCanceledAt(Instant.now());
                            }
                            organisationSubscriptionRepository.save(s);
                        });
            }
        }
    }

    private void handleChargeSuccess(JsonNode event, JsonNode data) {
        String reference = data.path("reference").asText(null);
        if (reference == null || reference.isBlank()) {
            return;
        }
        Optional<BillingPayment> checkoutPayment = billingPaymentRepository.lockByReference(reference);
        if (checkoutPayment.isPresent()) {
            BillingPayment payment = checkoutPayment.get();
            if (payment.getStatus() != PaymentStatus.SUCCESS) {
                applyVerifiedPayment(event, payment, payment.getOrganisation());
            }
            return;
        }
        recordRenewalCharge(reference, data);
    }

    /**
     * A recurring charge made by the gateway itself. It carries a gateway-generated
     * reference we have never seen, so it used to be dropped silently: the period never
     * advanced and a customer who paid was later dunned and downgraded. It is matched to
     * the tenant by gateway customer, checked against the plan price, recorded once (the
     * gateway transaction id is unique), and extends the paid period.
     */
    private void recordRenewalCharge(String reference, JsonNode data) {
        String customerCode = data.path("customer").path("customer_code").asText(null);
        if (customerCode == null || customerCode.isBlank()) {
            return;
        }
        OrganisationSubscription subscription = organisationSubscriptionRepository
                .findFirstByPaystackCustomerCodeAndDeletedAtIsNull(customerCode).orElse(null);
        long transactionId = data.path("id").asLong(0L);
        if (subscription == null || subscription.getPlan() == null
                || (transactionId != 0L && billingPaymentRepository.existsByPaystackTransactionId(transactionId))) {
            return;
        }
        SubscriptionPlan plan = subscription.getPlan();
        long paid = data.path("amount").asLong(-1L);
        String currency = data.path("currency").asText("");
        if (paid != plan.getAmountMinor() || !currency.equalsIgnoreCase(plan.getCurrency())) {
            log.warn(
                    "[BILLING] Renewal charge {} for org {} does not match plan {} ({} {} vs {} {}); not applied",
                    reference, subscription.getOrganisation().getId(), plan.getCode(),
                    paid, currency, plan.getAmountMinor(), plan.getCurrency());
            return;
        }

        BillingPayment payment = new BillingPayment();
        payment.setOrganisation(subscription.getOrganisation());
        payment.setPlan(plan);
        payment.setReference(reference);
        payment.setAmountMinor(paid);
        payment.setCurrency(plan.getCurrency());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaystackTransactionId(transactionId == 0L ? null : transactionId);
        payment.setChannel(data.path("channel").asText(null));
        payment.setGatewayResponse(data.path("gateway_response").asText(null));
        payment.setPaidAt(Instant.now());
        payment.setPaystackCustomerCode(customerCode);
        payment.setRawGatewayPayload(null);

        lifecycleService.applyPaidPeriod(subscription, plan);
        OrganisationSubscription saved = organisationSubscriptionRepository.save(subscription);
        payment.setSubscription(saved);
        billingPaymentRepository.save(payment);
    }

    private OrganisationSubscriptionDto applyVerifiedPayment(JsonNode response, BillingPayment payment,
            Organisation org) {
        JsonNode data = response.path("data");
        String gatewayStatus = data.path("status").asText("");
        if (!"success".equalsIgnoreCase(gatewayStatus)) {
            rejectPayment(payment, data.path("gateway_response").asText(null));
            throw new PaymentRejectedException("Payment is not successful");
        }

        long paidAmount = data.path("amount").asLong();
        String paidCurrency = data.path("currency").asText("");
        if (paidAmount != payment.getAmountMinor()
                || (!paidCurrency.isBlank() && !paidCurrency.equalsIgnoreCase(payment.getCurrency()))) {
            rejectPayment(payment, "Amount or currency mismatch");
            throw new PaymentRejectedException("Payment validation failed");
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaystackTransactionId(data.path("id").asLong());
        payment.setChannel(data.path("channel").asText(null));
        payment.setGatewayResponse(data.path("gateway_response").asText(null));
        payment.setPaidAt(Instant.now());
        payment.setRawGatewayPayload(null);
        payment.setPaystackAuthorizationCode(secretCryptoService.encrypt(
                data.path("authorization").path("authorization_code").asText(null)));
        payment.setPaystackCustomerCode(data.path("customer").path("customer_code").asText(null));
        payment.setPaystackSubscriptionCode(data.path("subscription").path("subscription_code").asText(null));
        payment.setPaystackEmailToken(secretCryptoService.encrypt(
                data.path("subscription").path("email_token").asText(null)));
        billingPaymentRepository.save(payment);

        OrganisationSubscription subscription = getOrProvisionFreemiumSubscription(org);
        String newSubscriptionCode = payment.getPaystackSubscriptionCode();
        if (newSubscriptionCode != null && !newSubscriptionCode.isBlank()
                && !newSubscriptionCode.equals(subscription.getPaystackSubscriptionCode())) {
            // A plan change creates a new gateway subscription. The old one would keep
            // charging the old price on its own schedule — the customer would be billed
            // twice — so it is stopped before its code is overwritten.
            lifecycleService.disableGatewaySubscription(subscription);
        }
        lifecycleService.applyPaidPeriod(subscription, payment.getPlan());
        if (payment.getPaystackCustomerCode() != null && !payment.getPaystackCustomerCode().isBlank()) {
            subscription.setPaystackCustomerCode(payment.getPaystackCustomerCode());
        }
        if (newSubscriptionCode != null && !newSubscriptionCode.isBlank()) {
            subscription.setPaystackSubscriptionCode(newSubscriptionCode);
        }
        if (payment.getPaystackEmailToken() != null && !payment.getPaystackEmailToken().isBlank()) {
            subscription.setPaystackEmailToken(payment.getPaystackEmailToken());
        }

        OrganisationSubscription saved = organisationSubscriptionRepository.save(subscription);
        payment.setSubscription(saved);
        billingPaymentRepository.save(payment);
        return toSubscriptionDto(saved, org);
    }

    private void rejectPayment(BillingPayment payment, String reason) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setGatewayResponse(reason);
        payment.setRawGatewayPayload(null);
        billingPaymentRepository.save(payment);
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
        dto.setCanceledAt(subscription.getCanceledAt());
        dto.setPastDueSince(subscription.getPastDueSince());
        if (subscription.getStatus() == SubscriptionStatus.PAST_DUE && subscription.getPastDueSince() != null) {
            dto.setGraceEndsAt(subscription.getPastDueSince().plus(Duration.ofDays(graceDays)));
        }
        if (subscription.getScheduledPlan() != null) {
            dto.setScheduledPlan(toPlanDto(subscription.getScheduledPlan()));
            dto.setScheduledChangeAt(subscription.getScheduledChangeAt());
        }
        dto.setCurrentAssetCount(assetRepository.countByOrganisationAndDeletedAtIsNull(org));
        dto.setCurrentEmployeeCount(userRepository.countByOrganisationAndDeletedAtIsNull(org));
        dto.setCurrentDepartmentCount(departmentRepository.countByOrganisationAndDeletedAtIsNull(org));
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
