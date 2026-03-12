package com.example.demo.services;

import com.example.demo.dto.DiscoveredDeviceDto;
import com.example.demo.dto.NetworkScanRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NetworkDiscoveryService {

    /** Run a network scan (ping sweep + optional port scan) and persist results */
    List<DiscoveredDeviceDto> scan(NetworkScanRequestDto request);

    /** List all discovered devices for the current tenant */
    Page<DiscoveredDeviceDto> list(Pageable pageable);

    /** Get a single discovered device by ID */
    DiscoveredDeviceDto getById(UUID id);

    /** Promote a discovered device to a managed Asset */
    Map<String, Object> promote(UUID deviceId);

    /** Soft-delete a discovered device record */
    void delete(UUID id);

    /** Summary stats: total, online, offline, promoted */
    Map<String, Object> summary();
}
