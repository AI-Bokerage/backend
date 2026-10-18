package com.company.ppmsvc.integration;

import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for all PPM integration tests.
 *
 * <p>Spins up a single {@link PostgreSQLContainer} (shared across all subclasses
 * via static singleton) and overrides {@code spring.datasource.*} so the full
 * Spring context — JPA, Flyway, application services — runs against the
 * Testcontainers Postgres instance.
 *
 * <p>RabbitMQ and Redis are excluded from the Spring context via
 * {@code application-integration.yaml}.  {@link StringRedisTemplate} is replaced
 * by a Mockito no-op so {@link com.company.ppmsvc.infrastructure.security.RedisRolePermissionResolver}
 * falls back gracefully.
 *
 * <p>No RLS setup is needed — PPM-SVC is a catalog service with no per-tenant
 * row isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration")
public abstract class AbstractContainerIntegrationTest {

    protected static final UUID DEV_USER =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    static final PostgreSQLContainer<?> POSTGRES;
    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("ppmdb")
            .withUsername("ppm_owner")
            .withPassword("integration_test_pw");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url",          POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user",         POSTGRES::getUsername);
        registry.add("spring.flyway.password",     POSTGRES::getPassword);
    }

    @MockitoBean
    protected StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setSecurityContext() {
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            DEV_USER, null, null, null,
            CpmsUserType.TENANT_USER,
            "test-session-id", "test-jti",
            Instant.now().plusSeconds(900)
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_TENANT_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
