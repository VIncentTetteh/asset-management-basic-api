package com.assetiq.security.aspect;

import com.assetiq.exceptions.MfaEnrolmentRequiredException;
import com.assetiq.exceptions.MfaStepUpRequiredException;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.UserRepository;
import com.assetiq.security.annotation.RequireFreshMfa;
import io.jsonwebtoken.Claims;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Fail-closed step-up enforcement for high-impact controller actions.
 *
 * <p>When the access token lacks a fresh {@code mfaAuthenticatedAt} claim the
 * request is rejected with a machine-readable reason so the client can react:
 * <ul>
 *   <li>{@link MfaStepUpRequiredException} (401 {@code MFA_STEP_UP_REQUIRED}) — the
 *       user has MFA enrolled; prompt for a code and call {@code POST /api/v1/mfa/step-up}.</li>
 *   <li>{@link MfaEnrolmentRequiredException} (428 {@code MFA_ENROLMENT_REQUIRED}) — the
 *       user has no authenticator, so a step-up prompt could never succeed.</li>
 * </ul>
 * The enrolment lookup only happens on the rejection path, so a request with a
 * fresh claim costs no database round trip.
 */
@Aspect
@Component
public class FreshMfaAspect {

    private final UserRepository userRepository;

    public FreshMfaAspect(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Around("@annotation(requireFreshMfa)")
    public Object requireFreshMfa(ProceedingJoinPoint joinPoint,
                                  RequireFreshMfa requireFreshMfa) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        Claims claims = authentication.getDetails() instanceof Claims c ? c : null;
        Number authenticatedAt = claims != null ? claims.get("mfaAuthenticatedAt", Number.class) : null;
        if (authenticatedAt == null) {
            throw stepUpRejection(authentication, claims, "Recent MFA authentication is required");
        }
        long ageSeconds = Instant.now().getEpochSecond() - authenticatedAt.longValue();
        if (ageSeconds < 0 || ageSeconds > requireFreshMfa.maxAgeSeconds()) {
            throw stepUpRejection(authentication, claims,
                    "MFA authentication has expired; confirm with your authenticator code");
        }
        return joinPoint.proceed();
    }

    private RuntimeException stepUpRejection(Authentication authentication, Claims claims, String message) {
        if (!hasMfaEnrolled(authentication.getName(), claims)) {
            return new MfaEnrolmentRequiredException(
                    "Two-factor authentication must be set up before performing this action");
        }
        return new MfaStepUpRequiredException(message);
    }

    /**
     * Unknown/unresolvable users are treated as enrolled: that yields the step-up
     * response, whose endpoint re-resolves the user and fails closed on its own.
     */
    private boolean hasMfaEnrolled(String email, Claims claims) {
        UUID organisationId = resolveOrganisationId(claims);
        if (email == null || organisationId == null) {
            return true;
        }
        return userRepository.findByEmailAndOrganisationId(email, organisationId)
                .map(FreshMfaAspect::isEnrolled)
                .orElse(true);
    }

    private static boolean isEnrolled(User user) {
        return Boolean.TRUE.equals(user.getMfaEnabled()) && user.getMfaSecret() != null;
    }

    private static UUID resolveOrganisationId(Claims claims) {
        UUID fromContext = TenantContext.getOrganisationId();
        if (fromContext != null) {
            return fromContext;
        }
        String fromClaim = claims != null ? claims.get("organisationId", String.class) : null;
        if (fromClaim == null || fromClaim.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(fromClaim);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
