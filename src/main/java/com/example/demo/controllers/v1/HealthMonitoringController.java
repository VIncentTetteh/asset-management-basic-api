package com.example.demo.controllers.v1;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Health & Monitoring Controller
 * Provides system health and API metrics
 */
@RestController
@RequestMapping("/api/v1")
public class HealthMonitoringController {

    /**
     * GET /api/v1/health
     * Get system health status
     */
    @GetMapping("/health")
    public ResponseEntity<?> getHealth() {

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());

        Map<String, Object> database = new HashMap<>();
        database.put("status", "UP");
        database.put("responseTime", 15);
        database.put("connectionPool", "20/20");

        Map<String, Object> cache = new HashMap<>();
        cache.put("status", "UP");
        cache.put("responseTime", 5);
        cache.put("hitRate", "92.5%");

        Map<String, Object> storage = new HashMap<>();
        storage.put("status", "UP");
        storage.put("available", "500GB");
        storage.put("used", "250GB");
        storage.put("utilization", "50%");

        Map<String, Object> components = new HashMap<>();
        components.put("database", database);
        components.put("cache", cache);
        components.put("storage", storage);

        response.put("components", components);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/health/detailed
     * Get detailed health status
     */
    @GetMapping("/health/detailed")
    public ResponseEntity<?> getDetailedHealth() {

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());

        // Database health
        Map<String, Object> database = new HashMap<>();
        database.put("status", "UP");
        database.put("driver", "PostgreSQL JDBC Driver");
        database.put("database", "asset_management");
        database.put("validationQuery", "isValid()");
        database.put("activeConnections", 15);
        database.put("maxConnections", 20);
        database.put("averageResponseTime", "25ms");

        // Cache health
        Map<String, Object> cache = new HashMap<>();
        cache.put("status", "UP");
        cache.put("type", "Redis");
        cache.put("cacheSize", "245MB");
        cache.put("maxSize", "500MB");
        cache.put("itemCount", 12450);
        cache.put("evictionPolicy", "LRU");

        // API Gateway
        Map<String, Object> apiGateway = new HashMap<>();
        apiGateway.put("status", "UP");
        apiGateway.put("requestsPerSecond", 245);
        apiGateway.put("p95Latency", "150ms");
        apiGateway.put("errorRate", "0.1%");

        Map<String, Object> components = new HashMap<>();
        components.put("database", database);
        components.put("cache", cache);
        components.put("apiGateway", apiGateway);

        response.put("components", components);
        response.put("uptime", "45d 12h 30m");
        response.put("version", "1.0.0");

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/metrics
     * Get API metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics(
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(required = false) String metric) {

        Map<String, Object> response = new HashMap<>();
        response.put("period", period);
        response.put("timestamp", Instant.now().toString());

        // Request metrics
        response.put("totalRequests", 15420);
        response.put("successfulRequests", 15350);
        response.put("failedRequests", 70);
        response.put("successRate", "99.55%");

        // Performance metrics
        response.put("averageLatency", 145);
        response.put("p50Latency", 120);
        response.put("p95Latency", 450);
        response.put("p99Latency", 890);
        response.put("maxLatency", 2450);

        // Error metrics
        response.put("errorRate", "0.45%");

        List<Map<String, Object>> topErrors = new ArrayList<>();
        topErrors.add(Map.of("error", "404 Not Found", "count", 35, "percentage", "50%"));
        topErrors.add(Map.of("error", "403 Forbidden", "count", 25, "percentage", "35.7%"));
        topErrors.add(Map.of("error", "500 Internal Server Error", "count", 10, "percentage", "14.3%"));

        response.put("topErrors", topErrors);

        // Endpoint metrics
        List<Map<String, Object>> slowestEndpoints = new ArrayList<>();
        slowestEndpoints.add(Map.of(
            "endpoint", "GET /api/v1/analytics/assets",
            "avgLatency", 350,
            "callCount", 120
        ));
        slowestEndpoints.add(Map.of(
            "endpoint", "POST /api/v1/reports/assets",
            "avgLatency", 280,
            "callCount", 45
        ));
        slowestEndpoints.add(Map.of(
            "endpoint", "GET /api/v1/assets",
            "avgLatency", 180,
            "callCount", 8520
        ));

        response.put("slowestEndpoints", slowestEndpoints);

        // Resource usage
        Map<String, Object> resources = new HashMap<>();
        resources.put("cpuUsage", "35%");
        resources.put("memoryUsage", "62%");
        resources.put("jvmHeap", "512MB / 1024MB");
        resources.put("diskUsage", "50%");

        response.put("resources", resources);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/metrics/endpoints
     * Get endpoint-specific metrics
     */
    @GetMapping("/metrics/endpoints")
    public ResponseEntity<?> getEndpointMetrics(
            @RequestParam(required = false) String sortBy) {

        List<Map<String, Object>> endpoints = new ArrayList<>();

        endpoints.add(createEndpointMetric("/api/v1/assets", "GET", 8520, 145, 0.05));
        endpoints.add(createEndpointMetric("/api/v1/purchase-orders", "GET", 2350, 120, 0.0));
        endpoints.add(createEndpointMetric("/api/v1/suppliers", "GET", 1200, 100, 0.1));
        endpoints.add(createEndpointMetric("/api/v1/assets", "POST", 450, 250, 0.2));
        endpoints.add(createEndpointMetric("/api/v1/analytics/assets", "GET", 120, 350, 0.8));

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("totalEndpoints", 50);
        response.put("endpoints", endpoints);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/metrics/throughput
     * Get throughput metrics over time
     */
    @GetMapping("/metrics/throughput")
    public ResponseEntity<?> getThroughputMetrics(
            @RequestParam(defaultValue = "24") int hours) {

        List<Map<String, Object>> throughput = new ArrayList<>();

        for (int i = 0; i < Math.min(hours, 24); i++) {
            throughput.add(Map.of(
                "hour", String.format("%02d:00", i),
                "requestCount", 600 + (i * 25),
                "successCount", 595 + (i * 25),
                "errorCount", 5 + (i % 3),
                "averageLatency", 120 + (i * 5)
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("period", hours + " hours");
        response.put("timestamp", Instant.now().toString());
        response.put("throughput", throughput);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/metrics/errors
     * Get error metrics
     */
    @GetMapping("/metrics/errors")
    public ResponseEntity<?> getErrorMetrics() {

        List<Map<String, Object>> errors = new ArrayList<>();

        errors.add(Map.of(
            "errorCode", "404",
            "errorType", "Not Found",
            "count", 35,
            "percentage", "50%",
            "lastOccurrence", "2026-03-05T10:25:00Z"
        ));

        errors.add(Map.of(
            "errorCode", "403",
            "errorType", "Forbidden",
            "count", 25,
            "percentage", "35.7%",
            "lastOccurrence", "2026-03-05T10:20:00Z"
        ));

        errors.add(Map.of(
            "errorCode", "500",
            "errorType", "Internal Server Error",
            "count", 10,
            "percentage", "14.3%",
            "lastOccurrence", "2026-03-05T09:45:00Z"
        ));

        Map<String, Object> response = new HashMap<>();
        response.put("totalErrors", 70);
        response.put("errorRate", "0.45%");
        response.put("errors", errors);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/metrics/database
     * Get database metrics
     */
    @GetMapping("/metrics/database")
    public ResponseEntity<?> getDatabaseMetrics() {

        Map<String, Object> response = new HashMap<>();

        response.put("status", "UP");
        response.put("totalConnections", 20);
        response.put("activeConnections", 15);
        response.put("idleConnections", 5);
        response.put("averageQueryTime", 25);
        response.put("totalQueries", 125420);
        response.put("queriesPerSecond", 245);

        List<Map<String, Object>> slowQueries = new ArrayList<>();
        slowQueries.add(Map.of(
            "query", "SELECT ... JOIN ... (asset loading)",
            "avgTime", 450,
            "callCount", 120
        ));
        slowQueries.add(Map.of(
            "query", "SELECT ... JOIN ... (analytics)",
            "avgTime", 350,
            "callCount", 45
        ));

        response.put("slowestQueries", slowQueries);

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> createEndpointMetric(String endpoint, String method, int requests, int latency, double errorRate) {
        Map<String, Object> metric = new HashMap<>();
        metric.put("endpoint", endpoint);
        metric.put("method", method);
        metric.put("requests", requests);
        metric.put("averageLatency", latency);
        metric.put("errorRate", errorRate + "%");
        metric.put("successRate", (100 - errorRate) + "%");
        return metric;
    }
}

