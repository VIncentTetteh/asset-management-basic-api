package com.assetiq.dto;

import com.assetiq.enums.InsightSeverity;
import com.assetiq.enums.InsightType;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class PredictiveInsightDto {

    private UUID id;
    private UUID assetId;
    private String assetName;
    private String assetTag;
    private InsightType insightType;
    private InsightSeverity severity;
    private String title;
    private String description;
    private double confidence;
    private LocalDate predictedDate;
    private boolean resolved;
    private Instant resolvedAt;
    private Instant createdAt;
}
