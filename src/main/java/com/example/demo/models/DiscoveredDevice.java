package com.example.demo.models;

import com.example.demo.enums.DeviceStatus;
import com.example.demo.enums.DiscoveryMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "discovered_device",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ip_address", "organisation_id"}))
public class DiscoveredDevice extends BaseEntity {

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(length = 255)
    private String hostname;

    @Column(name = "mac_address", length = 17)
    private String macAddress;

    @Column(name = "device_type", length = 100)
    private String deviceType;

    /** Comma-separated list of open port numbers */
    @Column(name = "open_ports", length = 500)
    private String openPorts;

    @Enumerated(EnumType.STRING)
    @Column(name = "discovery_method", nullable = false, length = 20)
    private DiscoveryMethod discoveryMethod = DiscoveryMethod.PING;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeviceStatus status = DeviceStatus.UNKNOWN;

    @Column(name = "os_hint", length = 200)
    private String osHint;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    /** Set when this device is promoted to a managed Asset */
    @Column(name = "promoted_asset_id")
    private UUID promotedAssetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
}
