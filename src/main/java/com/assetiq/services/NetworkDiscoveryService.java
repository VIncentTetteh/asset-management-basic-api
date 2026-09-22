package com.assetiq.services;

import com.assetiq.dto.DiscoveredDeviceDto;
import com.assetiq.dto.NetworkScanRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NetworkDiscoveryService {

    /** Run a network scan (ping sweep + optional port scan) and persist results */
    List<DiscoveredDeviceDto> scan(NetworkScanRequestDto request);

    /** Discovered devices for the current tenant, optionally only those with {@code status}. */
    Page<DiscoveredDeviceDto> list(Pageable pageable, com.assetiq.enums.DeviceStatus status);

    /** Get a single discovered device by ID */
    DiscoveredDeviceDto getById(UUID id);

    /** Promote a discovered device to a managed Asset (name, category and location optional). */
    Map<String, Object> promote(UUID deviceId, com.assetiq.dto.PromoteDeviceRequest request);

    /** Soft-delete a discovered device record */
    void delete(UUID id);

    /** Summary stats: total, online, offline, promoted, unknown */
    Map<String, Object> summary();
}
