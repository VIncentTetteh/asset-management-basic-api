package com.assetiq.security.aspect;

import com.assetiq.security.annotation.RequireFreshMfa;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FreshMfaAspectTest {

    @Mock ProceedingJoinPoint joinPoint;
    @Mock RequireFreshMfa annotation;

    private final FreshMfaAspect aspect = new FreshMfaAspect();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void freshMfaClaim_allowsSensitiveAction() throws Throwable {
        when(annotation.maxAgeSeconds()).thenReturn(600L);
        when(joinPoint.proceed()).thenReturn("allowed");
        authenticateWithClaim(Instant.now().getEpochSecond());

        assertThat(aspect.requireFreshMfa(joinPoint, annotation)).isEqualTo("allowed");
        verify(joinPoint).proceed();
    }

    @Test
    void missingMfaClaim_deniesSensitiveAction() {
        authenticateWithClaim(null);

        assertThatThrownBy(() -> aspect.requireFreshMfa(joinPoint, annotation))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("MFA");
        verifyNoInteractions(joinPoint);
    }

    @Test
    void staleMfaClaim_deniesSensitiveAction() {
        when(annotation.maxAgeSeconds()).thenReturn(600L);
        authenticateWithClaim(Instant.now().minusSeconds(601).getEpochSecond());

        assertThatThrownBy(() -> aspect.requireFreshMfa(joinPoint, annotation))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("expired");
    }

    private void authenticateWithClaim(Long authenticatedAt) {
        Claims claims = authenticatedAt == null
                ? Jwts.claims().build()
                : Jwts.claims().add("mfaAuthenticatedAt", authenticatedAt).build();
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("admin@example.com", null, List.of());
        authentication.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
