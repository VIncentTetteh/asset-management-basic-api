package com.assetiq.controllers.v1;

import com.assetiq.config.AppModeProperties;
import com.assetiq.license.LicenseService;
import com.assetiq.license.LicenseState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * License management endpoints.
 *
 * <p>Both endpoints are available in both cloud and standalone mode:</p>
 * <ul>
 *   <li>{@code GET /api/v1/license/status} — returns {@code { "mode": "cloud" }} in
 *       cloud mode (no license service loaded); returns full {@link LicenseState} in
 *       standalone mode.</li>
 *   <li>{@code POST /api/v1/license/activate} — no-op in cloud mode (returns 200 with
 *       mode=cloud); activates a new key in standalone mode.</li>
 * </ul>
 *
 * This keeps the frontend simple — it can always call the same endpoints regardless
 * of mode; the response content differs.
 */
@RestController
@RequestMapping("/api/v1/license")
public class LicenseController {

    private final AppModeProperties appMode;

    /**
     * Optional — only present when APP_MODE=standalone.
     * In cloud mode this is empty and both endpoints return a cloud-mode stub.
     */
    private final Optional<LicenseService> licenseService;

    public LicenseController(AppModeProperties appMode,
                             Optional<LicenseService> licenseService) {
        this.appMode        = appMode;
        this.licenseService = licenseService;
    }

    /**
     * Returns the current license state.
     * Frontend polls this every 5 minutes in standalone mode.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        if (appMode.isCloud()) {
            return ResponseEntity.ok(Map.of("mode", "cloud"));
        }
        LicenseState state = licenseService
            .orElseThrow(() -> new IllegalStateException("LicenseService not available"))
            .getCurrentState();

        return ResponseEntity.ok(Map.ofEntries(
            Map.entry("mode", "standalone"),
            Map.entry("status", state.status()),
            Map.entry("readOnly", state.readOnly()),
            Map.entry("plan", state.plan() != null ? state.plan() : ""),
            Map.entry("expiresAt", state.expiresAt() != null ? state.expiresAt().toString() : ""),
            Map.entry("daysRemaining", state.daysRemaining()),
            Map.entry("gracePeriodDays", state.gracePeriodDays()),
            Map.entry("limits", state.limits() != null ? state.limits() : Map.of()),
            Map.entry("features", state.features() != null ? state.features() : Map.of()),
            Map.entry(
                "lastRemoteValidationAt",
                state.lastRemoteValidationAt() != null ? state.lastRemoteValidationAt().toString() : ""
            ),
            Map.entry("message", state.message() != null ? state.message() : "")
        ));
    }

    /**
     * Activates a new license key entered by an admin.
     * Admin-only — requires MANAGE_ORGANIZATION_SETTINGS permission.
     */
    @PostMapping("/activate")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<Map<String, Object>> activate(
            @Valid @RequestBody ActivateKeyRequest req) {

        if (appMode.isCloud()) {
            return ResponseEntity.ok(Map.of(
                "mode",    "cloud",
                "message", "License activation is not required in cloud mode."
            ));
        }
        LicenseState state = licenseService
            .orElseThrow(() -> new IllegalStateException("LicenseService not available"))
            .activateKey(req.key());

        return ResponseEntity.ok(Map.of(
            "mode",          "standalone",
            "status",        state.status(),
            "readOnly",      state.readOnly(),
            "plan",          state.plan() != null ? state.plan() : "",
            "daysRemaining", state.daysRemaining(),
            "message",       state.message() != null ? state.message() : "License activated successfully."
        ));
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    public record ActivateKeyRequest(
        @NotBlank(message = "key is required") String key
    ) {}
}
