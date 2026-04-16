package com.assetiq.security.aspect;

import com.assetiq.multitenancy.TenantContext;
import com.assetiq.security.annotation.EnforceTenant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.util.UUID;

/**
 * AOP aspect that enforces tenant isolation on any controller method annotated
 * with {@link EnforceTenant}.
 *
 * <p>The aspect intercepts the method call, extracts the {@code organisationId}
 * parameter by name, and verifies it matches the UUID in the current
 * {@link TenantContext}. If there is a mismatch the call is rejected with
 * {@link AccessDeniedException} before any business logic runs.
 *
 * <p>This replaces the error-prone pattern of manually calling
 * {@code requireSameOrganisation(organisationId)} inside every controller method.
 * Forgetting the annotation is now a PR review issue rather than a silent
 * cross-tenant leak.
 */
@Aspect
@Component
public class TenantEnforcementAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantEnforcementAspect.class);

    @Around("@annotation(enforceTenant)")
    public Object enforce(ProceedingJoinPoint pjp, EnforceTenant enforceTenant) throws Throwable {
        UUID requestedOrgId = extractOrgId(pjp, enforceTenant.param());

        if (requestedOrgId != null) {
            UUID currentOrgId = TenantContext.getOrganisationId();

            if (currentOrgId == null) {
                log.warn("[TENANT] EnforceTenant: no tenant context on thread for {}",
                        pjp.getSignature().toShortString());
                throw new AccessDeniedException("Tenant context is required");
            }

            if (!currentOrgId.equals(requestedOrgId)) {
                log.warn("[TENANT] Cross-tenant access attempt: requested={} current={} method={}",
                        requestedOrgId, currentOrgId, pjp.getSignature().toShortString());
                throw new AccessDeniedException("organisationId does not match current tenant");
            }
        }

        return pjp.proceed();
    }

    /**
     * Reflects on the method parameters to find the one matching {@code paramName}
     * and returns its UUID value, or {@code null} if not found.
     */
    private static UUID extractOrgId(ProceedingJoinPoint pjp, String paramName) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = pjp.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (paramName.equals(parameters[i].getName()) && args[i] instanceof UUID) {
                return (UUID) args[i];
            }
        }
        return null;
    }
}
