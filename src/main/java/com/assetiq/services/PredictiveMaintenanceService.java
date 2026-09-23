package com.assetiq.services;

import com.assetiq.dto.PredictiveInsightDto;

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

    /**
     * Regenerate insights for the current tenant, examining at most
     * {@code maxAssets} assets, and return how many insights were written.
     *
     * <p>Used by the nightly refresh, which needs a bound (one large tenant must
     * not starve the rest of the run) and a count rather than a page of DTOs.
     * Identical analysis to {@link #generateInsights()}.
     *
     * @param maxAssets upper bound on assets examined; the oldest-updated assets
     *                  are examined first so a tenant over the bound still gets
     *                  its least-recently-analysed assets refreshed
     */
    int refreshInsights(int maxAssets);

    /** List insights, optionally filtered by type and/or severity */
    List<PredictiveInsightDto> getInsights(String type, String severity, boolean unresolvedOnly);

    PredictiveInsightDto getById(UUID id);

    /** Mark an insight as resolved */
    void resolve(UUID id);

    /** Dashboard summary: counts by severity, type, top risky assets */
    Map<String, Object> getSummary();
}
