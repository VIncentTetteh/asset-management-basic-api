package com.assetiq.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Network scan targets are bounded and never the server itself")
class NetworkDiscoveryScanPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "169.254.169.254", "0.0.0.0", "224.0.0.1", "255.255.255.255",
            "db.internal", "10.0.0.300", "not-an-ip"})
    void refusesServerLocalOrNonLiteralTargets(String ip) {
        assertThatThrownBy(() -> NetworkDiscoveryServiceImpl.validateTargets(List.of(ip)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsLanAddressesUpToASlash24() {
        assertThatCode(() -> NetworkDiscoveryServiceImpl.validateTargets(List.of("192.168.1.10", "10.0.0.5")))
                .doesNotThrowAnyException();
        List<String> tooMany = IntStream.range(0, 300).mapToObj(i -> "10.0." + (i / 250) + "." + (i % 250 + 1)).toList();
        assertThatThrownBy(() -> NetworkDiscoveryServiceImpl.validateTargets(tooMany))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void portsAreBounded() {
        assertThatThrownBy(() -> NetworkDiscoveryServiceImpl.validatePorts(List.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NetworkDiscoveryServiceImpl.validatePorts(IntStream.rangeClosed(1, 40).boxed().toList()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> NetworkDiscoveryServiceImpl.validatePorts(List.of(22, 443))).doesNotThrowAnyException();
    }
}
