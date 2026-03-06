package com.example.demo.config;

import com.example.demo.models.AuditEvent;
import com.example.demo.models.Organisation;
import com.example.demo.models.User;
import com.example.demo.multitenancy.TenantContext;
import com.example.demo.repositories.AuditEventRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.repositories.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class ApiAuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiAuditInterceptor.class);
    private static final Set<String> SKIP_PREFIXES = Set.of(
            "/swagger-ui", "/v3/api-docs", "/actuator", "/error");

    private final AuditEventRepository auditEventRepository;
    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;

    public ApiAuditInterceptor(
            AuditEventRepository auditEventRepository,
            OrganisationRepository organisationRepository,
            UserRepository userRepository) {
        this.auditEventRepository = auditEventRepository;
        this.organisationRepository = organisationRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        String path = request.getRequestURI();
        if (shouldSkip(path)) {
            return;
        }

        try {
            Organisation org = resolveOrganisation();
            String actorEmail = resolveActorEmail();
            User actor = resolveActor(org, actorEmail);

            AuditEvent event = new AuditEvent();
            event.setOrganisation(org);
            event.setActor(actor);
            event.setActorEmail(actorEmail);
            event.setMethod(request.getMethod());
            event.setPath(path);
            event.setQuery(request.getQueryString());
            event.setHandler(resolveHandler(handler));
            event.setResponseStatus(response.getStatus());
            event.setSuccess(ex == null && response.getStatus() < 400);
            event.setMessage(ex != null ? abbreviate(ex.getMessage(), 500) : null);
            event.setRequestId(response.getHeader(RequestCorrelationIdInterceptor.REQUEST_ID_HEADER));
            event.setClientIp(clientIp(request));
            event.setUserAgent(abbreviate(request.getHeader("User-Agent"), 500));

            auditEventRepository.save(event);
        } catch (Exception saveEx) {
            log.warn("Failed to persist audit event for path {}: {}", path, saveEx.getMessage());
        }
    }

    private boolean shouldSkip(String path) {
        if (path == null) {
            return true;
        }
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Organisation resolveOrganisation() {
        if (!TenantContext.hasOrganisationId()) {
            return null;
        }
        UUID orgId = TenantContext.getOrganisationId();
        return organisationRepository.findByIdAndDeletedAtIsNull(orgId).orElse(null);
    }

    private String resolveActorEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getName();
    }

    private User resolveActor(Organisation org, String actorEmail) {
        if (actorEmail == null) {
            return null;
        }
        if (org != null) {
            Optional<User> byOrg = userRepository.findByEmailAndOrganisationId(actorEmail, org.getId());
            if (byOrg.isPresent()) {
                return byOrg.get();
            }
        }
        return userRepository.findByEmail(actorEmail).orElse(null);
    }

    private String resolveHandler(Object handler) {
        if (handler instanceof HandlerMethod hm) {
            return hm.getBeanType().getSimpleName() + "." + hm.getMethod().getName();
        }
        return handler != null ? handler.getClass().getSimpleName() : null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String abbreviate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}

