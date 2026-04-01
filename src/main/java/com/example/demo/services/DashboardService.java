package com.example.demo.services;

import com.example.demo.models.Organisation;
import java.util.Map;

public interface DashboardService {
    Map<String, Object> getSummary(Organisation org);
    Map<String, Object> getAssetsByStatus(Organisation org);
    Map<String, Object> getAssetsByDepartment(Organisation org);
    Map<String, Object> getMaintenanceAlerts(Organisation org);
    Map<String, Object> getDepreciationSummary(Organisation org);
}
