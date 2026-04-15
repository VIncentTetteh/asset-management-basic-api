package com.assetiq.dto;

import com.assetiq.enums.DeviceStatus;
import com.assetiq.enums.DiscoveryMethod;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class DiscoveredDeviceDto {

    private UUID id;
    private String ipAddress;
    private String hostname;
    private String macAddress;
    private String deviceType;
    private List<Integer> openPorts;
    private DiscoveryMethod discoveryMethod;
    private DeviceStatus status;
    private String osHint;
    private Long responseTimeMs;
    private Instant lastSeenAt;
    private UUID promotedAssetId;
    private Instant createdAt;
}
