package com.assetiq.services.impl;

import com.assetiq.services.AssetService;
import com.assetiq.dto.PromoteDeviceRequest;
import com.assetiq.dto.AssetDto;
import com.assetiq.dto.DiscoveredDeviceDto;
import com.assetiq.dto.NetworkScanRequestDto;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.AssetType;
import com.assetiq.enums.DeviceStatus;
import com.assetiq.enums.DiscoveryMethod;
import com.assetiq.models.DiscoveredDevice;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.DiscoveredDeviceRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.NetworkDiscoveryService;
import com.assetiq.services.TenantAwareService;
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

    /** Hosts probed by one request; a /24. Larger sweeps tied up the request for minutes. */
    static final int MAX_HOSTS_PER_SCAN = 256;
    static final int MAX_PORTS_PER_SCAN = 32;
    private static final java.util.regex.Pattern IPV4 =
            java.util.regex.Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private final DiscoveredDeviceRepository deviceRepo;
    private final AssetService assetService;

    public NetworkDiscoveryServiceImpl(OrganisationRepository organisationRepository,
                                       DiscoveredDeviceRepository deviceRepo,
                                       AssetService assetService) {
        super(organisationRepository);
        this.deviceRepo = deviceRepo;
        this.assetService = assetService;
    }

    // ── Scan ─────────────────────────────────────────────────────────────────

    @Override
    public List<DiscoveredDeviceDto> scan(NetworkScanRequestDto request) {
        Organisation org = requireTenantOrg();
        List<String> ips = resolveIps(request);
        if (ips.isEmpty()) {
            throw new IllegalArgumentException("Give a CIDR range (e.g. 192.168.1.0/24) or a list of IP addresses");
        }
        validateTargets(ips);

        int timeoutMs = Math.min(Math.max(request.getTimeoutMs(), 100), 5000);
        List<Integer> ports = (request.getPorts() != null && !request.getPorts().isEmpty())
                ? request.getPorts() : DEFAULT_PORTS;
        validatePorts(ports);

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

        DiscoveredDevice device = findOrReviveDevice(ip, org);

        List<Integer> open = reachable && portScan ? scanPorts(ip, ports, timeoutMs) : null;
        applyProbe(device, reachable, hostname.equals(ip) ? null : hostname, elapsed, portScan, open, Instant.now());

        return deviceRepo.save(device);
    }

    /**
     * The live record for this address, else the last deleted one revived (a rescan
     * used to insert a duplicate row), else a new record.
     */
    DiscoveredDevice findOrReviveDevice(String ip, Organisation org) {
        DiscoveredDevice device = deviceRepo
                .findByIpAddressAndOrganisationAndDeletedAtIsNull(ip, org)
                .or(() -> deviceRepo.findFirstByIpAddressAndOrganisationAndDeletedAtIsNotNullOrderByDeletedAtDesc(ip, org))
                .orElseGet(DiscoveredDevice::new);
        device.setDeletedAt(null);
        device.setIpAddress(ip);
        device.setOrganisation(org);
        return device;
    }

    /**
     * Records one probe on a device. A device already promoted to an asset stays
     * PROMOTED (a rescan used to flip it back to ONLINE, offering Promote again,
     * which then failed). Open ports and the inferred type describe only this probe:
     * they are cleared when the host is unreachable or ports were not scanned, so
     * stale results do not linger.
     */
    static void applyProbe(DiscoveredDevice device, boolean reachable, String hostname, long elapsedMs,
                           boolean portScan, List<Integer> openPorts, Instant seenAt) {
        device.setHostname(hostname);
        if (device.getPromotedAssetId() != null) {
            device.setStatus(DeviceStatus.PROMOTED);
        } else {
            device.setStatus(reachable ? DeviceStatus.ONLINE : DeviceStatus.OFFLINE);
        }
        device.setResponseTimeMs(reachable ? elapsedMs : null);
        device.setLastSeenAt(seenAt);
        device.setDiscoveryMethod(portScan ? DiscoveryMethod.PORT_SCAN : DiscoveryMethod.PING);

        if (reachable && portScan && openPorts != null) {
            device.setOpenPorts(openPorts.stream().map(String::valueOf).collect(Collectors.joining(",")));
            device.setDeviceType(inferDeviceType(openPorts));
        } else {
            device.setOpenPorts(null);
            device.setDeviceType(null);
        }
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

    private static String inferDeviceType(List<Integer> openPorts) {
        if (openPorts.contains(3389)) return "Windows Workstation/Server";
        if (openPorts.contains(22) && openPorts.contains(80)) return "Linux Server";
        if (openPorts.contains(3306) || openPorts.contains(5432) || openPorts.contains(27017)) return "Database Server";
        if (openPorts.contains(80) || openPorts.contains(443)) return "Web Server";
        if (openPorts.contains(22)) return "Linux Device";
        return "Network Device";
    }

    // ── CIDR expansion ────────────────────────────────────────────────────────

    /**
     * Only literal IPv4 addresses, at most {@link #MAX_HOSTS_PER_SCAN}, and never
     * this server's own loopback, link-local (169.254.0.0/16, which includes the
     * cloud metadata endpoint), multicast or reserved space. Hostnames are refused
     * so a scan cannot be steered through DNS.
     */
    static void validateTargets(List<String> ips) {
        if (ips.size() > MAX_HOSTS_PER_SCAN) {
            throw new IllegalArgumentException("A scan can cover at most " + MAX_HOSTS_PER_SCAN
                    + " addresses (a /24); split larger ranges");
        }
        for (String ip : ips) {
            int[] o = parseIpv4(ip);
            if (o == null) {
                throw new IllegalArgumentException("Not an IPv4 address: " + ip);
            }
            boolean blocked = o[0] == 0 || o[0] == 127 || (o[0] == 169 && o[1] == 254) || o[0] >= 224;
            if (blocked) {
                throw new IllegalArgumentException("Address " + ip
                        + " is loopback, link-local, multicast or reserved and cannot be scanned");
            }
        }
    }

    static void validatePorts(List<Integer> ports) {
        if (ports.size() > MAX_PORTS_PER_SCAN) {
            throw new IllegalArgumentException("A scan can check at most " + MAX_PORTS_PER_SCAN + " ports");
        }
        for (Integer port : ports) {
            if (port == null || port < 1 || port > 65535) {
                throw new IllegalArgumentException("Invalid port: " + port);
            }
        }
    }

    private static int[] parseIpv4(String ip) {
        if (ip == null) return null;
        java.util.regex.Matcher m = IPV4.matcher(ip.trim());
        if (!m.matches()) return null;
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            octets[i] = Integer.parseInt(m.group(i + 1));
            if (octets[i] > 255) return null;
        }
        return octets;
    }

    private List<String> resolveIps(NetworkScanRequestDto req) {
        if (req.getIpAddresses() != null && !req.getIpAddresses().isEmpty()) {
            return req.getIpAddresses().stream().map(String::trim).filter(ip -> !ip.isEmpty()).distinct().toList();
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
            if (prefix < 24 || prefix > 32) {
                throw new IllegalArgumentException("CIDR prefix must be between /24 and /32 (at most "
                        + MAX_HOSTS_PER_SCAN + " addresses)");
            }

            String[] octets = baseIp.split("\\.");
            long base = (Long.parseLong(octets[0]) << 24)
                    | (Long.parseLong(octets[1]) << 16)
                    | (Long.parseLong(octets[2]) << 8)
                    | Long.parseLong(octets[3]);

            long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            long network = base & mask;
            long broadcast = network | (~mask & 0xFFFFFFFFL);

            if (prefix >= 31) {
                // /31 and /32 have no network/broadcast addresses to skip.
                for (long ip = network; ip <= broadcast; ip++) {
                    ips.add(((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "."
                            + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF));
                }
                return ips;
            }
            for (long ip = network + 1; ip < broadcast; ip++) {
                ips.add(((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "."
                        + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid CIDR range: " + cidr);
        }
        return ips;
    }

    // ── Other operations ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<DiscoveredDeviceDto> list(Pageable pageable, DeviceStatus status) {
        Organisation org = requireTenantOrg();
        Page<DiscoveredDevice> page = status == null
                ? deviceRepo.findByOrganisationAndDeletedAtIsNullOrderByLastSeenAtDesc(org, pageable)
                : deviceRepo.findByOrganisationAndStatusAndDeletedAtIsNullOrderByLastSeenAtDesc(org, status, pageable);
        return page.map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public DiscoveredDeviceDto getById(UUID id) {
        Organisation org = requireTenantOrg();
        DiscoveredDevice device = deviceRepo.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Discovered device not found: " + id));
        return toDto(device);
    }

    @Override
    public Map<String, Object> promote(UUID deviceId, PromoteDeviceRequest request) {
        Organisation org = requireTenantOrg();
        DiscoveredDevice device = deviceRepo.findByIdAndOrganisationAndDeletedAtIsNull(deviceId, org)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Discovered device not found: " + deviceId));

        if (device.getPromotedAssetId() != null) {
            throw new IllegalStateException("Device already promoted to asset: " + device.getPromotedAssetId());
        }

        // Registered through the asset service, so promotion gets the same plan limit,
        // duplicate-name check, category defaults (prefix tag, warranty), currency and
        // notifications as any other new asset.
        AssetDto asset = assetService.create(promotedAsset(device, request));

        device.setPromotedAssetId(asset.getId());
        device.setStatus(DeviceStatus.PROMOTED);
        deviceRepo.save(device);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetId", asset.getId());
        result.put("assetName", asset.getName());
        result.put("deviceId", deviceId);
        return result;
    }

    /**
     * The asset a device becomes: the requested name (else hostname, else IP), the
     * chosen category and location, and a description recording what the scan saw.
     */
    static AssetDto promotedAsset(DiscoveredDevice device, PromoteDeviceRequest request) {
        AssetDto dto = new AssetDto();
        String requested = request == null || request.name() == null ? null : request.name().trim();
        String fallback = device.getHostname() != null && !device.getHostname().isBlank()
                ? device.getHostname().trim() : device.getIpAddress();
        dto.setName(requested != null && !requested.isEmpty() ? requested : fallback);
        if (request != null) {
            dto.setCategoryId(request.categoryId());
            dto.setLocationId(request.locationId());
        }
        StringBuilder description = new StringBuilder("Promoted from network discovery.");
        description.append("\nIP address: ").append(device.getIpAddress());
        if (device.getHostname() != null && !device.getHostname().isBlank()) {
            description.append("\nHostname: ").append(device.getHostname().trim());
        }
        if (device.getDeviceType() != null && !device.getDeviceType().isBlank()) {
            description.append("\nDevice type: ").append(device.getDeviceType());
        }
        if (device.getOpenPorts() != null && !device.getOpenPorts().isBlank()) {
            description.append("\nOpen ports: ").append(device.getOpenPorts());
        }
        dto.setDescription(description.toString());
        dto.setAssetType(AssetType.HARDWARE);
        // Seen live on the network, so in service.
        dto.setStatus(AssetStatus.IN_USE);
        return dto;
    }

    @Override
    public void delete(UUID id) {
        Organisation org = requireTenantOrg();
        DiscoveredDevice device = deviceRepo.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Discovered device not found: " + id));
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
        long unknown  = all.stream().filter(d -> d.getStatus() == null || DeviceStatus.UNKNOWN.equals(d.getStatus())).count();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", all.size());
        map.put("online", online);
        map.put("offline", offline);
        map.put("promoted", promoted);
        // Each device has exactly one status, so the four counts add up to the total
        // and match what GET /devices?status= returns.
        map.put("unknown", unknown);
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
