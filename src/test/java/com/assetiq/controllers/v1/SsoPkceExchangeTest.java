package com.assetiq.controllers.v1;

import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.repositories.OrgSsoConfigRepository;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.security.JwtUtil;
import com.assetiq.security.sso.Pkce;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PKCE on the mobile SSO handoff.
 *
 * <p>The exchange code is delivered to the app through a custom URL scheme, which
 * any app on the device can claim. These tests pin the properties that make a
 * stolen code worthless: the verifier is required when the flow was started with a
 * challenge, a wrong one is refused, and a refused attempt still spends the code.
 */
@DisplayName("SSO exchange with PKCE")
class SsoPkceExchangeTest {

    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    private SsoController controller;
    private UserRepository userRepository;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        com.assetiq.services.RefreshSessionService refreshSessionService =
                mock(com.assetiq.services.RefreshSessionService.class);
        when(refreshSessionService.issue(any())).thenReturn(
                new com.assetiq.services.RefreshSessionService.IssuedRefreshToken(
                        "refresh-token", Instant.now().plusSeconds(3600)));

        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);

        controller = new SsoController(
                mock(OrgSsoConfigRepository.class),
                userRepository,
                mock(RoleRepository.class),
                mock(JwtUtil.class),
                mock(PasswordEncoder.class),
                refreshSessionService,
                mock(com.assetiq.security.SecretCryptoService.class),
                redisProvider);
        ReflectionTestUtils.setField(controller, "jwtExpirationMillis", 900_000L);
        ReflectionTestUtils.setField(controller, "allowedExchangeRedirectPrefixes", "assetiq://");
        ReflectionTestUtils.setField(controller, "allowedExchangeRedirectHttpsPrefix", "");

        Organisation org = new Organisation();
        org.setId(UUID.randomUUID());
        User user = new User();
        userId = user.getId();
        user.setEmail("scanner@example.com");
        user.setFirstName("Kojo");
        user.setLastName("Mensah");
        user.setOrganisation(org);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    void initiateRefusesThePlainMethod() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.initiate(UUID.randomUUID(), null, null, CHALLENGE, "plain", response);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getErrorMessage()).contains("S256");
    }

    @Test
    void initiateRefusesAMalformedChallenge() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.initiate(UUID.randomUUID(), null, null, "not a challenge", "S256", response);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void anExchangeStartedWithoutPkceStillWorks() throws Exception {
        String code = seedExchangeCode(null);
        var result = controller.exchange(Map.of("code", code));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theRightVerifierRedeemsTheCode() throws Exception {
        String code = seedExchangeCode(CHALLENGE);
        var result = controller.exchange(Map.of("code", code, "codeVerifier", VERIFIER));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isInstanceOf(Map.class);
    }

    @Test
    void theSnakeCaseSpellingIsAcceptedToo() throws Exception {
        String code = seedExchangeCode(CHALLENGE);
        var result = controller.exchange(Map.of("code", code, "code_verifier", VERIFIER));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aMissingVerifierIsRefusedWhenAChallengeWasStored() throws Exception {
        String code = seedExchangeCode(CHALLENGE);
        var result = controller.exchange(Map.of("code", code));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aWrongVerifierIsRefused() throws Exception {
        String code = seedExchangeCode(CHALLENGE);
        var result = controller.exchange(Map.of("code", code, "codeVerifier", "b".repeat(43)));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aRefusedAttemptStillSpendsTheCode() throws Exception {
        String code = seedExchangeCode(CHALLENGE);
        assertThat(controller.exchange(Map.of("code", code, "codeVerifier", "b".repeat(43)))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // The thief's failed guess must not leave the code redeemable by the real app.
        assertThat(controller.exchange(Map.of("code", code, "codeVerifier", VERIFIER))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aCodeCanOnlyBeRedeemedOnce() throws Exception {
        String code = seedExchangeCode(CHALLENGE);
        assertThat(controller.exchange(Map.of("code", code, "codeVerifier", VERIFIER))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.exchange(Map.of("code", code, "codeVerifier", VERIFIER))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theHttpsRedirectPrefixIsOnlyHonouredWhenConfiguredAndHttps() throws Exception {
        Method normalise = SsoController.class.getDeclaredMethod("normalizeExchangeRedirectUri", String.class);
        normalise.setAccessible(true);

        // Not configured: only the built-in custom schemes are accepted.
        assertThat(normalise.invoke(controller, "https://app.example.test/cb")).isNull();
        assertThat(normalise.invoke(controller, "assetiq://cb")).isEqualTo("assetiq://cb");

        ReflectionTestUtils.setField(controller, "allowedExchangeRedirectHttpsPrefix",
                "https://app.example.test/");
        assertThat(normalise.invoke(controller, "https://app.example.test/cb"))
                .isEqualTo("https://app.example.test/cb");
        assertThat(normalise.invoke(controller, "https://evil.example.test/cb")).isNull();

        // An http:// entry is ignored: it would hand the code to the network.
        ReflectionTestUtils.setField(controller, "allowedExchangeRedirectHttpsPrefix",
                "http://app.example.test/");
        assertThat(normalise.invoke(controller, "http://app.example.test/cb")).isNull();
    }

    @Test
    void theChallengeSurvivesSerialisationOfTheStoredCode() {
        assertThat(Pkce.matches(CHALLENGE, VERIFIER)).isTrue();
    }

    /** Puts a code in the controller's store the way the OAuth2 callback does. */
    private String seedExchangeCode(String codeChallenge) throws Exception {
        Method store = SsoController.class.getDeclaredMethod(
                "storeExchangeCode", String.class, UUID.class, String.class, String.class);
        store.setAccessible(true);
        String code = UUID.randomUUID().toString().replace("-", "");
        store.invoke(controller, code, userId, "access-token", codeChallenge);
        return code;
    }
}
