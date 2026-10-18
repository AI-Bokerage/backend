# Transactional Outbox + Two-Tier Audit Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace gen-auth-starter's fire-and-forget `AuthEventPublisher` with a transactional-outbox-backed one (no more silently-lost events on a broker outage), and add two-tier (tenant/platform) audit-exchange classification riding the same mechanism.

**Architecture:** A new `auth_outbox_events` table + JPA repository (Task 1). `AuthEventPublisher`'s existing typed methods stop calling `RabbitTemplate` directly and instead write an outbox row (Task 2). New audit-tier typed methods write outbox rows tagged with a tier-specific exchange (Task 3), wired into the 4 real call sites that need them. A new `@Scheduled` relay job drains `PENDING` rows to RabbitMQ with retry/failure handling (Task 4).

**Tech Stack:** Spring Boot 4 / Spring Data JPA, Flyway, Spring AMQP (`RabbitTemplate`, `TopicExchange`/`FanoutExchange`), `@Scheduled` (first use in this codebase), Jackson `ObjectMapper`, JUnit 5 + Mockito + AssertJ, Testcontainers (Postgres) for the concurrency-proving integration test.

**Spec:** `docs/superpowers/specs/2026-09-01-outbox-audit-routing-design.md`

## Global Constraints

- `V7__auth_outbox_events.sql` schema is verbatim from CPMS's real migration — exact columns/types/constraints/index, copied in Task 1.
- `MAX_RETRIES = 3` — a `private static final int` constant in `AuthOutboxRelayJob`, NOT a configurable property.
- `app.messaging.outbox.relay-interval-ms` default `5000`; `app.messaging.outbox.batch-size` default `50`.
- `app.messaging.audit.tenant-exchange` default `"auth.audit.tenant"`; `app.messaging.audit.platform-exchange` default `"auth.audit.platform"` — generic library defaults, NOT CPMS's own `cpms.audit`/`cpms.platform.audit` names (a host app wanting exact CPMS parity sets those via config, no code change).
- Tenant-audit exchange is a `TopicExchange`; platform-audit exchange is a `FanoutExchange` — matches CPMS's real exchange types exactly.
- `AuthEventPublisher`'s 5 live public method signatures (`publishLoginSuccess`, `publishLoginFailed`, `publishLogout`, `publishPasswordChanged`, `publishImpersonationStarted`) do not change — only their internals. `publishImpersonationEnded` is deleted (zero call sites, matches CPMS's own choice).
- All new/existing `AuthEventPublisher` call sites are guarded by the existing `if (authEventPublisher != null)` null-check pattern (`@Autowired(required = false)` throughout this codebase) — never remove or bypass this guard.
- `@EnableScheduling` goes directly on `MessagingConfig` (this codebase's established convention: compare `SecurityConfig`'s `@EnableWebSecurity`/`@EnableMethodSecurity` placed directly on the class, not on any application main class).
- No Co-Authored-By trailer on any commit.

---

### Task 1: Outbox schema, entity, repository

**Files:**
- Create: `gen-auth-starter/src/main/resources/db/migration/genauth/V7__auth_outbox_events.sql`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/entity/AuthOutboxEventEntity.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/repository/AuthOutboxEventJpaRepository.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/persistence/repository/AuthOutboxEventJpaRepositoryTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks (first task).
- Produces: `AuthOutboxEventEntity` (fields: `id, eventId, eventType, payload, userId, targetExchange, status, retryCount, createdAt, publishedAt, lastError, version`). `AuthOutboxEventJpaRepository.findPendingForUpdate(int limit): List<AuthOutboxEventEntity>`, `.markPublished(UUID id, Instant publishedAt): int`, `.incrementRetry(UUID id, String error): int`, `.markFailed(UUID id, String error): int` — all consumed by Task 2 (repository injection) and Task 4 (relay job).

- [ ] **Step 1: Write the migration**

`V7__auth_outbox_events.sql`:

```sql
CREATE TABLE auth_outbox_events (
    id              UUID            NOT NULL,
    event_id        UUID            NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    payload         JSONB           NOT NULL,
    user_id         UUID,
    target_exchange VARCHAR(255),
    status          VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL,
    published_at    TIMESTAMPTZ,
    last_error      TEXT,
    version         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_auth_outbox_events   PRIMARY KEY (id),
    CONSTRAINT uq_auth_outbox_event_id UNIQUE      (event_id)
);

CREATE INDEX idx_auth_outbox_status_created_at ON auth_outbox_events (status, created_at);
```

- [ ] **Step 2: Write the entity**

`AuthOutboxEventEntity.java`:

```java
package com.example.authsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "auth_outbox_events",
    indexes = {
        @Index(name = "idx_auth_outbox_status_created_at", columnList = "status, created_at")
    }
)
public class AuthOutboxEventEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "target_exchange", length = 255)
    private String targetExchange;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error")
    private String lastError;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
```

`@JdbcTypeCode(SqlTypes.JSON)` (Hibernate 6, already the ORM version this Spring Boot 4 project uses — confirmed by the existing `jakarta.persistence`/Hibernate-6-style annotations elsewhere in this codebase) maps the `String` field to the `jsonb` column directly — the payload is stored and read back as a plain JSON string, which is what `AuthEventPublisher` (Task 2) will hand it after calling `ObjectMapper.writeValueAsString(...)` itself; no Hibernate-side (de)serialization into a Java object is needed since nothing ever reads the payload back as a typed object.

- [ ] **Step 3: Write the repository**

`AuthOutboxEventJpaRepository.java`:

```java
package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.AuthOutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuthOutboxEventJpaRepository extends JpaRepository<AuthOutboxEventEntity, UUID> {

    @Query(value = "SELECT * FROM auth_outbox_events WHERE status = 'PENDING' " +
                   "ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<AuthOutboxEventEntity> findPendingForUpdate(@Param("limit") int limit);

    @Modifying
    @Query("UPDATE AuthOutboxEventEntity e SET e.status = 'PUBLISHED', e.publishedAt = :publishedAt WHERE e.id = :id")
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);

    @Modifying
    @Query("UPDATE AuthOutboxEventEntity e SET e.retryCount = e.retryCount + 1, e.lastError = :error WHERE e.id = :id")
    int incrementRetry(@Param("id") UUID id, @Param("error") String error);

    @Modifying
    @Query("UPDATE AuthOutboxEventEntity e SET e.status = 'FAILED', e.lastError = :error WHERE e.id = :id")
    int markFailed(@Param("id") UUID id, @Param("error") String error);
}
```

- [ ] **Step 4: Write a repository test proving the mapping and mutation methods work**

`AuthOutboxEventJpaRepositoryTest.java` — this test needs a real Postgres to exercise the native `FOR UPDATE SKIP LOCKED` query and JSONB column mapping (an H2/mocked repository can't prove either). Follow the exact Testcontainers convention already established in `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/config/RsaKeyConfigDbKeyIntegrationTest.java` — hand-built `EntityManagerFactory` + `JpaRepositoryFactory` against a `PostgreSQLContainer`, `Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), ...)` before starting the container, manual container lifecycle (no `@Testcontainers`/`@Container` annotations), Flyway migrate from `classpath:db/migration` in `@BeforeAll`, `hibernate.hbm2ddl.auto=validate`. Read that file in full before writing this one — copy its `@BeforeAll`/`@AfterAll` structure verbatim, only the entity/repository under test and the assertions change:

```java
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
```

- [ ] **Step 5: Run the test**

Run: `./gradlew :gen-auth-starter:test --tests "*AuthOutboxEventJpaRepositoryTest*"` from the repo root (`C:\Users\naksh\Desktop\Metaupspace\Gen_MS\Gen_AUTH`).
Expected: PASS (or SKIPPED if Docker is unavailable in the execution environment — check the log for the `Assumptions.assumeTrue` skip message rather than treating a skip as a failure).

- [ ] **Step 6: Commit**

```bash
git add gen-auth-starter/src/main/resources/db/migration/genauth/V7__auth_outbox_events.sql gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/entity/AuthOutboxEventEntity.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/repository/AuthOutboxEventJpaRepository.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/persistence/repository/AuthOutboxEventJpaRepositoryTest.java
git commit -m "add auth_outbox_events schema, entity, repository"
```

---

### Task 2: Rewrite `AuthEventPublisher` internals to write outbox rows

**Files:**
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/producer/AuthEventPublisher.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/constant/AuthExchangeConstants.java`
- Delete: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/ImpersonationEndedEvent.java`
- Modify (rewrite): `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/messaging/producer/AuthEventPublisherTest.java`
- Modify: `gen-auth-starter/src/test/java/com/example/authsvc/config/MessagingConfigTest.java`

**Interfaces:**
- Consumes: `AuthOutboxEventJpaRepository` (Task 1, injected via constructor), `com.fasterxml.jackson.databind.ObjectMapper` (Spring Boot auto-configures a default bean — same type `RedisOAuthSignupChallengeStore` already consumes elsewhere in this codebase).
- Produces: `AuthEventPublisher`'s new constructor `AuthEventPublisher(AuthOutboxEventJpaRepository outboxRepository, ObjectMapper objectMapper)` — Task 3 adds fields/methods to this same class and must use this exact constructor shape (Task 3's new `AuditRoutingProperties` dependency gets added to this constructor in Task 3, not here).

- [ ] **Step 1: Read the current test file style to preserve it**

Read `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/messaging/producer/AuthEventPublisherTest.java` in full (already known: `@ExtendWith(MockitoExtension.class)`, `@Mock` fields, plain `new AuthEventPublisher(...)` construction in `@BeforeEach`, `ArgumentCaptor` + AssertJ `assertThat`). The rewrite below preserves this exact style — only the mocked collaborator changes (`RabbitTemplate` → `AuthOutboxEventJpaRepository`).

- [ ] **Step 2: Write the failing rewritten test**

Replace `AuthEventPublisherTest.java` in full:

```java
package com.example.authsvc.infrastructure.messaging.producer;

import com.example.authsvc.infrastructure.messaging.constant.AuthExchangeConstants;
import com.example.authsvc.infrastructure.persistence.entity.AuthOutboxEventEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthOutboxEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthEventPublisherTest {

    @Mock private AuthOutboxEventJpaRepository outboxRepository;

    private AuthEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AuthEventPublisher(outboxRepository, new ObjectMapper());
    }

    @Test
    void publishLoginSuccess_writesPendingOutboxRowWithBusinessExchange() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        publisher.publishLoginSuccess(userId, tenantId, sessionId, "1.2.3.4", "curl/8.0");

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        AuthOutboxEventEntity saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(AuthExchangeConstants.RK_LOGIN_SUCCESS);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getTargetExchange()).isNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getPayload()).contains(userId.toString()).contains(tenantId.toString());
        assertThat(saved.getEventId()).isNotNull();
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void publishLoginFailed_hashesEmailInsteadOfStoringRaw() {
        publisher.publishLoginFailed("User@Example.com", "1.2.3.4", "INVALID_CREDENTIALS");

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload())
                .doesNotContain("user@example.com")
                .doesNotContain("User@Example.com");
    }

    @Test
    void publishLogout_writesOutboxRowWithLogoutRoutingKey() {
        UUID userId = UUID.randomUUID();
        publisher.publishLogout(userId, UUID.randomUUID());

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuthExchangeConstants.RK_LOGOUT);
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void publishPasswordChanged_writesOutboxRowWithPasswordChangedRoutingKey() {
        publisher.publishPasswordChanged(UUID.randomUUID(), UUID.randomUUID(), java.time.Instant.now());

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuthExchangeConstants.RK_PASSWORD_CHANGED);
    }

    @Test
    void publishImpersonationStarted_writesOutboxRowWithImpersonationStartedRoutingKey() {
        publisher.publishImpersonationStarted(UUID.randomUUID(), UUID.randomUUID(), "support", java.time.Instant.now());

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuthExchangeConstants.RK_IMPERSONATION_STARTED);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "*AuthEventPublisherTest*"`
Expected: FAIL — `AuthEventPublisher`'s current constructor takes `(RabbitTemplate, MessagingProperties)`, not `(AuthOutboxEventJpaRepository, ObjectMapper)`.

- [ ] **Step 4: Rewrite `AuthEventPublisher`'s internals**

Replace `AuthEventPublisher.java` in full:

```java
package com.example.authsvc.infrastructure.messaging.producer;

import com.example.authsvc.infrastructure.messaging.constant.AuthExchangeConstants;
import com.example.authsvc.infrastructure.messaging.event.ImpersonationStartedEvent;
import com.example.authsvc.infrastructure.messaging.event.LoginFailedEvent;
import com.example.authsvc.infrastructure.messaging.event.LoginSuccessEvent;
import com.example.authsvc.infrastructure.messaging.event.LogoutEvent;
import com.example.authsvc.infrastructure.messaging.event.PasswordChangedEvent;
import com.example.authsvc.infrastructure.persistence.entity.AuthOutboxEventEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthOutboxEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Only registered when {@code app.messaging.enabled=true} — see
 * {@code MessagingConfig} for the companion exchange beans. Every
 * {@code publishX}/{@code publishAuditX} method writes a {@code PENDING}
 * row to {@code auth_outbox_events} instead of publishing to RabbitMQ
 * directly — {@link com.example.authsvc.infrastructure.messaging.outbox.AuthOutboxRelayJob}
 * (a separate scheduled component) drains the table with retry/failure
 * handling. This means a broker outage no longer silently loses events:
 * rows accumulate as {@code PENDING} until the relay job can deliver them.
 *
 * <p>Serialization failures ARE propagated (not swallowed) — a payload that
 * cannot be serialized never gets a row, and the caller's own transaction
 * fails loudly rather than silently proceeding with a lost event.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AuthEventPublisher {

    private final AuthOutboxEventJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void publishLoginSuccess(UUID userId, UUID tenantId, UUID sessionId,
                                    String ip, String userAgent) {
        LoginSuccessEvent event = new LoginSuccessEvent(
                userId, tenantId, sessionId, ip, userAgent, Instant.now());
        enqueue(AuthExchangeConstants.RK_LOGIN_SUCCESS, event, userId, null);
    }

    /**
     * Email is SHA-256 hashed before inclusion in the event payload.
     * Raw PII must never appear in event messages.
     */
    public void publishLoginFailed(String email, String ip, String reason) {
        LoginFailedEvent event = new LoginFailedEvent(
                sha256(email), ip, reason, Instant.now());
        enqueue(AuthExchangeConstants.RK_LOGIN_FAILED, event, null, null);
    }

    public void publishLogout(UUID userId, UUID sessionId) {
        LogoutEvent event = new LogoutEvent(userId, sessionId, Instant.now());
        enqueue(AuthExchangeConstants.RK_LOGOUT, event, userId, null);
    }

    public void publishPasswordChanged(UUID userId, UUID sessionId, Instant timestamp) {
        PasswordChangedEvent event = new PasswordChangedEvent(userId, sessionId, timestamp);
        enqueue(AuthExchangeConstants.RK_PASSWORD_CHANGED, event, userId, null);
    }

    public void publishImpersonationStarted(UUID superAdminId, UUID tenantId,
                                             String reason, Instant expiresAt) {
        ImpersonationStartedEvent event = new ImpersonationStartedEvent(
                superAdminId, tenantId, reason, expiresAt, Instant.now());
        enqueue(AuthExchangeConstants.RK_IMPERSONATION_STARTED, event, superAdminId, null);
    }

    /**
     * Serializes {@code payload}, builds a {@code PENDING} outbox row, and saves it.
     * {@code targetExchange = null} means "publish to the default business exchange"
     * at relay time; a non-null value (used by the audit-tier methods added in a
     * later change to this class) overrides to a specific exchange.
     */
    void enqueue(String routingKey, Object payload, UUID userId, String targetExchange) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("event.serialize_failed routingKey={} eventType={} reason={}",
                    routingKey, payload.getClass().getSimpleName(), e.getMessage(), e);
            throw new IllegalStateException("Failed to serialize event payload for routingKey=" + routingKey, e);
        }

        AuthOutboxEventEntity row = AuthOutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .eventType(routingKey)
                .payload(json)
                .userId(userId)
                .targetExchange(targetExchange)
                .status("PENDING")
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        outboxRepository.save(row);
        log.info("event.enqueued routingKey={} eventType={} targetExchange={}",
                routingKey, payload.getClass().getSimpleName(), targetExchange);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    input.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JVM spec — this can never happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
```

Note: `enqueue(...)` is package-private (not `private`), not `public` either — Task 3 adds audit-tier methods to this same class in the same package, and they call this same helper directly as a sibling method, so `private` would work too since Task 3 modifies the same file. Keep it `private` unless Task 3's implementer finds a structural reason to change it (there shouldn't be one, since Task 3's new methods live in the same class).

- [ ] **Step 5: Delete `publishImpersonationEnded`'s now-dead pieces**

Delete the file `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/ImpersonationEndedEvent.java` entirely (the rewritten `AuthEventPublisher.java` above already omits the `publishImpersonationEnded` method and its import).

In `AuthExchangeConstants.java`, remove the now-unused constant:

```java
    public static final String RK_IMPERSONATION_ENDED   = "auth.impersonation.ended";
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "*AuthEventPublisherTest*"`
Expected: PASS.

- [ ] **Step 7: Fix `MessagingConfigTest` for the new constructor dependency**

Read `gen-auth-starter/src/test/java/com/example/authsvc/config/MessagingConfigTest.java` in full first (already known content: `ApplicationContextRunner` with `withUserConfiguration(PropertiesTestConfig.class, MessagingConfig.class, AuthEventPublisher.class)`). It currently relies on `RabbitAutoConfiguration` providing a `RabbitTemplate` bean for `AuthEventPublisher`'s old constructor — that dependency is gone. Replace the whole file:

```java
package com.example.authsvc.config;

import com.example.authsvc.config.properties.MessagingProperties;
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import com.example.authsvc.infrastructure.persistence.repository.AuthOutboxEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MessagingConfigTest {

    @Configuration
    @ConfigurationPropertiesScan("com.example.authsvc.config.properties")
    static class PropertiesTestConfig {}

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
            .withBean(AuthOutboxEventJpaRepository.class, () -> mock(AuthOutboxEventJpaRepository.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(PropertiesTestConfig.class, MessagingConfig.class, AuthEventPublisher.class);

    @Test
    void messagingDisabled_noExchangeOrPublisherBean() {
        contextRunner
                .withPropertyValues("app.messaging.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TopicExchange.class);
                    assertThat(context).doesNotHaveBean(AuthEventPublisher.class);
                });
    }

    @Test
    void messagingEnabled_exchangeAndPublisherBeanPresent() {
        contextRunner
                .withPropertyValues("app.messaging.enabled=true", "app.messaging.exchange=auth.events")
                .run(context -> {
                    assertThat(context).hasSingleBean(TopicExchange.class);
                    assertThat(context.getBean(TopicExchange.class).getName()).isEqualTo("auth.events");
                    assertThat(context).hasSingleBean(AuthEventPublisher.class);
                    assertThat(context.getBean(MessageConverter.class)).isInstanceOf(JacksonJsonMessageConverter.class);
                });
    }
}
```

(This test does NOT yet assert on the two new audit exchange beans — that assertion is added in Task 3, which is the task that actually creates them. Adding it here would fail until Task 3 lands.)

- [ ] **Step 8: Run the fixed config test**

Run: `./gradlew :gen-auth-starter:test --tests "*MessagingConfigTest*"`
Expected: PASS.

- [ ] **Step 9: Run the full module suite**

Run: `./gradlew :gen-auth-starter:test`
Expected: BUILD SUCCESSFUL — this task changes a class with 5 real call sites elsewhere in the codebase (Task 3 wires new calls into them, but they still call the unchanged public methods this task modified internally) and deletes a file; confirm nothing else broke.

- [ ] **Step 10: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/producer/AuthEventPublisher.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/constant/AuthExchangeConstants.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/messaging/producer/AuthEventPublisherTest.java gen-auth-starter/src/test/java/com/example/authsvc/config/MessagingConfigTest.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/ImpersonationEndedEvent.java
git commit -m "route AuthEventPublisher through the outbox table instead of publishing directly"
```

---

### Task 3: Audit-tier classification + wiring into real call sites

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/properties/AuditRoutingProperties.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/AuditLoginSuccessEvent.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/AuditLoginFailedEvent.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/AuditLogoutEvent.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/AuditPasswordChangedEvent.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/producer/AuthEventPublisher.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/constant/AuthExchangeConstants.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/config/MessagingConfig.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/LoginExecutionServiceImpl.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/RefreshTokenServiceImpl.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ChangePasswordServiceImpl.java`
- Modify: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/messaging/producer/AuthEventPublisherTest.java`
- Modify: `gen-auth-starter/src/test/java/com/example/authsvc/config/MessagingConfigTest.java`

**Interfaces:**
- Consumes: `AuthEventPublisher.enqueue(String, Object, UUID, String)` (Task 2, package-private, same class), `TenantConstants.PLATFORM_TENANT_ID` (existing, from `com.example.authsvc.domain.TenantConstants`).
- Produces: `AuthEventPublisher.publishAuditTenantLoginSuccess(UUID userId, UUID tenantId, Instant occurredAt)`, `.publishAuditTenantLoginFailed(UUID tenantId, Instant occurredAt, String reason)`, `.publishAuditPlatformLoginSuccess(UUID userId, Instant occurredAt)`, `.publishAuditPlatformLoginFailed(Instant occurredAt, String reason)`, `.publishAuditLogout(UUID userId, UUID tenantId, Instant occurredAt)`, `.publishAuditPasswordChanged(UUID userId, UUID tenantId, Instant occurredAt)` — no other task consumes these; they're called only from the 3 service-impl call sites this task wires.

- [ ] **Step 1: Write `AuditRoutingProperties`**

```java
package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Exchange names for two-tier audit-event routing, bound from
 * {@code app.messaging.audit.*}. Only read when {@code app.messaging.enabled=true}.
 * Defaults are generic — a host app wanting exact CPMS-Platform parity sets
 * {@code tenant-exchange=cpms.audit} / {@code platform-exchange=cpms.platform.audit}.
 */
@Data
@ConfigurationProperties(prefix = "app.messaging.audit")
public class AuditRoutingProperties {

    private String tenantExchange = "auth.audit.tenant";
    private String platformExchange = "auth.audit.platform";
}
```

- [ ] **Step 2: Write the four audit event payload records**

`AuditLoginSuccessEvent.java`:
```java
package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record AuditLoginSuccessEvent(UUID userId, UUID tenantId, Instant occurredAt) {}
```

`AuditLoginFailedEvent.java`:
```java
package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record AuditLoginFailedEvent(UUID tenantId, Instant occurredAt, String reason) {}
```

`AuditLogoutEvent.java`:
```java
package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record AuditLogoutEvent(UUID userId, UUID tenantId, Instant occurredAt) {}
```

`AuditPasswordChangedEvent.java`:
```java
package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record AuditPasswordChangedEvent(UUID userId, UUID tenantId, Instant occurredAt) {}
```

- [ ] **Step 3: Add the four new routing-key constants**

In `AuthExchangeConstants.java`, add:

```java
    public static final String RK_AUDIT_TENANT_LOGIN_SUCCESS   = "auth.tenant.login.success";
    public static final String RK_AUDIT_TENANT_LOGIN_FAILED    = "auth.tenant.login.failed";
    public static final String RK_AUDIT_PLATFORM_LOGIN_SUCCESS = "auth.superadmin.login.success";
    public static final String RK_AUDIT_PLATFORM_LOGIN_FAILED  = "auth.superadmin.login.failed";
    public static final String RK_AUDIT_LOGOUT                 = "auth.logout";
    public static final String RK_AUDIT_PASSWORD_CHANGED       = "auth.password.changed";
```

(`RK_AUDIT_LOGOUT`/`RK_AUDIT_PASSWORD_CHANGED` are deliberately the same string values as the existing `RK_LOGOUT`/`RK_PASSWORD_CHANGED` business routing keys — this matches CPMS's real routing-key reuse: the audit-tier row and the business row for the same logical event share a routing key, they just go to different exchanges with different payload shapes. Do not deduplicate these constants into one — the two constant names document two distinct call sites/payload types even though the string values happen to match today.)

- [ ] **Step 4: Write the failing tests for the new audit methods**

Add to `AuthEventPublisherTest.java` (append these test methods to the file from Task 2 — do not remove the existing 5):

```java
    @Test
    void publishAuditTenantLoginSuccess_writesOutboxRowTargetingTenantAuditExchange() {
        AuditRoutingProperties routing = new AuditRoutingProperties();
        publisher = new AuthEventPublisher(outboxRepository, new ObjectMapper(), routing);

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        publisher.publishAuditTenantLoginSuccess(userId, tenantId, java.time.Instant.now());

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuthExchangeConstants.RK_AUDIT_TENANT_LOGIN_SUCCESS);
        assertThat(captor.getValue().getTargetExchange()).isEqualTo("auth.audit.tenant");
    }

    @Test
    void publishAuditPlatformLoginFailed_writesOutboxRowTargetingPlatformAuditExchange() {
        AuditRoutingProperties routing = new AuditRoutingProperties();
        routing.setPlatformExchange("cpms.platform.audit");
        publisher = new AuthEventPublisher(outboxRepository, new ObjectMapper(), routing);

        publisher.publishAuditPlatformLoginFailed(java.time.Instant.now(), "INVALID_CREDENTIALS");

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuthExchangeConstants.RK_AUDIT_PLATFORM_LOGIN_FAILED);
        assertThat(captor.getValue().getTargetExchange()).isEqualTo("cpms.platform.audit");
    }

    @Test
    void publishAuditLogout_writesOutboxRowTargetingTenantAuditExchange() {
        AuditRoutingProperties routing = new AuditRoutingProperties();
        publisher = new AuthEventPublisher(outboxRepository, new ObjectMapper(), routing);

        UUID userId = UUID.randomUUID();
        publisher.publishAuditLogout(userId, UUID.randomUUID(), java.time.Instant.now());

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuthExchangeConstants.RK_AUDIT_LOGOUT);
        assertThat(captor.getValue().getTargetExchange()).isEqualTo("auth.audit.tenant");
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void publishAuditPasswordChanged_writesOutboxRowTargetingTenantAuditExchange() {
        AuditRoutingProperties routing = new AuditRoutingProperties();
        publisher = new AuthEventPublisher(outboxRepository, new ObjectMapper(), routing);

        publisher.publishAuditPasswordChanged(UUID.randomUUID(), UUID.randomUUID(), java.time.Instant.now());

        ArgumentCaptor<AuthOutboxEventEntity> captor = ArgumentCaptor.forClass(AuthOutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuthExchangeConstants.RK_AUDIT_PASSWORD_CHANGED);
        assertThat(captor.getValue().getTargetExchange()).isEqualTo("auth.audit.tenant");
    }
```

Add the import `import com.example.authsvc.config.properties.AuditRoutingProperties;` to the test file, and update the existing `@BeforeEach setUp()`'s `publisher = new AuthEventPublisher(outboxRepository, new ObjectMapper());` call to pass a third `new AuditRoutingProperties()` argument — the constructor signature changes in this task (Step 6 below), so this line must add the extra argument or the whole test file fails to compile. The 5 test methods from Task 2 don't construct `publisher` themselves (only `setUp()` does) so they need no changes; the 4 new tests below construct their own local `publisher` instance (shadowing the `setUp()`-assigned field) specifically to vary `AuditRoutingProperties` per test.

- [ ] **Step 5: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "*AuthEventPublisherTest*"`
Expected: FAIL — compile error, `publishAuditTenantLoginSuccess` etc. don't exist yet, and the 3-arg constructor doesn't exist yet.

- [ ] **Step 6: Add the audit methods to `AuthEventPublisher`**

Modify `AuthEventPublisher.java`:
- Add field `private final AuditRoutingProperties auditRoutingProperties;` and add the import `com.example.authsvc.config.properties.AuditRoutingProperties`.
- Since the class uses `@RequiredArgsConstructor`, adding this new `final` field automatically extends the generated constructor to `AuthEventPublisher(AuthOutboxEventJpaRepository, ObjectMapper, AuditRoutingProperties)` — no manual constructor edit needed.
- Add the six new public methods and one new private helper, appended after `publishImpersonationStarted`:

```java
    public void publishAuditTenantLoginSuccess(UUID userId, UUID tenantId, Instant occurredAt) {
        AuditLoginSuccessEvent event = new AuditLoginSuccessEvent(userId, tenantId, occurredAt);
        enqueue(AuthExchangeConstants.RK_AUDIT_TENANT_LOGIN_SUCCESS, event, userId,
                auditRoutingProperties.getTenantExchange());
    }

    public void publishAuditTenantLoginFailed(UUID tenantId, Instant occurredAt, String reason) {
        AuditLoginFailedEvent event = new AuditLoginFailedEvent(tenantId, occurredAt, reason);
        enqueue(AuthExchangeConstants.RK_AUDIT_TENANT_LOGIN_FAILED, event, null,
                auditRoutingProperties.getTenantExchange());
    }

    public void publishAuditPlatformLoginSuccess(UUID userId, Instant occurredAt) {
        AuditLoginSuccessEvent event = new AuditLoginSuccessEvent(userId, null, occurredAt);
        enqueue(AuthExchangeConstants.RK_AUDIT_PLATFORM_LOGIN_SUCCESS, event, userId,
                auditRoutingProperties.getPlatformExchange());
    }

    public void publishAuditPlatformLoginFailed(Instant occurredAt, String reason) {
        AuditLoginFailedEvent event = new AuditLoginFailedEvent(null, occurredAt, reason);
        enqueue(AuthExchangeConstants.RK_AUDIT_PLATFORM_LOGIN_FAILED, event, null,
                auditRoutingProperties.getPlatformExchange());
    }

    public void publishAuditLogout(UUID userId, UUID tenantId, Instant occurredAt) {
        AuditLogoutEvent event = new AuditLogoutEvent(userId, tenantId, occurredAt);
        enqueue(AuthExchangeConstants.RK_AUDIT_LOGOUT, event, userId,
                auditRoutingProperties.getTenantExchange());
    }

    public void publishAuditPasswordChanged(UUID userId, UUID tenantId, Instant occurredAt) {
        AuditPasswordChangedEvent event = new AuditPasswordChangedEvent(userId, tenantId, occurredAt);
        enqueue(AuthExchangeConstants.RK_AUDIT_PASSWORD_CHANGED, event, userId,
                auditRoutingProperties.getTenantExchange());
    }
```

Add imports for the four new event records (`AuditLoginSuccessEvent`, `AuditLoginFailedEvent`, `AuditLogoutEvent`, `AuditPasswordChangedEvent`).

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "*AuthEventPublisherTest*"`
Expected: PASS.

- [ ] **Step 8: Add the two new exchange beans to `MessagingConfig`**

Modify `MessagingConfig.java`:
- Add `@EnableScheduling` to the class-level annotations (`import org.springframework.scheduling.annotation.EnableScheduling;`) — this is the change that activates Task 4's `@Scheduled` relay job; it belongs here because `MessagingConfig` is only loaded when `app.messaging.enabled=true`, the same condition the relay job itself needs.
- Add `AuditRoutingProperties` as a constructor dependency (the class uses `@RequiredArgsConstructor` already, so add the field and it's automatic) and its import.
- Add two new `@Bean` methods:

```java
    @Bean
    public TopicExchange auditTenantExchange() {
        return new TopicExchange(auditRoutingProperties.getTenantExchange());
    }

    @Bean
    public FanoutExchange auditPlatformExchange() {
        return new FanoutExchange(auditRoutingProperties.getPlatformExchange());
    }
```

Add the import `org.springframework.amqp.core.FanoutExchange` (`TopicExchange` is already imported).

- [ ] **Step 9: Update `MessagingConfigTest` for the new beans and constructor dependency**

In `MessagingConfigTest.java`: add `.withBean(AuditRoutingProperties.class, AuditRoutingProperties::new)` to the `contextRunner` (needed since `MessagingConfig`'s constructor now requires it), add the import, and extend `messagingEnabled_exchangeAndPublisherBeanPresent` with:

```java
                    assertThat(context).hasSingleBean(FanoutExchange.class);
                    assertThat(context.getBean(FanoutExchange.class).getName()).isEqualTo("auth.audit.platform");
```

(add `import org.springframework.amqp.core.FanoutExchange;`). Also assert the tenant `TopicExchange` count is now 2 beans total (`authEventsExchange` + `auditTenantExchange`, both `TopicExchange`) rather than asserting `hasSingleBean(TopicExchange.class)` — change that assertion to check both exchange names are present among all `TopicExchange` beans instead:

```java
                    java.util.Collection<TopicExchange> topicExchanges = context.getBeansOfType(TopicExchange.class).values();
                    assertThat(topicExchanges).extracting(TopicExchange::getName)
                            .containsExactlyInAnyOrder("auth.events", "auth.audit.tenant");
```

- [ ] **Step 10: Run the config test**

Run: `./gradlew :gen-auth-starter:test --tests "*MessagingConfigTest*"`
Expected: PASS.

- [ ] **Step 11: Wire the new audit calls into `LoginExecutionServiceImpl`**

In `LoginExecutionServiceImpl.java`, inside the existing async block around line 234-238 (the `asyncExecutor.execute(() -> { if (authEventPublisher != null) { authEventPublisher.publishLoginSuccess(...); } });` block), add the audit-tier call in the SAME null-checked block, branching on whether this is a platform (super-admin) login by checking `fTenantId` against `TenantConstants.PLATFORM_TENANT_ID` (the existing sentinel — a super-admin's `AuthUserEntity.tenantId` is documented to equal this sentinel):

```java
        asyncExecutor.execute(() -> {
            if (authEventPublisher != null) {
                authEventPublisher.publishLoginSuccess(fUserId, fTenantId, fSessionId, fIp, fUa);
                if (com.example.authsvc.domain.TenantConstants.PLATFORM_TENANT_ID.equals(fTenantId)) {
                    authEventPublisher.publishAuditPlatformLoginSuccess(fUserId, Instant.now());
                } else {
                    authEventPublisher.publishAuditTenantLoginSuccess(fUserId, fTenantId, Instant.now());
                }
            }
        });
```

(Use a proper import `import com.example.authsvc.domain.TenantConstants;` at the top of the file instead of the fully-qualified reference shown above — the inline qualification above is only to make the exact insertion point unambiguous in this plan text.)

In `handleFailure(...)` (around line 261-270), apply the same `tenantId`-based branch to the existing `asyncExecutor.execute(() -> { if (authEventPublisher != null) { authEventPublisher.publishLoginFailed(...); } });` block:

```java
        asyncExecutor.execute(() -> {
            if (authEventPublisher != null) {
                authEventPublisher.publishLoginFailed(email, ip, reason);
                if (TenantConstants.PLATFORM_TENANT_ID.equals(tenantId)) {
                    authEventPublisher.publishAuditPlatformLoginFailed(Instant.now(), reason);
                } else {
                    authEventPublisher.publishAuditTenantLoginFailed(tenantId, Instant.now(), reason);
                }
            }
        });
```

Add `import com.example.authsvc.domain.TenantConstants;` to this file's imports.

- [ ] **Step 12: Wire the new audit call into `RefreshTokenServiceImpl`**

In `RefreshTokenServiceImpl.java`, all four existing `if (authEventPublisher != null) { authEventPublisher.publishLogout(...); }` blocks get one line added each, calling `publishAuditLogout` with whatever `tenantId` is available at that call site (all four sites are TENANT-tier only per CPMS's real router — logout has no platform-tier audit variant, so no branching is needed here, unlike login):

Call site 1 (natural expiry, inside `refresh()`, has `auditRecord: AuthRefreshTokenEntity` in scope):
```java
                            if (authEventPublisher != null) {
                                authEventPublisher.publishLogout(auditRecord.getUserId(), auditRecord.getSessionId());
                                authEventPublisher.publishAuditLogout(
                                        auditRecord.getUserId(), auditRecord.getTenantId(), Instant.now());
                            }
```

Call site 2 (absolute expiry, inside `refresh()`, has `cached: RefreshToken` in scope):
```java
                if (authEventPublisher != null) {
                    authEventPublisher.publishLogout(cached.getUserId(), cached.getSessionId());
                    authEventPublisher.publishAuditLogout(
                            cached.getUserId(), cached.getTenantId(), Instant.now());
                }
```

Call site 3 (replay attack, inside private `handleReplayAttack(...)`, has `auditRecord: AuthRefreshTokenEntity` in scope):
```java
            if (authEventPublisher != null) {
                authEventPublisher.publishLogout(auditRecord.getUserId(), auditRecord.getSessionId());
                authEventPublisher.publishAuditLogout(
                        auditRecord.getUserId(), auditRecord.getTenantId(), Instant.now());
            }
```

Call site 4 (explicit `logout(...)`, has `cached: RefreshToken` and an in-scope `now` variable):
```java
        if (authEventPublisher != null) {
            authEventPublisher.publishLogout(cached.getUserId(), cached.getSessionId());
            authEventPublisher.publishAuditLogout(cached.getUserId(), cached.getTenantId(), now);
        }
```

- [ ] **Step 13: Wire the new audit call into `ChangePasswordServiceImpl`**

In `ChangePasswordServiceImpl.java`, the existing block around line 88-90 (`if (authEventPublisher != null) { authEventPublisher.publishPasswordChanged(user.getId(), currentSessionId, now); }`) gets one line added — `now` and `user` (an `AuthUserEntity` with `getTenantId()`) are both already in scope:

```java
            if (authEventPublisher != null) {
                authEventPublisher.publishPasswordChanged(user.getId(), currentSessionId, now);
                authEventPublisher.publishAuditPasswordChanged(user.getId(), user.getTenantId(), now);
            }
```

- [ ] **Step 14: Compile and run the full module suite**

Run: `./gradlew :gen-auth-starter:test`
Expected: BUILD SUCCESSFUL — this task touches 3 service-impl files with existing test coverage (`LoginExecutionServiceImplTest`, `RefreshTokenServiceImplTest`, `ChangePasswordServiceImplTest` if they exist — check `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/` for their exact names) that may assert on `authEventPublisher` mock interactions; if any of those tests use strict/verify-no-more-interactions style Mockito assertions that would now fail because of the new extra `publishAudit*` call, update those specific assertions to also expect the new call (do not weaken the null-check-mock-not-invoked-when-disabled tests — only add expectations for the new calls where `authEventPublisher` is non-null in the test's setup).

- [ ] **Step 15: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/config/properties/AuditRoutingProperties.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/AuditLoginSuccessEvent.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/AuditLoginFailedEvent.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/AuditLogoutEvent.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/AuditPasswordChangedEvent.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/producer/AuthEventPublisher.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/constant/AuthExchangeConstants.java gen-auth-starter/src/main/java/com/example/authsvc/config/MessagingConfig.java gen-auth-starter/src/main/java/com/example/authsvc/application/impl/LoginExecutionServiceImpl.java gen-auth-starter/src/main/java/com/example/authsvc/application/impl/RefreshTokenServiceImpl.java gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ChangePasswordServiceImpl.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/messaging/producer/AuthEventPublisherTest.java gen-auth-starter/src/test/java/com/example/authsvc/config/MessagingConfigTest.java
git commit -m "add two-tier audit-exchange classification, wire into login/logout/password-changed"
```

---

### Task 4: `AuthOutboxRelayJob` — scheduled relay with retry

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/outbox/AuthOutboxRelayJob.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/messaging/outbox/AuthOutboxRelayJobTest.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/messaging/outbox/AuthOutboxRelayJobIntegrationTest.java`

**Interfaces:**
- Consumes: `AuthOutboxEventJpaRepository` (Task 1: `findPendingForUpdate`, `markPublished`, `incrementRetry`, `markFailed`), `MessagingProperties.getExchange()` (existing, default business exchange).
- Produces: nothing consumed by any other task (last task in this plan).

- [ ] **Step 1: Write the failing unit tests**

`AuthOutboxRelayJobTest.java`:

```java
package com.example.authsvc.infrastructure.messaging.outbox;

import com.example.authsvc.config.properties.MessagingProperties;
import com.example.authsvc.infrastructure.persistence.entity.AuthOutboxEventEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthOutboxEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthOutboxRelayJobTest {

    @Mock private AuthOutboxEventJpaRepository repository;
    @Mock private RabbitTemplate rabbitTemplate;

    private AuthOutboxRelayJob job(RabbitTemplate template) {
        MessagingProperties props = new MessagingProperties();
        props.setExchange("auth.events");
        AuthOutboxRelayJob job = new AuthOutboxRelayJob(repository, template, props);
        setBatchSize(job, 50);
        return job;
    }

    private static AuthOutboxEventEntity pendingRow(String targetExchange, int retryCount) {
        return AuthOutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .eventType("auth.login.success")
                .payload("{}")
                .targetExchange(targetExchange)
                .status("PENDING")
                .retryCount(retryCount)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void relay_nullRabbitTemplate_neverQueriesRepository() {
        AuthOutboxRelayJob job = job(null);
        job.relay();
        verifyNoInteractions(repository);
    }

    @Test
    void relay_sendSucceeds_marksPublishedWithMessageIdSetToEventId() {
        AuthOutboxEventEntity row = pendingRow(null, 0);
        when(repository.findPendingForUpdate(50)).thenReturn(List.of(row));

        job(rabbitTemplate).relay();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("auth.events"), eq("auth.login.success"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().getMessageProperties().getMessageId())
                .isEqualTo(row.getEventId().toString());
        verify(repository).markPublished(eq(row.getId()), any(Instant.class));
    }

    @Test
    void relay_nonNullTargetExchange_sendsToOverrideExchangeNotDefault() {
        AuthOutboxEventEntity row = pendingRow("auth.audit.tenant", 0);
        when(repository.findPendingForUpdate(50)).thenReturn(List.of(row));

        job(rabbitTemplate).relay();

        verify(rabbitTemplate).send(eq("auth.audit.tenant"), anyString(), any(Message.class));
    }

    @Test
    void relay_sendThrowsOnFirstAttempt_incrementsRetryNotFailed() {
        AuthOutboxEventEntity row = pendingRow(null, 0);
        when(repository.findPendingForUpdate(50)).thenReturn(List.of(row));
        doThrow(new RuntimeException("broker unreachable"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        job(rabbitTemplate).relay();

        verify(repository).incrementRetry(eq(row.getId()), anyString());
        verify(repository, never()).markFailed(any(), anyString());
    }

    @Test
    void relay_sendThrowsOnThirdAttempt_marksFailedNotRetry() {
        // retryCount == 2 means this is attempt #3 (0-indexed) — MAX_RETRIES=3, so this exhausts it.
        AuthOutboxEventEntity row = pendingRow(null, 2);
        when(repository.findPendingForUpdate(50)).thenReturn(List.of(row));
        doThrow(new RuntimeException("broker unreachable"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        job(rabbitTemplate).relay();

        verify(repository).markFailed(eq(row.getId()), anyString());
        verify(repository, never()).incrementRetry(any(), anyString());
    }

    private static void setBatchSize(AuthOutboxRelayJob job, int value) {
        try {
            var field = AuthOutboxRelayJob.class.getDeclaredField("batchSize");
            field.setAccessible(true);
            field.setInt(job, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "*AuthOutboxRelayJobTest*"`
Expected: FAIL — `AuthOutboxRelayJob` doesn't exist yet.

- [ ] **Step 3: Write `AuthOutboxRelayJob`**

```java
package com.example.authsvc.infrastructure.messaging.outbox;

import com.example.authsvc.config.properties.MessagingProperties;
import com.example.authsvc.infrastructure.persistence.entity.AuthOutboxEventEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthOutboxEventJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Drains {@code auth_outbox_events} to RabbitMQ. Gated the same as the rest of
 * messaging: {@code app.messaging.enabled=true}, and activated by
 * {@code @EnableScheduling} on {@code MessagingConfig}. {@link RabbitTemplate}
 * is optional — no broker configured means {@link #relay()} silently no-ops,
 * matching {@code AuthEventPublisher}'s existing "no broker, no crash" posture.
 *
 * <p>Retry semantics match CPMS's real implementation exactly: 3 total attempts,
 * no backoff beyond the fixed poll interval, {@code FAILED} on exhaustion with
 * no further automatic retry.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
public class AuthOutboxRelayJob {

    private static final int MAX_RETRIES = 3;

    private final AuthOutboxEventJpaRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messagingProperties;

    @Value("${app.messaging.outbox.batch-size:50}")
    private int batchSize;

    public AuthOutboxRelayJob(
            AuthOutboxEventJpaRepository repository,
            @Autowired(required = false) RabbitTemplate rabbitTemplate,
            MessagingProperties messagingProperties) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.messagingProperties = messagingProperties;
    }

    @Scheduled(fixedDelayString = "${app.messaging.outbox.relay-interval-ms:5000}")
    @Transactional
    public void relay() {
        if (rabbitTemplate == null) {
            return;
        }

        List<AuthOutboxEventEntity> batch = repository.findPendingForUpdate(batchSize);
        for (AuthOutboxEventEntity event : batch) {
            try {
                String exchange = event.getTargetExchange() != null
                        ? event.getTargetExchange()
                        : messagingProperties.getExchange();

                Message message = MessageBuilder
                        .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setMessageId(event.getEventId().toString())
                        .build();

                rabbitTemplate.send(exchange, event.getEventType(), message);
                repository.markPublished(event.getId(), Instant.now());
                log.info("outbox.relay.sent id={} eventType={} exchange={}",
                        event.getId(), event.getEventType(), exchange);
            } catch (Exception e) {
                if (event.getRetryCount() >= MAX_RETRIES - 1) {
                    repository.markFailed(event.getId(), e.getMessage());
                    log.error("outbox.relay.failed id={} eventType={} reason={}",
                            event.getId(), event.getEventType(), e.getMessage());
                } else {
                    repository.incrementRetry(event.getId(), e.getMessage());
                    log.warn("outbox.relay.retry id={} eventType={} retryCount={} reason={}",
                            event.getId(), event.getEventType(), event.getRetryCount() + 1, e.getMessage());
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "*AuthOutboxRelayJobTest*"`
Expected: PASS.

- [ ] **Step 5: Write the Testcontainers concurrency integration test**

This is the one claim a mocked unit test cannot prove: two concurrent transactions calling `findPendingForUpdate` each get disjoint rows (`SKIP LOCKED` working as intended). Follow the exact convention from `RsaKeyConfigDbKeyIntegrationTest.java` (hand-built `EntityManagerFactory`, manual container lifecycle, `Assumptions.assumeTrue` Docker check) — read that file again if needed, then write:

```java
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
```

- [ ] **Step 6: Run the integration test**

Run: `./gradlew :gen-auth-starter:test --tests "*AuthOutboxRelayJobIntegrationTest*"`
Expected: PASS (or SKIPPED if Docker unavailable).

- [ ] **Step 7: Run the full module suite**

Run: `./gradlew :gen-auth-starter:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/outbox/AuthOutboxRelayJob.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/messaging/outbox/AuthOutboxRelayJobTest.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/messaging/outbox/AuthOutboxRelayJobIntegrationTest.java
git commit -m "add AuthOutboxRelayJob scheduled outbox drain with retry/failure handling"
```

---

## Post-plan note for the final whole-branch reviewer

- Confirm `README.md` documents the new config flags (`app.messaging.outbox.relay-interval-ms`, `app.messaging.outbox.batch-size`, `app.messaging.audit.tenant-exchange`, `app.messaging.audit.platform-exchange`) and the behavior change (events are now outbox-backed, not fire-and-forget) — follow the format used in prior additions (see the HMAC internal-auth README diff for the format to match).
- Confirm `gen-auth-demo/src/main/resources/application.yaml` gets example entries for the new flags, mirroring how `app.internal-hmac-auth`/`app.otp` were added there.
- Check whether `LoginExecutionServiceImplTest`/`RefreshTokenServiceImplTest`/`ChangePasswordServiceImplTest` exist and needed updates in Task 3 Step 14 — if the task's implementer reported concerns there, resolve them before considering the branch clean.
- `AuthAuditLogEntity`/`AuditLogService`/`auth_audit_logs` were deliberately untouched by this entire plan — confirm the final diff has zero changes to those files (a scope-creep signal if any exist).
