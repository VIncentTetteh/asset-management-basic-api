package com.assetiq.controllers.v1;

import com.assetiq.config.RateLimitingConfig;
import com.assetiq.config.RateLimitingInterceptor;
import com.assetiq.config.RedisRateLimiter;
import com.assetiq.models.Organisation;
import com.assetiq.models.Role;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.UserRepository;
import com.assetiq.security.JwtUtil;
import com.assetiq.security.SecretCryptoService;
import com.assetiq.services.RefreshSessionService;
import com.assetiq.services.SessionRevocationService;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.assetiq.config.RateLimitingConfig.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contract tests for {@code POST /api/v1/mfa/step-up}. */
@ExtendWith(MockitoExtension.class)
class MfaStepUpControllerTest {

    private static final String EMAIL = "approver@example.com";
    private static final long SESSION_VERSION = 7L;

    @Mock UserRepository userRepository;
    @Mock JwtUtil jwtUtil;
    @Mock RefreshSessionService refreshSessionService;
    @Mock SecretCryptoService secretCryptoService;
    @Mock SessionRevocationService sessionRevocationService;
    @Mock RedisRateLimiter rateLimiter;

    private final UUID orgId = UUID.randomUUID();
    private final String secret = new DefaultSecretGenerator().generate();
    private User user;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MfaController controller = new MfaController(userRepository, jwtUtil, refreshSessionService,
                secretCryptoService, sessionRevocationService);
        ReflectionTestUtils.setField(controller, "jwtExpirationMillis", 900_000L);
        ReflectionTestUtils.setField(controller, "authCookieSecure", true);

        RateLimitingConfig rateConfig = new RateLimitingConfig();
        rateConfig.setEnabled(true);
        rateConfig.setTrustedProxyCidrs(List.of());

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new RateLimitingInterceptor(rateLimiter, rateConfig))
                .build();

        Organisation organisation = new Organisation();
        organisation.setId(orgId);
        Role role = new Role();
        role.setName("FINANCE_APPROVER");
        user = new User();
        user.setEmail(EMAIL);
        user.setFirstName("Ama");
        user.setLastName("Mensah");
        user.setOrganisation(organisation);
        user.setRole(role);
        user.setSessionVersion(SESSION_VERSION);

        TenantContext.setOrganisationId(orgId);
        lenient().when(userRepository.findByEmailAndOrganisationId(EMAIL, orgId)).thenReturn(Optional.of(user));
        lenient().when(rateLimiter.tryConsume(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new RedisRateLimiter.RateLimitResult(true, 4, 60));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void validCode_reissuesAccessCookieWithFreshMfaClaimAndKeepsRefreshSession() throws Exception {
        enrol();
        when(jwtUtil.generateToken(eq(EMAIL), anyMap(), eq(900_000L))).thenReturn("stepped-up-jwt");
        long before = Instant.now().getEpochSecond();

        String setCookie = mockMvc.perform(stepUp(currentCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaAuthenticatedAt").isNumber())
                .andExpect(jsonPath("$.token").value("stepped-up-jwt"))
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).startsWith("access_token=stepped-up-jwt")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> claims = ArgumentCaptor.forClass(Map.class);
        verify(jwtUtil).generateToken(eq(EMAIL), claims.capture(), eq(900_000L));
        assertThat(claims.getValue())
                .containsEntry("sessionVersion", SESSION_VERSION)
                .containsEntry("role", "ROLE_FINANCE_APPROVER")
                .containsEntry("organisationId", orgId.toString())
                .containsEntry("email", EMAIL);
        assertThat((Long) claims.getValue().get("mfaAuthenticatedAt")).isGreaterThanOrEqualTo(before);
        verifyNoInteractions(refreshSessionService);
    }

    @Test
    void invalidCode_returns401MfaCodeInvalid() throws Exception {
        enrol();

        mockMvc.perform(stepUp("000000".equals(currentCode()) ? "111111" : "000000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("MFA_CODE_INVALID"));

        verifyNoInteractions(jwtUtil);
    }

    @Test
    void userWithoutMfa_returns428EnrolmentRequired() throws Exception {
        mockMvc.perform(stepUp("123456"))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.errorCode").value("MFA_ENROLMENT_REQUIRED"));

        verifyNoInteractions(jwtUtil, secretCryptoService);
    }

    @Test
    void exhaustedAuthTier_returns429BeforeVerifyingCode() throws Exception {
        when(rateLimiter.tryConsume(eq(TIER_AUTH_MINUTE), anyString(), eq(AUTH_REQUESTS_PER_MINUTE), anyInt()))
                .thenReturn(new RedisRateLimiter.RateLimitResult(false, 0, 30));

        mockMvc.perform(stepUp("123456"))
                .andExpect(status().isTooManyRequests());

        verifyNoInteractions(userRepository, jwtUtil, secretCryptoService);
    }

    private void enrol() {
        user.setMfaEnabled(true);
        user.setMfaSecret("encrypted-secret");
        when(secretCryptoService.decrypt("encrypted-secret")).thenReturn(secret);
    }

    private String currentCode() {
        try {
            return new DefaultCodeGenerator(HashingAlgorithm.SHA1)
                    .generate(secret, Instant.now().getEpochSecond() / 30);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MockHttpServletRequestBuilder stepUp(String code) {
        return post("/api/v1/mfa/step-up")
                .principal(UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}");
    }
}
