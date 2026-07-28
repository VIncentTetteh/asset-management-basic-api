package com.assetiq.services.impl;

import com.assetiq.dto.BillingCheckoutRequest;
import com.assetiq.dto.BillingCheckoutResponse;
import com.assetiq.enums.BillingPlanTier;
import com.assetiq.enums.PaymentStatus;
import com.assetiq.enums.SubscriptionStatus;
import com.assetiq.models.BillingPayment;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrganisationSubscription;
import com.assetiq.models.SubscriptionPlan;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.BillingPaymentRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.OrganisationSubscriptionRepository;
import com.assetiq.repositories.SubscriptionPlanRepository;
import com.assetiq.repositories.UserRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
                objectMapper);
        ReflectionTestUtils.setField(billingService, "paystackChannelsCsv", "card,mobile_money,bank,ussd");
        ReflectionTestUtils.setField(billingService, "defaultCallbackUrl", "https://portal.assetiq.io/callback");

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
        when(billingPaymentRepository.findByReferenceAndOrganisationAndDeletedAtIsNull("ref_ok", org))
                .thenReturn(Optional.of(payment));
        when(paystackGatewayService.verifyTransaction("ref_ok")).thenReturn(objectMapper.readTree(
                "{\"data\":{\"status\":\"success\",\"amount\":79900,\"id\":42,\"channel\":\"mobile_money\","
                        + "\"gateway_response\":\"Approved\"}}"));

        OrganisationSubscription existing = new OrganisationSubscription();
        existing.setOrganisation(org);
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
        when(billingPaymentRepository.findByReferenceAndOrganisationAndDeletedAtIsNull("ref_short", org))
                .thenReturn(Optional.of(payment));
        // Gateway reports success but the customer paid less than the plan price.
        when(paystackGatewayService.verifyTransaction("ref_short")).thenReturn(objectMapper.readTree(
                "{\"data\":{\"status\":\"success\",\"amount\":50000,\"id\":7,\"channel\":\"card\"}}"));
        lenient().when(billingPaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(IllegalStateException.class, () -> billingService.verifyCheckout("ref_short"));

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        verify(organisationSubscriptionRepository, never()).save(any());
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
