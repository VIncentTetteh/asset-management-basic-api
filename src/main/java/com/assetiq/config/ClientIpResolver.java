package com.assetiq.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;

/**
 * The client's IP address, honouring {@code X-Forwarded-For} only when the TCP
 * peer is a trusted proxy ({@code app.rate-limiting.trusted-proxy-cidrs}).
 * Anything else a client sends is ignored, so the address cannot be spoofed.
 * Shared by rate limiting and evidence records such as consent.
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private final RateLimitingConfig config;

    public ClientIpResolver(RateLimitingConfig config) {
        this.config = config;
    }

    /** First {@code X-Forwarded-For} address from a trusted proxy, else the TCP peer address. */
    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    /**
     * Returns true if {@code remoteAddr} falls within any of the configured
     * trusted-proxy CIDRs.  Ignores any CIDRs that fail to parse rather than
     * crashing — a bad config entry causes a WARN, not a 500.
     */
    public boolean isTrustedProxy(String remoteAddr) {
        List<String> cidrs = config.getTrustedProxyCidrs();
        if (cidrs == null || cidrs.isEmpty()) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(remoteAddr);
            for (String cidr : cidrs) {
                try {
                    if (isInCidr(addr, cidr)) return true;
                } catch (Exception e) {
                    log.warn("[CLIENT_IP] Unparseable trusted-proxy CIDR '{}': {}", cidr, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[CLIENT_IP] Could not parse remoteAddr '{}': {}", remoteAddr, e.getMessage());
        }
        return false;
    }

    /** Checks whether {@code addr} is within the given CIDR (e.g. "10.0.0.0/8"). */
    private boolean isInCidr(InetAddress addr, String cidr) throws Exception {
        int slash = cidr.indexOf('/');
        if (slash < 0) {
            // No prefix — treat as host address (/32 or /128)
            return InetAddress.getByName(cidr).equals(addr);
        }
        InetAddress network   = InetAddress.getByName(cidr.substring(0, slash));
        int         prefixLen = Integer.parseInt(cidr.substring(slash + 1));

        byte[] addrBytes    = addr.getAddress();
        byte[] networkBytes = network.getAddress();

        if (addrBytes.length != networkBytes.length) {
            // IPv4 vs IPv6 mismatch — not in CIDR
            return false;
        }

        int fullBytes  = prefixLen / 8;
        int remainBits = prefixLen % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (addrBytes[i] != networkBytes[i]) return false;
        }
        if (remainBits > 0 && fullBytes < addrBytes.length) {
            int mask = (0xFF << (8 - remainBits)) & 0xFF;
            if ((addrBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) return false;
        }
        return true;
    }

}
