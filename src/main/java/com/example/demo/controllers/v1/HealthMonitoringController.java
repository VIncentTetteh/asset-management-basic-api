package com.example.demo.controllers.v1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;

/**
 * Health & Monitoring Controller.
 * Reports real JVM and DB health; no hardcoded values.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthMonitoringController {

    private static final Logger log = LoggerFactory.getLogger(HealthMonitoringController.class);
    private static final long START_TIME = System.currentTimeMillis();

    private final DataSource dataSource;

    public HealthMonitoringController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** GET /api/v1/health */
    @GetMapping("/health")
    public ResponseEntity<?> getHealth() {
        Map<String, Object> db = dbHealth(false);
        Map<String, Object> jvm = jvmSummary();

        String overallStatus = "UP".equals(db.get("status")) ? "UP" : "DEGRADED";

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", overallStatus);
        response.put("timestamp", Instant.now().toString());
        response.put("uptime", uptimeString());

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("database", db);
        components.put("jvm", jvm);
        response.put("components", components);

        return ResponseEntity.ok(response);
    }

    /** GET /api/v1/health/detailed */
    @GetMapping("/health/detailed")
    public ResponseEntity<?> getDetailedHealth() {
        Map<String, Object> db = dbHealth(true);
        Map<String, Object> jvm = jvmDetailed();

        String overallStatus = "UP".equals(db.get("status")) ? "UP" : "DEGRADED";

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", overallStatus);
        response.put("timestamp", Instant.now().toString());
        response.put("uptime", uptimeString());
        response.put("version", "2.0.0");

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("database", db);
        components.put("jvm", jvm);
        response.put("components", components);

        return ResponseEntity.ok(response);
    }

    /** GET /api/v1/metrics */
    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics(
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(required = false) String metric) {

        Runtime rt = Runtime.getRuntime();
        long totalMem = rt.totalMemory();
        long freeMem  = rt.freeMemory();
        long usedMem  = totalMem - freeMem;
        long maxMem   = rt.maxMemory();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("period", period);
        response.put("timestamp", Instant.now().toString());

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedMb", mb(usedMem));
        jvm.put("heapTotalMb", mb(totalMem));
        jvm.put("heapMaxMb", mb(maxMem));
        jvm.put("heapUtilizationPct", pct(usedMem, maxMem));
        jvm.put("availableProcessors", rt.availableProcessors());
        jvm.put("threadCount", Thread.activeCount());
        response.put("jvm", jvm);

        response.put("uptime", uptimeString());
        response.put("uptimeMs", System.currentTimeMillis() - START_TIME);

        return ResponseEntity.ok(response);
    }

    /** GET /api/v1/metrics/endpoints — static summary (actuator integration future work) */
    @GetMapping("/metrics/endpoints")
    public ResponseEntity<?> getEndpointMetrics(@RequestParam(required = false) String sortBy) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("note", "Per-endpoint metrics require Micrometer/Actuator integration.");
        return ResponseEntity.ok(response);
    }

    /** GET /api/v1/metrics/throughput */
    @GetMapping("/metrics/throughput")
    public ResponseEntity<?> getThroughputMetrics(@RequestParam(defaultValue = "24") int hours) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("uptimeMs", System.currentTimeMillis() - START_TIME);
        response.put("note", "Detailed throughput metrics require Micrometer integration.");
        return ResponseEntity.ok(response);
    }

    /** GET /api/v1/metrics/errors */
    @GetMapping("/metrics/errors")
    public ResponseEntity<?> getErrorMetrics() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("note", "Error metrics require Micrometer integration.");
        return ResponseEntity.ok(response);
    }

    /** GET /api/v1/metrics/database */
    @GetMapping("/metrics/database")
    public ResponseEntity<?> getDatabaseMetrics() {
        return ResponseEntity.ok(dbHealth(true));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> dbHealth(boolean detailed) {
        Map<String, Object> db = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(3);
            long elapsed = System.currentTimeMillis() - start;
            db.put("status", valid ? "UP" : "DOWN");
            db.put("responseTimeMs", elapsed);
            if (detailed) {
                db.put("driverName", conn.getMetaData().getDriverName());
                db.put("driverVersion", conn.getMetaData().getDriverVersion());
                db.put("databaseProductName", conn.getMetaData().getDatabaseProductName());
                db.put("databaseProductVersion", conn.getMetaData().getDatabaseProductVersion());
                db.put("url", conn.getMetaData().getURL());
            }
        } catch (Exception e) {
            log.warn("DB health check failed: {}", e.getMessage());
            db.put("status", "DOWN");
            db.put("error", e.getMessage());
        }
        return db;
    }

    private Map<String, Object> jvmSummary() {
        Runtime rt = Runtime.getRuntime();
        long usedMem = rt.totalMemory() - rt.freeMemory();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedMb", mb(usedMem));
        jvm.put("heapMaxMb", mb(rt.maxMemory()));
        jvm.put("heapUtilizationPct", pct(usedMem, rt.maxMemory()));
        jvm.put("threadCount", Thread.activeCount());
        return jvm;
    }

    private Map<String, Object> jvmDetailed() {
        Runtime rt = Runtime.getRuntime();
        long totalMem = rt.totalMemory();
        long freeMem  = rt.freeMemory();
        long usedMem  = totalMem - freeMem;
        long maxMem   = rt.maxMemory();

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedMb", mb(usedMem));
        jvm.put("heapFreeMb", mb(freeMem));
        jvm.put("heapTotalMb", mb(totalMem));
        jvm.put("heapMaxMb", mb(maxMem));
        jvm.put("heapUtilizationPct", pct(usedMem, maxMem));
        jvm.put("availableProcessors", rt.availableProcessors());
        jvm.put("threadCount", Thread.activeCount());
        jvm.put("javaVersion", System.getProperty("java.version"));
        jvm.put("javaVendor", System.getProperty("java.vendor"));
        jvm.put("osName", System.getProperty("os.name"));
        jvm.put("osArch", System.getProperty("os.arch"));
        return jvm;
    }

    private static long mb(long bytes) {
        return bytes / (1024 * 1024);
    }

    private static double pct(long used, long max) {
        if (max == 0) return 0.0;
        return Math.round((used * 1000.0 / max)) / 10.0;
    }

    private static String uptimeString() {
        long ms = System.currentTimeMillis() - START_TIME;
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours   = minutes / 60;
        long days    = hours / 24;
        return String.format("%dd %dh %dm %ds", days, hours % 24, minutes % 60, seconds % 60);
    }
}
