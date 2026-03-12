package com.example.demo.controllers.v1;

import com.example.demo.dto.PredictiveInsightDto;
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

    public AIInsightsController(PredictiveMaintenanceService predictiveService) {
        this.predictiveService = predictiveService;
    }

    /**
     * POST /api/v1/ai/insights/generate
     * Re-analyse all assets and refresh predictive insights.
     * Returns the full list of newly generated insights.
     */
    @PostMapping("/insights/generate")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<List<PredictiveInsightDto>> generate() {
        return ResponseEntity.ok(predictiveService.generateInsights());
    }

    /**
     * GET /api/v1/ai/insights?type=MAINTENANCE_DUE&severity=HIGH&unresolvedOnly=true
     * List insights with optional filters.
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
     * Mark an insight as resolved (acknowledged / acted upon).
     */
    @PostMapping("/insights/{id}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable UUID id) {
        predictiveService.resolve(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/ai/insights/summary
     * Dashboard summary: total unresolved, counts by severity.
     */
    @GetMapping("/insights/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(predictiveService.getSummary());
    }
}
