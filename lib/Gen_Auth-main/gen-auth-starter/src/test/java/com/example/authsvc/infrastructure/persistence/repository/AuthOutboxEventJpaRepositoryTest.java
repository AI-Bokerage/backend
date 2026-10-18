package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.AuthOutboxEventEntity;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * First consumer of {@link AuthOutboxEventJpaRepository} — proves the native
 * {@code FOR UPDATE SKIP LOCKED} polling query and JSONB payload mapping against a real
 * Postgres instance (an H2/mocked repository can't exercise either).
 *
 * <p>Follows the same Testcontainers convention established in
 * {@code RsaKeyConfigDbKeyIntegrationTest}: bypasses {@code @SpringBootTest}/{@code @DataJpaTest}
 * in favor of a hand-built {@link EntityManagerFactory} + {@link JpaRepositoryFactory}, with
 * manual container lifecycle (no {@code @Testcontainers}/{@code @Container}) so Docker
 * availability can be checked with {@link Assumptions#assumeTrue} before anything tries to
 * start a container.
 */
class AuthOutboxEventJpaRepositoryTest {

    private static PostgreSQLContainer<?> postgres;
    private static EntityManagerFactory   emf;

    @BeforeAll
    static void migrateSchemaAndBuildEntityManagerFactory() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available in this environment — skipping the Testcontainers-backed "
                        + "AuthOutboxEventJpaRepository integration test");

        postgres = new PostgreSQLContainer<>("postgres:15");
        postgres.start();

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");

        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setDataSource((DataSource) dataSource);
        emfBean.setPackagesToScan("com.example.authsvc.infrastructure.persistence.entity");
        emfBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties jpaProps = new Properties();
        jpaProps.setProperty("hibernate.hbm2ddl.auto", "validate");
        emfBean.setJpaProperties(jpaProps);
        emfBean.afterPropertiesSet();
        emf = emfBean.getObject();
    }

    @AfterAll
    static void tearDown() {
        if (emf != null) { emf.close(); }
        if (postgres != null) { postgres.stop(); }
    }

    @Test
    void saveThenFindPendingForUpdate_returnsRowSortedByCreatedAt_thenMutationMethodsTransitionStatus() {
        EntityManager em = emf.createEntityManager();
        JpaRepositoryFactory repositoryFactory = new JpaRepositoryFactory(em);
        AuthOutboxEventJpaRepository repository =
                repositoryFactory.getRepository(AuthOutboxEventJpaRepository.class);

        UUID id = UUID.randomUUID();
        em.getTransaction().begin();
        repository.save(AuthOutboxEventEntity.builder()
                .id(id)
                .eventId(UUID.randomUUID())
                .eventType("auth.login.success")
                .payload("{\"userId\":\"" + UUID.randomUUID() + "\"}")
                .status("PENDING")
                .retryCount(0)
                .createdAt(Instant.now())
                .build());
        em.getTransaction().commit();
        em.clear();

        List<AuthOutboxEventEntity> pending = repository.findPendingForUpdate(10);
        assertEquals(1, pending.size());
        assertEquals(id, pending.get(0).getId());
        assertEquals("PENDING", pending.get(0).getStatus());

        em.getTransaction().begin();
        int updated = repository.incrementRetry(id, "broker timeout");
        em.getTransaction().commit();
        assertEquals(1, updated);
        em.clear();

        AuthOutboxEventEntity afterRetry = repository.findById(id).orElseThrow();
        assertEquals(1, afterRetry.getRetryCount());
        assertEquals("broker timeout", afterRetry.getLastError());

        em.getTransaction().begin();
        repository.markPublished(id, Instant.now());
        em.getTransaction().commit();
        em.clear();

        AuthOutboxEventEntity published = repository.findById(id).orElseThrow();
        assertEquals("PUBLISHED", published.getStatus());
        assertTrue(published.getPublishedAt() != null);

        // A PUBLISHED row is no longer PENDING, so it must not reappear in the next poll.
        List<AuthOutboxEventEntity> pendingAfter = repository.findPendingForUpdate(10);
        assertEquals(0, pendingAfter.size());

        em.close();
    }
}
