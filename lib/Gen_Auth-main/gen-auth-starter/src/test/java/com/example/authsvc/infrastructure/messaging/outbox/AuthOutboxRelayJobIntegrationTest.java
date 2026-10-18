package com.example.authsvc.infrastructure.messaging.outbox;

import com.example.authsvc.infrastructure.persistence.entity.AuthOutboxEventEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthOutboxEventJpaRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@code FOR UPDATE SKIP LOCKED} genuinely partitions concurrent batches —
 * the one claim in this sub-project a mocked unit test cannot make. Follows the
 * same manual-lifecycle Testcontainers convention as
 * {@code RsaKeyConfigDbKeyIntegrationTest} (this codebase's first use of
 * Testcontainers), not {@code @Testcontainers}/{@code @Container}.
 */
class AuthOutboxRelayJobIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    private static EntityManagerFactory   emf;

    @BeforeAll
    static void migrateSchemaAndBuildEntityManagerFactory() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available in this environment — skipping the SKIP LOCKED "
                        + "concurrency integration test");

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
        dataSource.setMaximumPoolSize(4);

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
    void concurrentFindPendingForUpdate_returnsDisjointBatches() throws InterruptedException {
        EntityManager seedEm = emf.createEntityManager();
        JpaRepositoryFactory seedFactory = new JpaRepositoryFactory(seedEm);
        AuthOutboxEventJpaRepository seedRepo = seedFactory.getRepository(AuthOutboxEventJpaRepository.class);

        seedEm.getTransaction().begin();
        for (int i = 0; i < 4; i++) {
            seedRepo.save(AuthOutboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .eventId(UUID.randomUUID())
                    .eventType("auth.login.success")
                    .payload("{}")
                    .status("PENDING")
                    .retryCount(0)
                    .createdAt(Instant.now())
                    .build());
        }
        seedEm.getTransaction().commit();
        seedEm.close();

        // Two threads each lock a batch of 2 (of the 4 total rows) concurrently, hold the
        // lock briefly via an unfinished transaction, then release. SKIP LOCKED must ensure
        // thread B's batch never overlaps thread A's while A still holds its locks.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicReference<List<UUID>> batchA = new AtomicReference<>();
        AtomicReference<List<UUID>> batchB = new AtomicReference<>();

        Runnable lockBatch = () -> {
            EntityManager em = emf.createEntityManager();
            try {
                JpaRepositoryFactory factory = new JpaRepositoryFactory(em);
                AuthOutboxEventJpaRepository repo = factory.getRepository(AuthOutboxEventJpaRepository.class);
                em.getTransaction().begin();
                List<UUID> ids = repo.findPendingForUpdate(2).stream()
                        .map(AuthOutboxEventEntity::getId).toList();
                bothStarted.countDown();
                bothStarted.await(5, TimeUnit.SECONDS);
                Thread.sleep(200); // hold the row locks while the other thread also queries
                em.getTransaction().commit();
                if (batchA.get() == null) {
                    batchA.set(ids);
                } else {
                    batchB.set(ids);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                em.close();
            }
        };

        pool.submit(lockBatch);
        pool.submit(lockBatch);
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(2, batchA.get().size());
        assertEquals(2, batchB.get().size());
        assertTrue(java.util.Collections.disjoint(batchA.get(), batchB.get()),
                "SKIP LOCKED must prevent the two concurrent batches from overlapping");
    }
}
