package com.assetiq.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpointGroup;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The readiness probe decides whether the platform routes traffic to an instance.
 *
 * <p>Spring's default readiness group contains {@code readinessState} alone, which flips to
 * UP once the context has started and never consults a dependency. {@code railway.toml}
 * points its health check at {@code /actuator/health/readiness}, so an instance whose
 * database connection was broken still advertised itself as ready and received traffic it
 * could only answer with 500s.
 *
 * <p>This test matters more than it looks because
 * {@code management.endpoint.health.validate-group-membership} is deliberately {@code false}
 * — required, since {@code redis} is optional in this application and naming an absent
 * contributor would otherwise abort startup. The cost of that setting is that a typo in the
 * group's include list is silently ignored rather than reported: {@code db} misspelt as
 * {@code bd} would leave readiness exactly as weak as before, with nothing to show for it.
 * These assertions are what stop that regression.
 */
@SpringBootTest
@DisplayName("Readiness probe composition")
class ReadinessProbeTest {

    @Autowired
    private HealthEndpointGroups groups;

    @Test
    @DisplayName("readiness includes the database, not just readinessState")
    void readinessIncludesDatabase() {
        HealthEndpointGroup readiness = groups.get("readiness");

        assertThat(readiness)
                .describedAs("no 'readiness' health group is configured")
                .isNotNull();
        assertThat(readiness.isMember("db"))
                .describedAs("The database is the one dependency this service cannot serve "
                        + "any request without. If it is not part of readiness, the load "
                        + "balancer keeps sending traffic to an instance that can only 500.")
                .isTrue();
        assertThat(readiness.isMember("readinessState")).isTrue();
    }

    @Test
    @DisplayName("liveness excludes the database so an outage cannot cause a crash loop")
    void livenessExcludesDatabase() {
        HealthEndpointGroup liveness = groups.get("liveness");

        assertThat(liveness).isNotNull();
        assertThat(liveness.isMember("db"))
                .describedAs("A failing database must take instances out of rotation, not "
                        + "kill them: restarting every replica turns a recoverable dependency "
                        + "outage into a crash loop that cannot recover on its own.")
                .isFalse();
    }
}
