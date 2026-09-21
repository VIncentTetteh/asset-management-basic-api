package com.assetiq.services.impl;

import com.assetiq.dto.BillingCheckoutRequest;
import com.assetiq.dto.BillingCheckoutResponse;
import com.assetiq.dto.PlanChangeResponse;
import com.assetiq.enums.BillingInterval;
import com.assetiq.enums.BillingPlanTier;
import com.assetiq.enums.PaymentStatus;
import com.assetiq.enums.SubscriptionStatus;
import com.assetiq.models.BillingPayment;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrganisationSubscription;
import com.assetiq.models.SubscriptionPlan;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.exceptions.PaymentRejectedException;
import com.assetiq.repositories.BillingPaymentRepository;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.OrganisationSubscriptionRepository;
import com.assetiq.repositories.SubscriptionPlanRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.security.SecretCryptoService;
import com.assetiq.services.NotificationService;
import com.assetiq.services.UsageLimitService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the money-touching checkout path in {@link BillingServiceImpl}.
 *
 * Guards two behaviours that would be expensive to get wrong in front of a
 * paying customer:
 *  1. The Paystack init payload advertises the configured payment channels —
 *     specifically mobile_money (the P1-6 Ghana launch-blocker fix). A silent
 *     regression here means Ghanaian customers cannot pay with MTN / Telecel /
 *     AirtelTigo Money.
 *  2. Zero-amount plans (Freemium, Enterprise custom-quote) never reach the
 *     gateway and surface a clear, portal-renderable message instead.
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceImplTest {

    @Mock private OrganisationRepository organisationRepository;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock private OrganisationSubscriptionRepository organisationSubscriptionRepository;
    @Mock private BillingPaymentRepository billingPaymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private PaystackGatewayService paystackGatewayService;
    @Mock private SecretCryptoService secretCryptoService;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private UsageLimitService usageLimitService;
    @Mock private NotificationService notificationService;

    private static final String WEBHOOK_SECRET = "sk_test_webhook_fixture";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BillingServiceImpl billingService;
    private Organisation org;

    @BeforeEach
    void setUp() {
        billingService = new BillingServiceImpl(
                organisationRepository,
                subscriptionPlanRepository,
                organisationSubscriptionRepository,
                billingPaymentRepository,
                userRepository,
                assetRepository,
                paystackGatewayService,
                objectMapper,
                secretCryptoService,
                departmentRepository,
                new SubscriptionLifecycleService(organisationSubscriptionRepository, subscriptionPlanRepository,
                        paystackGatewayService, secretCryptoService, notificationService),
                usageLimitService);
        ReflectionTestUtils.setField(billingService, "paystackChannelsCsv", "card,mobile_money,bank,ussd");
        ReflectionTestUtils.setField(billingService, "defaultCallbackUrl", "https://portal.assetiq.io/callback");
        ReflectionTestUtils.setField(billingService, "allowedCallbackOrigins", List.of("https://app.assetiq.io"));
        ReflectionTestUtils.setField(billingService, "paystackSecretKey", WEBHOOK_SECRET);
        lenient().when(secretCryptoService.encrypt(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(secretCryptoService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));

        org = new Organisation();
        org.setName("Kwabenya Depot Ltd");

        TenantContext.setOrganisationId(org.getId());
        lenient().when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId()))
                .thenReturn(Optional.of(org));

        var auth = new UsernamePasswordAuthenticationToken(
                "ama.boateng@kwabenya.com.gh", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private SubscriptionPlan plan(String code, BillingPlanTier tier, Long amountMinor, String currency) {
        SubscriptionPlan p = new SubscriptionPlan();
        p.setCode(code);
        p.setName(code + " plan");
        p.setTier(tier);
        p.setAmountMinor(amountMinor);
        p.setCurrency(currency);
        p.setActive(true);
        p.setId(UUID.randomUUID());
        p.setInterval(BillingInterval.MONTHLY);
        p.setMaxAssets(1_000);
        p.setMaxEmployees(100);
        return p;
    }

    @Test
    void initializeCheckout_attachesMobileMoneyChannelAndAmount() throws Exception {
        SubscriptionPlan basic = plan("BASIC", BillingPlanTier.BASIC, 79_900L, "GHS");
        when(subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("BASIC"))
                .thenReturn(Optional.of(basic));

        JsonNode success = objectMapper.readTree(
                "{\"status\":true,\"data\":{\"authorization_url\":\"https://checkout.paystack.com/xyz\","
                        + "\"access_code\":\"acc_xyz\",\"reference\":\"ref_xyz\"}}");
        when(paystackGatewayService.initializeTransaction(any())).thenReturn(success);
        lenient().when(billingPaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BillingCheckoutRequest request = new BillingCheckoutRequest();
        request.setPlanCode("BASIC");
        request.setCallbackUrl("https://portal.assetiq.io/done");

        BillingCheckoutResponse response = billingService.initializeCheckout(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(paystackGatewayService).initializeTransaction(payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();

        assertEquals(List.of("card", "mobile_money", "bank", "ussd"), payload.get("channels"),
                "Paystack payload must advertise mobile_money so Ghanaian customers can pay with MoMo");
        assertEquals(79_900L, payload.get("amount"));
        assertEquals("GHS", payload.get("currency"));
        assertEquals("https://checkout.paystack.com/xyz", response.getAuthorizationUrl());
    }

    @Test
    void initializeCheckout_rejectsFreemiumWithoutHittingGateway() {
        SubscriptionPlan freemium = plan("FREEMIUM", BillingPlanTier.FREEMIUM, 0L, "GHS");
        when(subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("FREEMIUM"))
                .thenReturn(Optional.of(freemium));

        BillingCheckoutRequest request = new BillingCheckoutRequest();
        request.setPlanCode("FREEMIUM");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> billingService.initializeCheckout(request));
        assertTrue(ex.getMessage().toLowerCase().contains("freemium"));
        verify(paystackGatewayService, never()).initializeTransaction(any());
    }

    @Test
    void initializeCheckout_rejectsEnterpriseWithContactSalesMessage() {
        SubscriptionPlan enterprise = plan("ENTERPRISE", BillingPlanTier.ENTERPRISE, 0L, "USD");
        when(subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("ENTERPRISE"))
                .thenReturn(Optional.of(enterprise));

        BillingCheckoutRequest request = new BillingCheckoutRequest();
        request.setPlanCode("ENTERPRISE");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> billingService.initializeCheckout(request));
        assertTrue(ex.getMessage().toLowerCase().contains("contact sales"));
        verify(paystackGatewayService, never()).initializeTransaction(any());
    }

    @Test
    void verifyCheckout_activatesSubscriptionWhenPaidAmountMatches() throws Exception {
        SubscriptionPlan basic = plan("BASIC", BillingPlanTier.BASIC, 79_900L, "GHS");
        BillingPayment payment = pendingPayment("ref_ok", 79_900L, basic);
        when(billingPaymentRepository.lockByReference("ref_ok")).thenReturn(Optional.of(payment));
        when(paystackGatewayService.verifyTransaction("ref_ok")).thenReturn(objectMapper.readTree(
                "{\"data\":{\"status\":\"success\",\"amount\":79900,\"id\":42,\"channel\":\"mobile_money\","
                        + "\"gateway_response\":\"Approved\"}}"));

        OrganisationSubscription existing = new OrganisationSubscription();
        existing.setOrganisation(org);
        existing.setStatus(SubscriptionStatus.ACTIVE);
        when(organisationSubscriptionRepository.findFirstByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(org))
                .thenReturn(Optional.of(existing));
        when(organisationSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(billingPaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        billingService.verifyCheckout("ref_ok");

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("mobile_money", payment.getChannel());
        assertEquals(SubscriptionStatus.ACTIVE, existing.getStatus());
        assertEquals(basic, existing.getPlan(), "The paid plan must become the active subscription plan");
    }

    @Test
    void verifyCheckout_rejectsUnderpaymentAndDoesNotActivate() throws Exception {
        SubscriptionPlan basic = plan("BASIC", BillingPlanTier.BASIC, 79_900L, "GHS");
        BillingPayment payment = pendingPayment("ref_short", 79_900L, basic);
        when(billingPaymentRepository.lockByReference("ref_short")).thenReturn(Optional.of(payment));
        // Gateway reports success but the customer paid less than the plan price.
        when(paystackGatewayService.verifyTransaction("ref_short")).thenReturn(objectMapper.readTree(
                "{\"data\":{\"status\":\"success\",\"amount\":50000,\"id\":7,\"channel\":\"card\"}}"));
        lenient().when(billingPaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(PaymentRejectedException.class, () -> billingService.verifyCheckout("ref_short"));

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        verify(organisationSubscriptionRepository, never()).save(any());
    }

    // ── Plan changes ─────────────────────────────────────────────────────────

    private OrganisationSubscription currentSubscription(SubscriptionPlan plan) {
        OrganisationSubscription s = new OrganisationSubscription();
        s.setOrganisation(org);
        s.setPlan(plan);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setAutoRenew(true);
        s.setCurrentPeriodStart(Instant.now().minus(10, ChronoUnit.DAYS));
        s.setCurrentPeriodEnd(Instant.now().plus(20, ChronoUnit.DAYS));
        lenient().when(organisationSubscriptionRepository
                        .findFirstByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(org))
                .thenReturn(Optional.of(s));
        lenient().when(organisationSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return s;
    }

    private BillingCheckoutRequest request(String planCode, String callbackUrl) {
        BillingCheckoutRequest request = new BillingCheckoutRequest();
        request.setPlanCode(planCode);
        request.setCallbackUrl(callbackUrl);
        return request;
    }

    @Test
    void changePlan_upgradeStartsACheckoutAndLeavesThePlanUntilPaid() throws Exception {
        SubscriptionPlan basic = plan("BASIC", BillingPlanTier.BASIC, 79_900L, "GHS");
        SubscriptionPlan business = plan("BUSINESS", BillingPlanTier.BUSINESS, 199_900L, "GHS");
        OrganisationSubscription current = currentSubscription(basic);
        when(subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("BUSINESS")).thenReturn(Optional.of(business));
        when(paystackGatewayService.initializeTransaction(any())).thenReturn(objectMapper.readTree(
                "{\"status\":true,\"data\":{\"authorization_url\":\"https://checkout.paystack.com/up\"}}"));

        PlanChangeResponse response = billingService.changePlan(request("BUSINESS", null));

        assertEquals(PlanChangeResponse.Action.CHECKOUT, response.getAction());
        assertEquals("https://checkout.paystack.com/up", response.getCheckout().getAuthorizationUrl());
        assertEquals(basic, current.getPlan(), "An upgrade must not apply before it is paid for");
    }

    @Test
    void changePlan_downgradeIsScheduledForPeriodEndAndStopsTheRecurringCharge() {
        SubscriptionPlan business = plan("BUSINESS", BillingPlanTier.BUSINESS, 199_900L, "GHS");
        SubscriptionPlan freemium = plan("FREEMIUM", BillingPlanTier.FREEMIUM, 0L, "GHS");
        OrganisationSubscription current = currentSubscription(business);
        current.setPaystackSubscriptionCode("SUB_old");
        current.setPaystackEmailToken("tok_old");
        when(subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("FREEMIUM")).thenReturn(Optional.of(freemium));

        PlanChangeResponse response = billingService.changePlan(request("FREEMIUM", null));

        assertEquals(PlanChangeResponse.Action.SCHEDULED, response.getAction());
        assertEquals(business, current.getPlan(), "The paid plan continues until the period ends");
        assertEquals(freemium, current.getScheduledPlan());
        assertEquals(current.getCurrentPeriodEnd(), current.getScheduledChangeAt());
        assertEquals(Boolean.FALSE, current.getAutoRenew());
        assertEquals("FREEMIUM", response.getSubscription().getScheduledPlan().getCode());
        verify(paystackGatewayService).disableSubscription("SUB_old", "tok_old");
    }

    @Test
    void changePlan_downgradeRefusedWhenUsageDoesNotFit() {
        SubscriptionPlan business = plan("BUSINESS", BillingPlanTier.BUSINESS, 199_900L, "GHS");
        SubscriptionPlan basic = plan("BASIC", BillingPlanTier.BASIC, 79_900L, "GHS");
        OrganisationSubscription current = currentSubscription(business);
        when(subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("BASIC")).thenReturn(Optional.of(basic));
        doThrow(new IllegalStateException("Your current usage does not fit the BASIC plan"))
                .when(usageLimitService).assertUsageFitsPlan(org, basic);

        assertThrows(IllegalStateException.class, () -> billingService.changePlan(request("BASIC", null)));

        assertEquals(null, current.getScheduledPlan());
        verify(organisationSubscriptionRepository, never()).save(any());
    }

    @Test
    void changePlan_samePlanIsNoChange() {
        SubscriptionPlan basic = plan("BASIC", BillingPlanTier.BASIC, 79_900L, "GHS");
        currentSubscription(basic);
        when(subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("BASIC")).thenReturn(Optional.of(basic));

        assertEquals(PlanChangeResponse.Action.NO_CHANGE,
                billingService.changePlan(request("BASIC", null)).getAction());
        verify(paystackGatewayService, never()).initializeTransaction(any());
    }

    @Test
    void cancelScheduledChange_restoresRenewal() {
        SubscriptionPlan business = plan("BUSINESS", BillingPlanTier.BUSINESS, 199_900L, "GHS");
        OrganisationSubscription current = currentSubscription(business);
        current.setScheduledPlan(plan("FREEMIUM", BillingPlanTier.FREEMIUM, 0L, "GHS"));
        current.setAutoRenew(false);

        billingService.cancelScheduledChange();

        assertEquals(null, current.getScheduledPlan());
        assertEquals(Boolean.TRUE, current.getAutoRenew());
    }

    @Test
    void listPlans_offersTheAnnualPlan() {
        SubscriptionPlan annual = plan("BUSINESS_ANNUAL", BillingPlanTier.BUSINESS, 2_000_000L, "GHS");
        annual.setInterval(BillingInterval.ANNUALLY);
        when(subscriptionPlanRepository.findByActiveIsTrueAndDeletedAtIsNullOrderByAmountMinorAsc())
                .thenReturn(List.of(annual));

        assertEquals("BUSINESS_ANNUAL", billingService.listPlans().get(0).getCode());
    }

    // ── Callback URL ─────────────────────────────────────────────────────────

    @Test
    void callbackUrl_onlyAcceptsTheAppsOwnOrigins() {
        assertEquals("https://app.assetiq.io/billing/callback/",
                billingService.resolveCallbackUrl("https://app.assetiq.io/billing/callback/"));
        assertEquals("https://portal.assetiq.io/callback",
                billingService.resolveCallbackUrl("https://evil.example/phish"));
        assertEquals("https://portal.assetiq.io/callback",
                billingService.resolveCallbackUrl("javascript:alert(1)"));
        assertEquals("https://portal.assetiq.io/callback", billingService.resolveCallbackUrl(null));
    }

    // ── Verify ───────────────────────────────────────────────────────────────

    @Test
    void verifyCheckout_refusesAnotherTenantsReference() {
        Organisation other = new Organisation();
        BillingPayment foreign = pendingPayment("ref_foreign", 1L, plan("BASIC", BillingPlanTier.BASIC, 1L, "GHS"));
        foreign.setOrganisation(other);
        when(billingPaymentRepository.lockByReference("ref_foreign")).thenReturn(Optional.of(foreign));

        assertThrows(IllegalArgumentException.class, () -> billingService.verifyCheckout("ref_foreign"));
        verify(paystackGatewayService, never()).verifyTransaction(anyString());
    }

    @Test
    void verifyCheckout_rejectsCurrencyMismatch() throws Exception {
        SubscriptionPlan basic = plan("BASIC", BillingPlanTier.BASIC, 79_900L, "GHS");
        BillingPayment payment = pendingPayment("ref_ccy", 79_900L, basic);
        when(billingPaymentRepository.lockByReference("ref_ccy")).thenReturn(Optional.of(payment));
        when(paystackGatewayService.verifyTransaction("ref_ccy")).thenReturn(objectMapper.readTree(
                "{\"data\":{\"status\":\"success\",\"amount\":79900,\"currency\":\"NGN\",\"id\":9}}"));
        lenient().when(billingPaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(PaymentRejectedException.class, () -> billingService.verifyCheckout("ref_ccy"));
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    // ── Webhook renewals ─────────────────────────────────────────────────────

    private static String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static String renewalCharge(long amount, String currency, long transactionId) {
        return "{\"event\":\"charge.success\",\"data\":{\"id\":" + transactionId
                + ",\"reference\":\"gw_renewal_" + transactionId + "\",\"amount\":" + amount
                + ",\"currency\":\"" + currency + "\",\"status\":\"success\","
                + "\"customer\":{\"customer_code\":\"CUS_1\"}}}";
    }

    @Test
    void webhook_recurringChargeExtendsThePaidPeriodAndIsRecorded() throws Exception {
        SubscriptionPlan business = plan("BUSINESS", BillingPlanTier.BUSINESS, 199_900L, "GHS");
        OrganisationSubscription current = currentSubscription(business);
        current.setStatus(SubscriptionStatus.PAST_DUE);
        current.setPastDueSince(Instant.now().minus(2, ChronoUnit.DAYS));
        current.setCurrentPeriodEnd(Instant.now().minus(2, ChronoUnit.DAYS));
        when(organisationSubscriptionRepository.findFirstByPaystackCustomerCodeAndDeletedAtIsNull("CUS_1"))
                .thenReturn(Optional.of(current));
        when(billingPaymentRepository.lockByReference(anyString())).thenReturn(Optional.empty());

        String payload = renewalCharge(199_900L, "GHS", 555L);
        billingService.handlePaystackWebhook(sign(payload), payload);

        assertEquals(SubscriptionStatus.ACTIVE, current.getStatus());
        assertEquals(null, current.getPastDueSince());
        assertTrue(current.getCurrentPeriodEnd().isAfter(Instant.now().plus(29, ChronoUnit.DAYS)));
        ArgumentCaptor<BillingPayment> saved = ArgumentCaptor.forClass(BillingPayment.class);
        verify(billingPaymentRepository).save(saved.capture());
        assertEquals(555L, saved.getValue().getPaystackTransactionId());
        assertEquals(PaymentStatus.SUCCESS, saved.getValue().getStatus());
    }

    @Test
    void webhook_duplicateRenewalIsIgnored() throws Exception {
        SubscriptionPlan business = plan("BUSINESS", BillingPlanTier.BUSINESS, 199_900L, "GHS");
        OrganisationSubscription current = currentSubscription(business);
        Instant end = current.getCurrentPeriodEnd();
        when(organisationSubscriptionRepository.findFirstByPaystackCustomerCodeAndDeletedAtIsNull("CUS_1"))
                .thenReturn(Optional.of(current));
        when(billingPaymentRepository.lockByReference(anyString())).thenReturn(Optional.empty());
        when(billingPaymentRepository.existsByPaystackTransactionId(555L)).thenReturn(true);

        String payload = renewalCharge(199_900L, "GHS", 555L);
        billingService.handlePaystackWebhook(sign(payload), payload);

        assertEquals(end, current.getCurrentPeriodEnd());
        verify(billingPaymentRepository, never()).save(any());
    }

    @Test
    void webhook_renewalForTheWrongAmountIsNotApplied() throws Exception {
        SubscriptionPlan business = plan("BUSINESS", BillingPlanTier.BUSINESS, 199_900L, "GHS");
        OrganisationSubscription current = currentSubscription(business);
        Instant end = current.getCurrentPeriodEnd();
        when(organisationSubscriptionRepository.findFirstByPaystackCustomerCodeAndDeletedAtIsNull("CUS_1"))
                .thenReturn(Optional.of(current));
        when(billingPaymentRepository.lockByReference(anyString())).thenReturn(Optional.empty());

        String payload = renewalCharge(100L, "GHS", 556L);
        billingService.handlePaystackWebhook(sign(payload), payload);

        assertEquals(end, current.getCurrentPeriodEnd());
        verify(billingPaymentRepository, never()).save(any());
    }

    private BillingPayment pendingPayment(String reference, long amountMinor, SubscriptionPlan plan) {
        BillingPayment payment = new BillingPayment();
        payment.setOrganisation(org);
        payment.setPlan(plan);
        payment.setReference(reference);
        payment.setAmountMinor(amountMinor);
        payment.setCurrency(plan.getCurrency());
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }
}
