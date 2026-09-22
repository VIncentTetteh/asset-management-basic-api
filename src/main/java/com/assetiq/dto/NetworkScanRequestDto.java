package com.assetiq.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class NetworkScanRequestDto {

    /**
     * Shape of a literal IPv4 address. The octet values themselves are checked in
     * the service (together with the loopback / link-local / metadata / multicast
     * guards); this keeps malformed input out and reports it as a field error
     * rather than a bare 400, the way every other form does.
     */
    public static final String IPV4 = "^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$";

    /** The same address, with an optional /0-32 prefix. */
    public static final String IPV4_CIDR = "^$|^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(/\\d{1,2})?$";

    /** A scan covers at most a /24. */
    public static final int MAX_HOSTS = 256;

    /** The service checks at most this many ports. */
    public static final int MAX_PORTS = 32;

    /**
     * CIDR range to scan (e.g. "192.168.1.0/24").
     * Alternatively supply a list of explicit IPs.
     */
    @Pattern(regexp = IPV4_CIDR, message = "must be an IPv4 range such as 192.168.1.0/24")
    private String cidrRange;

    /** Explicit IP list — used when cidrRange is null/blank */
    @Size(max = MAX_HOSTS, message = "A scan can cover at most " + MAX_HOSTS + " addresses (a /24)")
    private List<@Pattern(regexp = IPV4, message = "must be an IPv4 address such as 192.168.1.10") String> ipAddresses;

    /** Whether to perform a port scan on reachable hosts */
    private boolean portScan = false;

    /** Ports to check during port scan (defaults applied in service if empty) */
    @Size(max = MAX_PORTS, message = "A scan can check at most " + MAX_PORTS + " ports")
    private List<@Min(value = 1, message = "must be between 1 and 65535")
                 @Max(value = 65535, message = "must be between 1 and 65535") Integer> ports;

    /** Timeout in milliseconds per host */
    @Min(value = 50, message = "must be at least 50 ms")
    @Max(value = 10_000, message = "must be at most 10000 ms")
    private int timeoutMs = 1000;
}
