package com.assetiq.dto;

import lombok.Data;

import java.util.List;

@Data
public class NetworkScanRequestDto {

    /**
     * CIDR range to scan (e.g. "192.168.1.0/24").
     * Alternatively supply a list of explicit IPs.
     */
    private String cidrRange;

    /** Explicit IP list — used when cidrRange is null/blank */
    private List<String> ipAddresses;

    /** Whether to perform a port scan on reachable hosts */
    private boolean portScan = false;

    /** Ports to check during port scan (defaults applied in service if empty) */
    private List<Integer> ports;

    /** Timeout in milliseconds per host */
    private int timeoutMs = 1000;
}
