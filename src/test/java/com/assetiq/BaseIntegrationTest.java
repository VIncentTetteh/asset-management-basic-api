package com.assetiq;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for Spring Boot integration tests.
 *
 * Provides a shared Postgres + Redis Testcontainer pair started once per JVM
 * (singleton containers, shared by every subclass and its cached Spring context).
 * Subclasses inherit @SpringBootTest, @AutoConfigureMockMvc, and  * and can inject MockMvc, TestRestTemplate, or any Spring bean directly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("assetiq_test")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @SuppressWarnings("resource")
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    // Singleton containers: started once per JVM and never stopped per class
    // (Ryuk removes them at exit). With @Container they stopped after the first
    // subclass while Spring's cached context kept their old ports, so every later
    // subclass sharing that context failed with "connection refused".
    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",         postgres::getJdbcUrl);
        registry.add("spring.datasource.username",    postgres::getUsername);
        registry.add("spring.datasource.password",    postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.data.redis.host",        redis::getHost);
        registry.add("spring.data.redis.port",        () -> redis.getMappedPort(6379));
        // Suppress startup security validation noise in test runs
        registry.add("app.startup.skip-secret-validation", () -> "true");
    }
}
