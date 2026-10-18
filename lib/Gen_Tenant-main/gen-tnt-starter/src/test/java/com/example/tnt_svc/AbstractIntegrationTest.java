// gen-tnt-starter/src/test/java/com/example/tnt_svc/AbstractIntegrationTest.java
package com.example.tnt_svc;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared real-Postgres base for every integration test in this module — no mocked DB.
 *
 * <p>{@code @EnableAutoConfiguration} is required alongside {@code @SpringBootTest(classes=...)}:
 * passing an explicit {@code classes} array does not by itself trigger Spring Boot's
 * autoconfiguration import chain (DataSource/JPA/Flyway all live behind it) the way a real
 * {@code @SpringBootApplication} entrypoint would — {@code gen-tnt-starter} has no such
 * entrypoint of its own (that only exists in {@code gen-tnt-demo}), so tests here have to
 * ask for it explicitly.
 */
@Testcontainers
@EnableAutoConfiguration
@SpringBootTest(classes = com.example.tnt_svc.config.GenTntAutoConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("gentnt_test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/gentnt");
        registry.add("gentnt.internal-secret", () -> "test-secret");
    }
}
