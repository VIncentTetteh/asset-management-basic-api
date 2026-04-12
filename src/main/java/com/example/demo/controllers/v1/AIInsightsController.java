package com.example.demo.controllers.v1;

import com.example.demo.dto.AiChatRequest;
import com.example.demo.dto.AiChatResponse;
import com.example.demo.dto.PredictiveInsightDto;
import com.example.demo.services.AiChatService;
import com.example.demo.services.PredictiveMaintenanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI / Predictive Intelligence endpoints.
 * Base path: /api/v1/ai
 */
@RestController
@RequestMapping("/api/v1/ai")
@PreAuthorize("isAuthenticated()")
public class AIInsightsController {

    private final PredictiveMaintenanceService predictiveService;
    private final AiChatService                aiChatService;

    public AIInsightsController(PredictiveMaintenanceService predictiveService,
                                AiChatService aiChatService) {
        this.predictiveService = predictiveService;
        this.aiChatService     = aiChatService;
    }

    // ── Conversational AI ────────────────────────────────────────────────────

    /**
     * POST /api/v1/ai/chat
     *
     * RAG-powered chat endpoint.  The server retrieves all live organisational
     * data (assets, maintenance, users, departments, budgets, AI insights) from
     * the database, injects it as a structured system prompt, and calls the
     * Anthropic Messages API to generate a grounded response.
     *
     * Clients send only the user message + recent conversation history.
     * No organisation data needs to be included in the request — the backend
     * owns retrieval, so web, mobile, and desktop all share the same logic.
     */
    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        return ResponseEntity.ok(aiChatService.chat(request));
    }

    // ── Predictive insights ───────────────────────────────────────────────────

    /**
     * POST /api/v1/ai/insights/generate
     * Re-analyse all assets and refresh predictive insights.
     */
    @PostMapping("/insights/generate")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','VIEW_MAINTENANCE','SCHEDULE_MAINTENANCE','CONDUCT_AUDIT','VIEW_REPORTS')")
    public ResponseEntity<List<PredictiveInsightDto>> generate() {
        return ResponseEntity.ok(predictiveService.generateInsights());
    }

    /**
     * GET /api/v1/ai/insights?type=MAINTENANCE_DUE&severity=HIGH&unresolvedOnly=true
     */
    @GetMapping("/insights")
    public ResponseEntity<List<PredictiveInsightDto>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "true") boolean unresolvedOnly) {
        return ResponseEntity.ok(predictiveService.getInsights(type, severity, unresolvedOnly));
    }

    /**
     * GET /api/v1/ai/insights/{id}
     */
    @GetMapping("/insights/{id}")
    public ResponseEntity<PredictiveInsightDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(predictiveService.getById(id));
    }

    /**
     * POST /api/v1/ai/insights/{id}/resolve
     */
    @PostMapping("/insights/{id}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable UUID id) {
        predictiveService.resolve(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/ai/insights/summary
     */
    @GetMapping("/insights/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(predictiveService.getSummary());
    }
}
