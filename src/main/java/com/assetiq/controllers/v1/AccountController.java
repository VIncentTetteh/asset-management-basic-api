package com.assetiq.controllers.v1;

import com.assetiq.services.AccountLifecycleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Tenant self-service for the two things every customer is entitled to: taking their
 * data with them, and leaving.
 *
 * <p>Both are restricted to organisation administrators. Exporting hands over every
 * record the tenant owns and closing destroys them, so neither belongs to an ordinary
 * user account.
 */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountLifecycleService accountLifecycleService;
    private final ObjectMapper objectMapper;

    /**
     * Download everything this tenant owns as a single JSON document.
     *
     * <p>Streamed straight to the caller rather than parked in object storage: an
     * export is the most sensitive artefact the system produces, and not persisting a
     * copy means there is no stale bundle to leak later.
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<byte[]> exportData() throws Exception {
        Map<String, Object> export = accountLifecycleService.exportTenantData();

        byte[] body = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(export);

        String filename = "assetiq-export-" + Instant.now().toString().substring(0, 10) + ".json";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length)
                .body(body);
    }

    /**
     * Close this tenant's account.
     *
     * <p>Requires {@code confirm: "DELETE"} in the body. A destructive, effectively
     * irreversible action should not be one malformed client request away from firing,
     * and an explicit confirmation phrase is the cheapest guard that actually works.
     */
    @PostMapping("/delete")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<?> closeAccount(@RequestBody(required = false) Map<String, String> body) {
        String confirm = body == null ? null : body.get("confirm");
        if (!"DELETE".equals(confirm)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Account closure must be confirmed by sending {\"confirm\":\"DELETE\"}."));
        }

        Instant purgeAfter = accountLifecycleService.requestClosure(
                body == null ? null : body.get("reason"));

        return ResponseEntity.ok(Map.of(
                "message", "Your account is closed. Access has ended immediately.",
                "purgeAfter", purgeAfter.toString(),
                "recoverable", true,
                "recoveryInstructions",
                "Contact support before the purge date to restore this account."));
    }
}
