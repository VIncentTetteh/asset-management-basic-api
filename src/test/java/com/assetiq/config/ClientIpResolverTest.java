package com.assetiq.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) r.addHeader("X-Forwarded-For", forwardedFor);
        return r;
    }

    @Test
    void forwardedForIsIgnoredWithoutTrustedProxies() {
        ClientIpResolver resolver = new ClientIpResolver(new RateLimitingConfig());

        assertThat(resolver.resolve(request("198.51.100.9", "1.2.3.4"))).isEqualTo("198.51.100.9");
    }

    @Test
    void forwardedForIsUsedOnlyFromATrustedProxy() {
        RateLimitingConfig config = new RateLimitingConfig();
        config.setTrustedProxyCidrs(List.of("10.0.0.0/8"));
        ClientIpResolver resolver = new ClientIpResolver(config);

        assertThat(resolver.resolve(request("10.1.2.3", "203.0.113.7, 10.1.2.3"))).isEqualTo("203.0.113.7");
        assertThat(resolver.resolve(request("198.51.100.9", "203.0.113.7"))).isEqualTo("198.51.100.9");
        assertThat(resolver.resolve(request("10.1.2.3", null))).isEqualTo("10.1.2.3");
    }
}
