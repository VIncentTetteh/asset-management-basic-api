package com.assetiq.security.aspect;

import com.assetiq.controllers.v1.GlobalExceptionHandler;
import com.assetiq.exceptions.MfaEnrolmentRequiredException;
import com.assetiq.exceptions.MfaStepUpRequiredException;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.UserRepository;
import com.assetiq.security.annotation.RequireFreshMfa;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FreshMfaAspectTest {

    private static final String EMAIL = "admin@example.com";

    @Mock ProceedingJoinPoint joinPoint;
    @Mock RequireFreshMfa annotation;
    @Mock UserRepository userRepository;

    private final UUID orgId = UUID.randomUUID();
    private FreshMfaAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new FreshMfaAspect(userRepository);
        TenantContext.setOrganisationId(orgId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void freshMfaClaim_allowsSensitiveActionWithoutUserLookup() throws Throwable {
        when(annotation.maxAgeSeconds()).thenReturn(600L);
        when(joinPoint.proceed()).thenReturn("allowed");
        authenticateWithClaim(Instant.now().getEpochSecond());

        assertThat(aspect.requireFreshMfa(joinPoint, annotation)).isEqualTo("allowed");
        verify(joinPoint).proceed();
        verifyNoInteractions(userRepository);
    }

    @Test
    void missingMfaClaim_enrolledUser_requiresStepUp() {
        givenUser(true);
        authenticateWithClaim(null);

        assertThatThrownBy(() -> aspect.requireFreshMfa(joinPoint, annotation))
                .isInstanceOf(MfaStepUpRequiredException.class)
                .hasMessageContaining("MFA");
        verifyNoInteractions(joinPoint);
    }

    @Test
    void staleMfaClaim_enrolledUser_requiresStepUp() {
        givenUser(true);
        when(annotation.maxAgeSeconds()).thenReturn(600L);
        authenticateWithClaim(Instant.now().minusSeconds(601).getEpochSecond());

        assertThatThrownBy(() -> aspect.requireFreshMfa(joinPoint, annotation))
                .isInstanceOf(MfaStepUpRequiredException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void missingMfaClaim_userWithoutMfa_requiresEnrolment() {
        givenUser(false);
        authenticateWithClaim(null);

        assertThatThrownBy(() -> aspect.requireFreshMfa(joinPoint, annotation))
                .isInstanceOf(MfaEnrolmentRequiredException.class);
        verifyNoInteractions(joinPoint);
    }

    @Test
    void unauthenticated_isStillAccessDenied() {
        assertThatThrownBy(() -> aspect.requireFreshMfa(joinPoint, annotation))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rendersStepUpAs401AndEnrolmentAs428ThroughTheRealAdvice() throws Exception {
        AspectJProxyFactory factory = new AspectJProxyFactory(new ApprovalStubController());
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup((Object) factory.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        authenticateWithClaim(null);

        givenUser(true);
        mockMvc.perform(post("/approve"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("MFA_STEP_UP_REQUIRED"));

        givenUser(false);
        mockMvc.perform(post("/approve"))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.errorCode").value("MFA_ENROLMENT_REQUIRED"));
    }

    @RestController
    static class ApprovalStubController {
        @PostMapping("/approve")
        @RequireFreshMfa
        public String approve() {
            return "approved";
        }
    }

    private void givenUser(boolean mfaEnrolled) {
        User user = new User();
        user.setEmail(EMAIL);
        user.setMfaEnabled(mfaEnrolled);
        user.setMfaSecret(mfaEnrolled ? "encrypted" : null);
        when(userRepository.findByEmailAndOrganisationId(EMAIL, orgId)).thenReturn(Optional.of(user));
    }

    private void authenticateWithClaim(Long authenticatedAt) {
        Claims claims = authenticatedAt == null
                ? Jwts.claims().build()
                : Jwts.claims().add("mfaAuthenticatedAt", authenticatedAt).build();
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of());
        authentication.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
