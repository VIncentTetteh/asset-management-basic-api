package com.assetiq.security.aspect;

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

/** Fail-closed step-up enforcement for high-impact controller actions. */
@Aspect
@Component
public class FreshMfaAspect {

    @Around("@annotation(requireFreshMfa)")
    public Object requireFreshMfa(ProceedingJoinPoint joinPoint,
                                  RequireFreshMfa requireFreshMfa) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        if (!(authentication.getDetails() instanceof Claims claims)) {
            throw new AccessDeniedException("Recent MFA authentication is required");
        }
        Number authenticatedAt = claims.get("mfaAuthenticatedAt", Number.class);
        if (authenticatedAt == null) {
            throw new AccessDeniedException("Recent MFA authentication is required");
        }
        long ageSeconds = Instant.now().getEpochSecond() - authenticatedAt.longValue();
        if (ageSeconds < 0 || ageSeconds > requireFreshMfa.maxAgeSeconds()) {
            throw new AccessDeniedException("MFA authentication has expired; sign in again");
        }
        return joinPoint.proceed();
    }
}

