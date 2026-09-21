package com.assetiq.controllers.v1;

import com.assetiq.models.Organisation;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The /setup → /verify enrolment contract the web profile page depends on. */
@ExtendWith(MockitoExtension.class)
class MfaEnrolmentControllerTest {

    private static final String EMAIL = "user@example.com";

    @Mock UserRepository userRepository;
    @Mock JwtUtil jwtUtil;
    @Mock RefreshSessionService refreshSessionService;
    @Mock SecretCryptoService secretCryptoService;
    @Mock SessionRevocationService sessionRevocationService;

    private final UUID orgId = UUID.randomUUID();
    private User user;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MfaController controller = new MfaController(userRepository, jwtUtil, refreshSessionService,
                secretCryptoService, sessionRevocationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Organisation organisation = new Organisation();
        organisation.setId(orgId);
        user = new User();
        user.setEmail(EMAIL);
        user.setOrganisation(organisation);
        TenantContext.setOrganisationId(orgId);
        when(userRepository.findByEmailAndOrganisationId(EMAIL, orgId)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void setup_returnsSecretAndQrDataUri() throws Exception {
        when(secretCryptoService.encrypt(anyString())).thenReturn("encrypted");

        mockMvc.perform(authed(post("/api/v1/mfa/setup")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isString())
                .andExpect(jsonPath("$.qrCodeImage").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));

        assertThat(user.getMfaSecret()).isEqualTo("encrypted");
        assertThat(user.getMfaEnabled()).isFalse();
    }

    @Test
    void setup_whenAlreadyEnabled_isRefusedWithoutReplacingSecret() throws Exception {
        user.setMfaEnabled(true);
        user.setMfaSecret("live-secret");

        mockMvc.perform(authed(post("/api/v1/mfa/setup")))
                .andExpect(status().isConflict());

        assertThat(user.getMfaSecret()).isEqualTo("live-secret");
        verifyNoInteractions(secretCryptoService);
    }

    @Test
    void verify_validCode_enablesMfaAndRevokesSessions() throws Exception {
        String secret = new DefaultSecretGenerator().generate();
        user.setMfaSecret("encrypted");
        when(secretCryptoService.decrypt("encrypted")).thenReturn(secret);
        String code = new DefaultCodeGenerator(HashingAlgorithm.SHA1)
                .generate(secret, Instant.now().getEpochSecond() / 30);

        mockMvc.perform(authed(post("/api/v1/mfa/verify")).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        assertThat(user.getMfaEnabled()).isTrue();
        verify(sessionRevocationService).revokeAll(user);
    }

    @Test
    void verify_invalidCode_returnsTypedMfaCodeInvalid() throws Exception {
        user.setMfaSecret("encrypted");
        when(secretCryptoService.decrypt("encrypted")).thenReturn(new DefaultSecretGenerator().generate());

        mockMvc.perform(authed(post("/api/v1/mfa/verify")).content("{\"code\":\"12345\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("MFA_CODE_INVALID"));

        assertThat(user.getMfaEnabled()).isFalse();
        verifyNoInteractions(sessionRevocationService);
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder
                .principal(UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of()))
                .contentType(MediaType.APPLICATION_JSON);
    }
}
