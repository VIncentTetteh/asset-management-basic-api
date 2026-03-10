package com.example.demo.services;

import com.example.demo.models.Organisation;

import java.util.Map;

public interface AnalyticsService {
    Map<String, Object> getAssetAnalytics(String period, String groupBy, Organisation org);
    Map<String, Object> getFinancialAnalytics(String period, Organisation org);
    Map<String, Object> getPurchaseOrderAnalytics(String period, Organisation org);
    Map<String, Object> getMaintenanceAnalytics(Organisation org);
    Map<String, Object> getDepreciationTrends(int months, Organisation org);
}
