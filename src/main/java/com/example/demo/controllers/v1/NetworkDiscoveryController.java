package com.example.demo.controllers.v1;

import com.example.demo.dto.DiscoveredDeviceDto;
import com.example.demo.dto.NetworkScanRequestDto;
import com.example.demo.services.NetworkDiscoveryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IT Asset Discovery — network scanning and device management.
 * Base path: /api/v1/discovery
 */
@RestController
@RequestMapping("/api/v1/discovery")
@PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
public class NetworkDiscoveryController {

    private final NetworkDiscoveryService discoveryService;

    public NetworkDiscoveryController(NetworkDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /**
     * POST /api/v1/discovery/scan
     * Trigger a network scan (ping sweep + optional port scan).
     * Body: { cidrRange, ipAddresses, portScan, ports, timeoutMs }
     */
    @PostMapping("/scan")
    public ResponseEntity<List<DiscoveredDeviceDto>> scan(@RequestBody NetworkScanRequestDto request) {
        return ResponseEntity.ok(discoveryService.scan(request));
    }

    /**
     * GET /api/v1/discovery/devices
     * List all discovered devices for the current tenant.
     */
    @GetMapping("/devices")
    public ResponseEntity<Page<DiscoveredDeviceDto>> list(
            @PageableDefault(size = 20, sort = "lastSeenAt") Pageable pageable) {
        return ResponseEntity.ok(discoveryService.list(pageable));
    }

    /**
     * GET /api/v1/discovery/devices/{id}
     */
    @GetMapping("/devices/{id}")
    public ResponseEntity<DiscoveredDeviceDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(discoveryService.getById(id));
    }

    /**
     * POST /api/v1/discovery/devices/{id}/promote
     * Promote a discovered device to a managed Asset.
     */
    @PostMapping("/devices/{id}/promote")
    public ResponseEntity<Map<String, Object>> promote(@PathVariable UUID id) {
        return ResponseEntity.ok(discoveryService.promote(id));
    }

    /**
     * DELETE /api/v1/discovery/devices/{id}
     */
    @DeleteMapping("/devices/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        discoveryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/discovery/summary
     * Stats: total, online, offline, promoted.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(discoveryService.summary());
    }
}
