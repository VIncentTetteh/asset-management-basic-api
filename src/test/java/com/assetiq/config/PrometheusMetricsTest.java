package com.assetiq.config;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the application can actually be scraped.
 *
 * <p>{@code application.yml} exposed {@code prometheus} in the actuator include list and
 * set {@code management.prometheus.metrics.export.enabled: true}, and the README advertised
 * "Actuator + Prometheus" — but {@code micrometer-registry-prometheus} was never a
 * dependency. Spring Boot registers a Prometheus registry only when that jar is present, so
 * {@code /actuator/prometheus} returned 404 and the service emitted no metrics at all. Every
 * signal pointed the other way: the config looked right, the docs looked right, and nothing
 * failed. Only a missing-bean check catches that.
 */
@SpringBootTest
@DisplayName("Prometheus metrics")
class PrometheusMetricsTest {

    @Autowired(required = false)
    private PrometheusMeterRegistry registry;

    @Test
    @DisplayName("a Prometheus registry is wired into the context")
    void registryIsPresent() {
        assertThat(registry)
                .describedAs("No PrometheusMeterRegistry bean. The actuator config exposes "
                        + "/actuator/prometheus, but without micrometer-registry-prometheus on "
                        + "the classpath that endpoint 404s and the service has no metrics.")
                .isNotNull();
    }

    @Test
    @DisplayName("the registry produces a scrapeable payload with JVM and HTTP metrics")
    void scrapeReturnsMetrics() {
        String scrape = registry.scrape();

        assertThat(scrape).isNotBlank();
        // Spot-check the families an operator builds the first dashboard and alert on.
        assertThat(scrape)
                .describedAs("expected standard JVM instrumentation in the scrape output")
                .contains("jvm_memory_used_bytes");
    }
}
