package com.example.demo.services;

import com.example.demo.dto.PredictiveInsightDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PredictiveMaintenanceService {

    /**
     * Analyse all assets in the current tenant and generate / refresh
     * predictive insights. Safe to call repeatedly — existing unresolved
     * insights for the same asset+type are replaced.
     */
    List<PredictiveInsightDto> generateInsights();

    /** List insights, optionally filtered by type and/or severity */
    List<PredictiveInsightDto> getInsights(String type, String severity, boolean unresolvedOnly);

    PredictiveInsightDto getById(UUID id);

    /** Mark an insight as resolved */
    void resolve(UUID id);

    /** Dashboard summary: counts by severity, type, top risky assets */
    Map<String, Object> getSummary();
}
