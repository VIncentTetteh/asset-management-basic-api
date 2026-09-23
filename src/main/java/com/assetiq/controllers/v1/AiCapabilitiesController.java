package com.assetiq.controllers.v1;

import com.assetiq.services.FeatureFlagService;
import com.assetiq.services.ai.AiDataSection;
import com.assetiq.services.ai.AiRateLimiter;
import com.assetiq.services.ai.LlmClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tells a client whether the assistant is usable before it renders a chat box.
 *
 * <p>Deliberately NOT behind {@code @FeatureFlagGate}: the gate answers 404 to
 * keep a disabled feature's existence hidden, which is right for the feature
 * endpoints and useless for a client trying to decide whether to show a nav
 * entry. Without this, a client can only distinguish "feature off", "no
 * permission" and "route missing" by guessing at a 404 — which is exactly how
 * the assistant came to be invisible with no error anyone could act on.
 *
 * <p>This endpoint reveals only whether the caller's own tenant has the feature
 * and what the caller may read. It exposes no other tenant's state, no key, and
 * no provider URL.
 */
@RestController
@RequestMapping("/api/v1/ai")
@PreAuthorize("isAuthenticated()")
public class AiCapabilitiesController {

    /** Flag key gating every AI surface. Must match {@code AIInsightsController}. */
    static final String AI_FLAG = "commercial.governed-ai";

    private static final Set<String> CHAT_AUTHORITIES =
            Set.of("ROLE_ORG_ADMIN", "ROLE_ADMIN", "ROLE_USER", "USE_AI_ASSISTANT");

    private final FeatureFlagService featureFlags;
    private final LlmClient          llm;
    private final AiRateLimiter      rateLimiter;

    public AiCapabilitiesController(FeatureFlagService featureFlags,
                                    LlmClient llm,
                                    AiRateLimiter rateLimiter) {
        this.featureFlags = featureFlags;
        this.llm          = llm;
        this.rateLimiter  = rateLimiter;
    }

    /**
     * GET /api/v1/ai/capabilities
     *
     * <pre>
     * {
     *   "enabled": true,          // feature flag on for this tenant
     *   "configured": true,       // a provider key is present in this environment
     *   "available": true,        // enabled && configured
     *   "canChat": true,          // caller may call POST /chat
     *   "provider": "groq",
     *   "scope":    ["ASSETS","MAINTENANCE"],   // what the caller may be told about
     *   "withheld": ["COMPLIANCE","RISKS"],     // what the caller may not
     *   "limits": { ... }
     * }
     * </pre>
     */
    @GetMapping("/capabilities")
    public ResponseEntity<Map<String, Object>> capabilities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean enabled    = featureFlags.isEnabled(AI_FLAG);
        boolean configured = llm.isConfigured();
        Set<AiDataSection> granted = AiDataSection.grantedTo(authentication);

        boolean holdsChatAuthority = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(CHAT_AUTHORITIES::contains);

        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("userPerMinute",        rateLimiter.userPerMinute());
        limits.put("userPerHour",          rateLimiter.userPerHour());
        limits.put("organisationPerHour",  rateLimiter.organisationPerHour());
        limits.put("maxMessageChars",      2000);
        limits.put("maxHistoryTurns",      12);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled",    enabled);
        body.put("configured", configured);
        body.put("available",  enabled && configured);
        body.put("canChat",    enabled && configured && holdsChatAuthority && !granted.isEmpty());
        body.put("provider",   llm.provider());
        body.put("scope",      granted.stream().map(Enum::name).sorted().collect(Collectors.toList()));
        body.put("withheld",   java.util.Arrays.stream(AiDataSection.values())
                                    .filter(s -> !granted.contains(s))
                                    .map(Enum::name).sorted().collect(Collectors.toList()));
        body.put("limits",     limits);
        return ResponseEntity.ok(body);
    }
}
