package com.example.demo.services.impl;

import com.example.demo.dto.DiscoveredDeviceDto;
import com.example.demo.dto.NetworkScanRequestDto;
import com.example.demo.enums.AssetStatus;
import com.example.demo.enums.AssetType;
import com.example.demo.enums.DeviceStatus;
import com.example.demo.enums.DiscoveryMethod;
import com.example.demo.models.Asset;
import com.example.demo.models.DiscoveredDevice;
import com.example.demo.models.Organisation;
import com.example.demo.repositories.AssetRepository;
import com.example.demo.repositories.DiscoveredDeviceRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.NetworkDiscoveryService;
import com.example.demo.services.TenantAwareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class NetworkDiscoveryServiceImpl extends TenantAwareService implements NetworkDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(NetworkDiscoveryServiceImpl.class);

    private static final List<Integer> DEFAULT_PORTS = List.of(22, 80, 443, 445, 3389, 8080, 8443, 3306, 5432, 27017);

    private final DiscoveredDeviceRepository deviceRepo;
    private final AssetRepository assetRepository;

    public NetworkDiscoveryServiceImpl(OrganisationRepository organisationRepository,
                                       DiscoveredDeviceRepository deviceRepo,
                                       AssetRepository assetRepository) {
        super(organisationRepository);
        this.deviceRepo = deviceRepo;
        this.assetRepository = assetRepository;
    }

    // ── Scan ─────────────────────────────────────────────────────────────────

    @Override
    public List<DiscoveredDeviceDto> scan(NetworkScanRequestDto request) {
        Organisation org = requireTenantOrg();
        List<String> ips = resolveIps(request);
        if (ips.isEmpty()) {
            return Collections.emptyList();
        }

        int timeoutMs = Math.min(Math.max(request.getTimeoutMs(), 100), 5000);
        List<Integer> ports = (request.getPorts() != null && !request.getPorts().isEmpty())
                ? request.getPorts() : DEFAULT_PORTS;

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(ips.size(), 50));
        List<Future<DiscoveredDevice>> futures = new ArrayList<>();

        for (String ip : ips) {
            futures.add(pool.submit(() -> probeHost(ip, timeoutMs, request.isPortScan(), ports, org)));
        }

        pool.shutdown();
        try {
            pool.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<DiscoveredDevice> results = new ArrayList<>();
        for (Future<DiscoveredDevice> f : futures) {
            try {
                DiscoveredDevice d = f.get();
                if (d != null) results.add(d);
            } catch (Exception e) {
                log.debug("Scan probe failed: {}", e.getMessage());
            }
        }

        return results.stream().map(this::toDto).collect(Collectors.toList());
    }

    private DiscoveredDevice probeHost(String ip, int timeoutMs, boolean portScan,
                                       List<Integer> ports, Organisation org) {
        long start = System.currentTimeMillis();
        boolean reachable;
        String hostname = ip;
        try {
            InetAddress addr = InetAddress.getByName(ip);
            reachable = addr.isReachable(timeoutMs);
            String resolved = addr.getCanonicalHostName();
            if (!resolved.equals(ip)) hostname = resolved;
        } catch (Exception e) {
            reachable = false;
        }
        long elapsed = System.currentTimeMillis() - start;

        // Upsert: find existing record or create new
        DiscoveredDevice device = deviceRepo
                .findByIpAddressAndOrganisationAndDeletedAtIsNull(ip, org)
                .orElse(new DiscoveredDevice());

        device.setIpAddress(ip);
        device.setHostname(hostname.equals(ip) ? null : hostname);
        device.setOrganisation(org);
        device.setStatus(reachable ? DeviceStatus.ONLINE : DeviceStatus.OFFLINE);
        device.setResponseTimeMs(reachable ? elapsed : null);
        device.setLastSeenAt(Instant.now());
        device.setDiscoveryMethod(portScan ? DiscoveryMethod.PORT_SCAN : DiscoveryMethod.PING);

        if (reachable && portScan) {
            List<Integer> open = scanPorts(ip, ports, timeoutMs);
            device.setOpenPorts(open.stream().map(String::valueOf).collect(Collectors.joining(",")));
            device.setDeviceType(inferDeviceType(open));
        }

        return deviceRepo.save(device);
    }

    private List<Integer> scanPorts(String ip, List<Integer> ports, int timeoutMs) {
        List<Integer> open = new ArrayList<>();
        for (int port : ports) {
            try (Socket s = new Socket()) {
                s.connect(new java.net.InetSocketAddress(ip, port), timeoutMs);
                open.add(port);
            } catch (Exception ignored) {
            }
        }
        return open;
    }

    private String inferDeviceType(List<Integer> openPorts) {
        if (openPorts.contains(3389)) return "Windows Workstation/Server";
        if (openPorts.contains(22) && openPorts.contains(80)) return "Linux Server";
        if (openPorts.contains(3306) || openPorts.contains(5432) || openPorts.contains(27017)) return "Database Server";
        if (openPorts.contains(80) || openPorts.contains(443)) return "Web Server";
        if (openPorts.contains(22)) return "Linux Device";
        return "Network Device";
    }

    // ── CIDR expansion ────────────────────────────────────────────────────────

    private List<String> resolveIps(NetworkScanRequestDto req) {
        if (req.getIpAddresses() != null && !req.getIpAddresses().isEmpty()) {
            return req.getIpAddresses();
        }
        if (req.getCidrRange() != null && !req.getCidrRange().isBlank()) {
            return expandCidr(req.getCidrRange());
        }
        return Collections.emptyList();
    }

    private List<String> expandCidr(String cidr) {
        List<String> ips = new ArrayList<>();
        try {
            String[] parts = cidr.split("/");
            String baseIp = parts[0];
            int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;
            prefix = Math.max(prefix, 16); // safety: don't scan more than /16

            String[] octets = baseIp.split("\\.");
            long base = (Long.parseLong(octets[0]) << 24)
                    | (Long.parseLong(octets[1]) << 16)
                    | (Long.parseLong(octets[2]) << 8)
                    | Long.parseLong(octets[3]);

            long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            long network = base & mask;
            long broadcast = network | (~mask & 0xFFFFFFFFL);

            for (long ip = network + 1; ip < broadcast; ip++) {
                ips.add(((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "."
                        + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF));
            }
        } catch (Exception e) {
            log.warn("Failed to expand CIDR '{}': {}", cidr, e.getMessage());
        }
        return ips;
    }

    // ── Other operations ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<DiscoveredDeviceDto> list(Pageable pageable) {
        Organisation org = requireTenantOrg();
        return deviceRepo.findByOrganisationAndDeletedAtIsNullOrderByLastSeenAtDesc(org, pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public DiscoveredDeviceDto getById(UUID id) {
        Organisation org = requireTenantOrg();
        DiscoveredDevice device = deviceRepo.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new NoSuchElementException("Discovered device not found: " + id));
        return toDto(device);
    }

    @Override
    public Map<String, Object> promote(UUID deviceId) {
        Organisation org = requireTenantOrg();
        DiscoveredDevice device = deviceRepo.findByIdAndOrganisationAndDeletedAtIsNull(deviceId, org)
                .orElseThrow(() -> new NoSuchElementException("Discovered device not found: " + deviceId));

        if (device.getPromotedAssetId() != null) {
            throw new IllegalStateException("Device already promoted to asset: " + device.getPromotedAssetId());
        }

        Asset asset = new Asset();
        asset.setName(device.getHostname() != null ? device.getHostname() : device.getIpAddress());
        asset.setDescription("Auto-promoted from network discovery. IP: " + device.getIpAddress());
        asset.setAssetType(AssetType.HARDWARE);
        asset.setStatus(AssetStatus.IN_USE);
        asset.setOrganisation(org);
        asset = assetRepository.save(asset);

        device.setPromotedAssetId(asset.getId());
        device.setStatus(DeviceStatus.PROMOTED);
        deviceRepo.save(device);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetId", asset.getId());
        result.put("assetName", asset.getName());
        result.put("deviceId", deviceId);
        return result;
    }

    @Override
    public void delete(UUID id) {
        Organisation org = requireTenantOrg();
        DiscoveredDevice device = deviceRepo.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new NoSuchElementException("Discovered device not found: " + id));
        device.setDeletedAt(Instant.now());
        deviceRepo.save(device);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Organisation org = requireTenantOrg();
        List<DiscoveredDevice> all = deviceRepo.findByOrganisationAndDeletedAtIsNull(org);
        long online   = all.stream().filter(d -> DeviceStatus.ONLINE.equals(d.getStatus())).count();
        long offline  = all.stream().filter(d -> DeviceStatus.OFFLINE.equals(d.getStatus())).count();
        long promoted = all.stream().filter(d -> DeviceStatus.PROMOTED.equals(d.getStatus())).count();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", all.size());
        map.put("online", online);
        map.put("offline", offline);
        map.put("promoted", promoted);
        return map;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private DiscoveredDeviceDto toDto(DiscoveredDevice d) {
        DiscoveredDeviceDto dto = new DiscoveredDeviceDto();
        dto.setId(d.getId());
        dto.setIpAddress(d.getIpAddress());
        dto.setHostname(d.getHostname());
        dto.setMacAddress(d.getMacAddress());
        dto.setDeviceType(d.getDeviceType());
        dto.setDiscoveryMethod(d.getDiscoveryMethod());
        dto.setStatus(d.getStatus());
        dto.setOsHint(d.getOsHint());
        dto.setResponseTimeMs(d.getResponseTimeMs());
        dto.setLastSeenAt(d.getLastSeenAt());
        dto.setPromotedAssetId(d.getPromotedAssetId());
        dto.setCreatedAt(d.getCreatedAt());
        if (d.getOpenPorts() != null && !d.getOpenPorts().isBlank()) {
            dto.setOpenPorts(Arrays.stream(d.getOpenPorts().split(","))
                    .map(Integer::parseInt).collect(Collectors.toList()));
        }
        return dto;
    }
}
