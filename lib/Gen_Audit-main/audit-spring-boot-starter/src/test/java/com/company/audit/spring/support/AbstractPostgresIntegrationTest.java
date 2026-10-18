package com.company.audit.spring.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need a real Postgres instance via Testcontainers.
 *
 * <p>Uses the "singleton container" pattern: the container is started once, in a static
 * initializer, and never explicitly stopped (Testcontainers' Ryuk reaper cleans it up at JVM
 * exit). This is intentional and required, not an oversight — the {@code @Testcontainers}
 * JUnit 5 extension manages a {@code @Container}-annotated field's lifecycle per test class, so
 * a field merely inherited from this abstract base would be stopped after the first subclass's
 * tests finish, leaving every subsequent test class unable to connect. A manually-started,
 * never-stopped static container is the standard way to genuinely share one instance across
 * multiple test classes.
 */
public abstract class AbstractPostgresIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    /**
     * Registers the running container's connection details as Spring datasource properties.
     *
     * @param registry the registry to add dynamic properties to
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
