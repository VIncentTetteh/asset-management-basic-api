package com.example.demo.dto;

import lombok.Data;

@Data
public class ReportHistoryItemDto {
    private String reportId;
    private String reportType;
    private String format;
    private String filename;
    private String contentType;
    private String generatedAt;
    private String downloadUrl;
}

