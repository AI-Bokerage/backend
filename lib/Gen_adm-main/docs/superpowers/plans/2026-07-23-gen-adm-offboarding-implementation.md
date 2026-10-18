# Gen_ADM Phase 2 Offboarding Saga Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a resumable, retry-only user-offboarding saga to Gen_ADM: tenant-scoped job/step tracking, session revocation as a mandatory built-in step, a pluggable step-handler port for everything else, REST controllers reusing Phase 1's RBAC/permission/exception machinery.

**Architecture:** Additive to the existing `gen-adm-starter`/`gen-adm-demo` split — no changes to Phase 1's public surface. `OffboardingServiceImpl.initiate(...)` inserts one job row and one step row per registered step (session revocation always sequence 0), then registers a `TransactionSynchronization.afterCommit()` callback that invokes `OffboardingStepExecutor.execute(tenantId, jobId)` in-process, synchronously, once the transaction commits. The executor is idempotent and resumable: it skips COMPLETED steps and stops at the first failure, recording a linear-backoff `nextRetryAt`.

**Tech Stack:** Same as Phase 1 — Java 21, Spring Boot 4.0.6, Gradle 9.4.1, PostgreSQL, Flyway, Spring Data JPA, Spring AOP, Lombok, JUnit 5, Mockito, Testcontainers. No new dependencies.

## Global Constraints

- Package root stays `com.example.admsvc` — new code lives alongside Phase 1's, not in a separate module.
- `SessionRevocationGateway` (`domain/port`) has **no default bean, ever**. A consumer that omits an implementation must fail to start — this is the fix for the source `adm-svc` bug where `HttpAuthSessionRevocationGateway` was fully coded but never wired into the step executor. Never add a no-op fallback for this specific port.
- Retry-only failure model: `nextRetryAt = now + min(5 × attemptCount, 30) minutes`. No compensation, no rollback, anywhere in this plan.
- No scheduled retry/timeout sweeper (source's `OffboardingRetryScheduler`/`OffboardingTimeoutScheduler`) — explicitly out of scope per the approved spec. The only way a FAILED job progresses again is the manual `POST /api/v1/offboarding/{jobId}/retry` endpoint.
- Retrying a COMPLETED or PERMANENTLY_FAILED job is a no-op that returns the current job unchanged — not an error, not an exception.
- Cross-tenant job lookups surface as 404 (`GenAdmNotFoundException`), never 403 — same anti-enumeration convention as Phase 1.
- Reuse Phase 1's `TenantContextAspect`, `GenAdmPrincipal`, `@TenantIdParam`, `PermissionChecker`, and `GlobalExceptionHandler` completely unmodified. No new exception subclasses, no aspect changes — every new `@Transactional` class goes in `com.example.admsvc.application.impl` exactly like Phase 1's services, so the existing aspect pointcut already covers it.
- `OffboardingStepExecutor.execute(...)` and `OffboardingServiceImpl.retry(...)`/afterCommit-kickoff paths have no `GenAdmPrincipal`/HTTP request reliably in scope — pass `tenantId` explicitly via `@TenantIdParam`, exactly like Phase 1's `bootstrapTenant`. Normal request-driven methods (`initiate`, `getJob`) keep taking `tenantId` as an explicit first parameter used in the repository query itself (defense in depth alongside RLS), same as `RoleServiceImpl`.
- One final whole-branch review at the end of all tasks, not one per task — same convention as Phase 1.
- Known sandbox limitation carried forward from Phase 1: any Testcontainers-based test here can only be verified by code review in this sandbox (confirmed Docker-detection limitation, not a code defect). Task 3's `SessionRevocationGatewayRequiredTest` is a deliberate exception — it uses a plain `AnnotationConfigApplicationContext` with mocked repositories and needs no database, so it is expected to actually run and pass here.

---

## Task 1: Offboarding persistence — enums, entities, repositories, migrations

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/OffboardingJobStatus.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/OffboardingStepStatus.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/OffboardingJobEntity.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/OffboardingStepEntity.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/OffboardingJobRepository.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/OffboardingStepRepository.java`
- Create: `gen-adm-starter/src/main/resources/db/migration/genadm/V3__create_offboarding_tables.sql`
- Create: `gen-adm-starter/src/main/resources/db/migration/genadm/V4__enable_offboarding_rls.sql`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/OffboardingPersistenceIntegrationTest.java`

**Interfaces:**
- Consumes: nothing from existing code beyond Flyway's existing `classpath:db/migration/genadm` location (Task 2 of Phase 1) and the RLS pattern from Phase 1's `V2__enable_rls.sql`.
- Produces: `OffboardingJobEntity` (`UUID id`, `UUID tenantId`, `UUID userId`, `UUID initiatedBy`, `String reason`, `OffboardingJobStatus status`, `int attemptCount`, `int maxAttempts`, `Instant nextRetryAt`, `Instant completedAt`, `Long version`, `Instant createdAt`, `Instant updatedAt`), `OffboardingStepEntity` (`UUID id`, `UUID jobId`, `UUID tenantId`, `UUID userId`, `int sequence`, `String stepName`, `OffboardingStepStatus status`, `int attemptNumber`, `String errorMessage`, `Instant createdAt`, `Instant updatedAt`) — later tasks depend on these exact field names.
- Produces: `OffboardingJobRepository.findByIdAndTenantId(UUID, UUID): Optional<OffboardingJobEntity>` — Task 4's service calls this exact signature. `OffboardingStepRepository.findAllByJobIdOrderBySequenceAsc(UUID): List<OffboardingStepEntity>` — Task 3's executor calls this exact signature.

- [ ] **Step 1: Create the status enums**

`gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/OffboardingJobStatus.java`:
```java
package com.example.admsvc.domain.enums;

public enum OffboardingJobStatus {
    PENDING,
    COMPLETED,
    FAILED,
    PERMANENTLY_FAILED
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/OffboardingStepStatus.java`:
```java
package com.example.admsvc.domain.enums;

public enum OffboardingStepStatus {
    PENDING,
    COMPLETED,
    FAILED
}
```

- [ ] **Step 2: Write the failing persistence integration test first**

`gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/OffboardingPersistenceIntegrationTest.java`:
```java
package com.example.admsvc.infrastructure.persistence;

import com.example.admsvc.domain.enums.OffboardingJobStatus;
import com.example.admsvc.domain.enums.OffboardingStepStatus;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingStepEntity;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = OffboardingPersistenceIntegrationTest.TestApp.class)
class OffboardingPersistenceIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OffboardingJobRepository jobRepository;

    @Autowired
    private OffboardingStepRepository stepRepository;

    @Test
    void savesAndFindsAJobByIdAndTenant() {
        UUID tenantId = UUID.randomUUID();
        OffboardingJobEntity job = OffboardingJobEntity.builder()
                .tenantId(tenantId)
                .userId(UUID.randomUUID())
                .initiatedBy(UUID.randomUUID())
                .reason("left the company")
                .build();
        jobRepository.saveAndFlush(job);

        Optional<OffboardingJobEntity> found = jobRepository.findByIdAndTenantId(job.getId(), tenantId);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(OffboardingJobStatus.PENDING);
        assertThat(found.get().getAttemptCount()).isZero();
        assertThat(found.get().getMaxAttempts()).isEqualTo(5);
    }

    @Test
    void aJobIsInvisibleWhenLookedUpUnderTheWrongTenant() {
        UUID tenantId = UUID.randomUUID();
        OffboardingJobEntity job = OffboardingJobEntity.builder()
                .tenantId(tenantId)
                .userId(UUID.randomUUID())
                .initiatedBy(UUID.randomUUID())
                .reason("left the company")
                .build();
        jobRepository.saveAndFlush(job);

        Optional<OffboardingJobEntity> found = jobRepository.findByIdAndTenantId(job.getId(), UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    void savesStepsAndListsThemOrderedBySequence() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OffboardingJobEntity job = jobRepository.saveAndFlush(OffboardingJobEntity.builder()
                .tenantId(tenantId).userId(userId).initiatedBy(UUID.randomUUID()).reason("r").build());

        stepRepository.saveAndFlush(OffboardingStepEntity.builder()
                .jobId(job.getId()).tenantId(tenantId).userId(userId)
                .sequence(1).stepName("SECOND").build());
        stepRepository.saveAndFlush(OffboardingStepEntity.builder()
                .jobId(job.getId()).tenantId(tenantId).userId(userId)
                .sequence(0).stepName("SESSION_REVOCATION").build());

        List<OffboardingStepEntity> steps = stepRepository.findAllByJobIdOrderBySequenceAsc(job.getId());
        assertThat(steps).extracting(OffboardingStepEntity::getStepName)
                .containsExactly("SESSION_REVOCATION", "SECOND");
        assertThat(steps.get(0).getStatus()).isEqualTo(OffboardingStepStatus.PENDING);
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.persistence.OffboardingPersistenceIntegrationTest"`
Expected: FAIL to compile — the entities/repositories/migration don't exist yet.

- [ ] **Step 4: Create `OffboardingJobEntity`**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/OffboardingJobEntity.java`:
```java
package com.example.admsvc.infrastructure.persistence.entity;

import com.example.admsvc.domain.enums.OffboardingJobStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "offboarding_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OffboardingJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OffboardingJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = Instant.now();
        if (status == null) {
            status = OffboardingJobStatus.PENDING;
        }
        if (maxAttempts == 0) {
            maxAttempts = 5;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
```

- [ ] **Step 5: Create `OffboardingStepEntity`**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/OffboardingStepEntity.java`:
```java
package com.example.admsvc.infrastructure.persistence.entity;

import com.example.admsvc.domain.enums.OffboardingStepStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "offboarding_steps", uniqueConstraints = @UniqueConstraint(columnNames = { "job_id", "sequence" }))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OffboardingStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "step_name", nullable = false, length = 100)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OffboardingStepStatus status;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = Instant.now();
        if (status == null) {
            status = OffboardingStepStatus.PENDING;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
```

- [ ] **Step 6: Create the repositories**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/OffboardingJobRepository.java`:
```java
package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OffboardingJobRepository extends JpaRepository<OffboardingJobEntity, UUID> {

    Optional<OffboardingJobEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/OffboardingStepRepository.java`:
```java
package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.infrastructure.persistence.entity.OffboardingStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OffboardingStepRepository extends JpaRepository<OffboardingStepEntity, UUID> {

    List<OffboardingStepEntity> findAllByJobIdOrderBySequenceAsc(UUID jobId);
}
```

- [ ] **Step 7: Create the tables migration**

`gen-adm-starter/src/main/resources/db/migration/genadm/V3__create_offboarding_tables.sql`:
```sql
CREATE TABLE offboarding_jobs (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    user_id        UUID NOT NULL,
    initiated_by   UUID NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    status         VARCHAR(30) NOT NULL,
    attempt_count  INT NOT NULL DEFAULT 0,
    max_attempts   INT NOT NULL DEFAULT 5,
    next_retry_at  TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    version        BIGINT,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_offboarding_jobs_tenant_user ON offboarding_jobs(tenant_id, user_id);

CREATE TABLE offboarding_steps (
    id             UUID PRIMARY KEY,
    job_id         UUID NOT NULL REFERENCES offboarding_jobs(id) ON DELETE CASCADE,
    tenant_id      UUID NOT NULL,
    user_id        UUID NOT NULL,
    sequence       INT NOT NULL,
    step_name      VARCHAR(100) NOT NULL,
    status         VARCHAR(30) NOT NULL,
    attempt_number INT NOT NULL DEFAULT 0,
    error_message  TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_offboarding_steps_job_sequence UNIQUE (job_id, sequence)
);

CREATE INDEX idx_offboarding_steps_job ON offboarding_steps(job_id);
```

- [ ] **Step 8: Create the RLS migration**

`gen-adm-starter/src/main/resources/db/migration/genadm/V4__enable_offboarding_rls.sql`:
```sql
ALTER TABLE offboarding_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE offboarding_jobs FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_offboarding_jobs ON offboarding_jobs
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE offboarding_steps ENABLE ROW LEVEL SECURITY;
ALTER TABLE offboarding_steps FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_offboarding_steps ON offboarding_steps
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

- [ ] **Step 9: Run the test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.persistence.OffboardingPersistenceIntegrationTest"`
Expected: PASS (3 tests, 0 failures). Requires Docker running locally for Testcontainers.

- [ ] **Step 10: Run the full build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` — Phase 1's own tests are unaffected by these additive tables/migrations.

- [ ] **Step 11: Commit**

```bash
git add gen-adm-starter
git commit -m "feat: add offboarding job/step entities, repositories, and RLS migrations"
```

---

## Task 2: Offboarding domain ports

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/port/SessionRevocationGateway.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/port/OffboardingStepHandler.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/port/OffboardingEventPublisher.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/event/NoOpOffboardingEventPublisher.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/config/OffboardingConfig.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `SessionRevocationGateway.revoke(UUID tenantId, UUID userId): void` (no default bean — Task 3's executor constructor requires one), `OffboardingStepHandler.stepName(): String` / `.handle(UUID tenantId, UUID userId, UUID initiatedBy): void` (0+ beans, pluggable), `OffboardingEventPublisher.onCompleted(UUID tenantId, UUID userId): void` / `.onFailed(UUID tenantId, UUID userId, String reason): void` (default no-op bean via `@ConditionalOnMissingBean`) — Task 3's executor constructor injects all three.

No dedicated test for this task: the three interfaces have no logic to verify, and `NoOpOffboardingEventPublisher` is a trivial no-op with no branches. `SessionRevocationGateway`'s "no default bean" requirement is verified by Task 3's `SessionRevocationGatewayRequiredTest`, which needs `OffboardingStepExecutor` to exist first.

- [ ] **Step 1: Create `SessionRevocationGateway`**

`gen-adm-starter/src/main/java/com/example/admsvc/domain/port/SessionRevocationGateway.java`:
```java
package com.example.admsvc.domain.port;

import java.util.UUID;

/**
 * Revokes an offboarded user's active sessions. Mandatory — Gen_ADM ships no
 * default implementation and no no-op fallback, unlike source adm-svc where a
 * fully-coded {@code HttpAuthSessionRevocationGateway} existed but was never
 * actually invoked by the step executor. A consumer that omits a bean for
 * this port fails to start, by design.
 */
public interface SessionRevocationGateway {

    void revoke(UUID tenantId, UUID userId);
}
```

- [ ] **Step 2: Create `OffboardingStepHandler`**

`gen-adm-starter/src/main/java/com/example/admsvc/domain/port/OffboardingStepHandler.java`:
```java
package com.example.admsvc.domain.port;

import java.util.UUID;

/**
 * One pluggable offboarding step beyond the mandatory session-revocation
 * step. A consumer registers zero or more Spring beans implementing this
 * interface; {@code OffboardingServiceImpl} orders them after session
 * revocation in the order Spring injects the {@code List<OffboardingStepHandler>}.
 */
public interface OffboardingStepHandler {

    String stepName();

    void handle(UUID tenantId, UUID userId, UUID initiatedBy);
}
```

- [ ] **Step 3: Create `OffboardingEventPublisher`**

`gen-adm-starter/src/main/java/com/example/admsvc/domain/port/OffboardingEventPublisher.java`:
```java
package com.example.admsvc.domain.port;

import java.util.UUID;

/**
 * Optional synchronous hook for a consumer to react to an offboarding job's
 * outcome (e.g. fire its own event) without Gen_ADM owning a broker
 * dependency. Not an outbox — no persistence, no retry of the notification
 * itself. Defaults to a no-op ({@link com.example.admsvc.infrastructure.event.NoOpOffboardingEventPublisher}).
 */
public interface OffboardingEventPublisher {

    void onCompleted(UUID tenantId, UUID userId);

    void onFailed(UUID tenantId, UUID userId, String reason);
}
```

- [ ] **Step 4: Create the no-op default publisher**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/event/NoOpOffboardingEventPublisher.java`:
```java
package com.example.admsvc.infrastructure.event;

import com.example.admsvc.domain.port.OffboardingEventPublisher;

import java.util.UUID;

public class NoOpOffboardingEventPublisher implements OffboardingEventPublisher {

    @Override
    public void onCompleted(UUID tenantId, UUID userId) {
    }

    @Override
    public void onFailed(UUID tenantId, UUID userId, String reason) {
    }
}
```

- [ ] **Step 5: Register the default bean**

`gen-adm-starter/src/main/java/com/example/admsvc/config/OffboardingConfig.java`:
```java
package com.example.admsvc.config;

import com.example.admsvc.domain.port.OffboardingEventPublisher;
import com.example.admsvc.infrastructure.event.NoOpOffboardingEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OffboardingConfig {

    @Bean
    @ConditionalOnMissingBean(OffboardingEventPublisher.class)
    public OffboardingEventPublisher offboardingEventPublisher() {
        return new NoOpOffboardingEventPublisher();
    }
}
```

This class is discovered the same way `FlywayConfig`/`SecurityConfig` already are — `GenAdmAutoConfiguration`'s existing `@ComponentScan("com.example.admsvc")` reaches it; no change to `AutoConfiguration.imports` is needed.

- [ ] **Step 6: Run the full build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add gen-adm-starter
git commit -m "feat: add offboarding domain ports (session revocation, step handler, event publisher)"
```

---

## Task 3: OffboardingStepExecutor — resumable, retry-only engine

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/OffboardingStepExecutor.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/OffboardingStepExecutorTest.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/SessionRevocationGatewayRequiredTest.java`

**Interfaces:**
- Consumes: `OffboardingJobRepository`, `OffboardingStepRepository` (Task 1); `SessionRevocationGateway`, `OffboardingStepHandler`, `OffboardingEventPublisher` (Task 2); `TenantIdParam` (Phase 1 Task 3).
- Produces: `OffboardingStepExecutor.SESSION_REVOCATION_STEP` (public `String` constant, value `"SESSION_REVOCATION"`) and `.execute(@TenantIdParam UUID tenantId, UUID jobId): void` — Task 4's `OffboardingServiceImpl` calls both this exact constant and this exact method signature.

- [ ] **Step 1: Write the failing executor unit tests first**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/OffboardingStepExecutorTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.domain.enums.OffboardingJobStatus;
import com.example.admsvc.domain.enums.OffboardingStepStatus;
import com.example.admsvc.domain.port.OffboardingEventPublisher;
import com.example.admsvc.domain.port.OffboardingStepHandler;
import com.example.admsvc.domain.port.SessionRevocationGateway;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingStepEntity;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingStepRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OffboardingStepExecutorTest {

    private final OffboardingJobRepository jobRepository = mock(OffboardingJobRepository.class);
    private final OffboardingStepRepository stepRepository = mock(OffboardingStepRepository.class);
    private final SessionRevocationGateway sessionRevocationGateway = mock(SessionRevocationGateway.class);
    private final OffboardingEventPublisher eventPublisher = mock(OffboardingEventPublisher.class);

    private OffboardingJobEntity newJob(int attemptCount, int maxAttempts) {
        return OffboardingJobEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .initiatedBy(UUID.randomUUID())
                .reason("test")
                .status(OffboardingJobStatus.PENDING)
                .attemptCount(attemptCount)
                .maxAttempts(maxAttempts)
                .build();
    }

    private OffboardingStepEntity newStep(UUID jobId, int sequence, String stepName, OffboardingStepStatus status) {
        return OffboardingStepEntity.builder()
                .id(UUID.randomUUID())
                .jobId(jobId)
                .sequence(sequence)
                .stepName(stepName)
                .status(status)
                .attemptNumber(0)
                .build();
    }

    @Test
    void resumeSkipsCompletedStepsAndOnlyRunsRemaining() {
        OffboardingJobEntity job = newJob(0, 5);
        OffboardingStepEntity completedRevocation =
                newStep(job.getId(), 0, "SESSION_REVOCATION", OffboardingStepStatus.COMPLETED);
        OffboardingStepEntity pendingHandlerStep =
                newStep(job.getId(), 1, "TASK_X", OffboardingStepStatus.PENDING);

        OffboardingStepHandler handler = mock(OffboardingStepHandler.class);
        when(handler.stepName()).thenReturn("TASK_X");

        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(stepRepository.findAllByJobIdOrderBySequenceAsc(job.getId()))
                .thenReturn(List.of(completedRevocation, pendingHandlerStep));

        OffboardingStepExecutor executor = new OffboardingStepExecutor(
                jobRepository, stepRepository, sessionRevocationGateway, List.of(handler), eventPublisher);

        executor.execute(job.getTenantId(), job.getId());

        verify(sessionRevocationGateway, never()).revoke(any(), any());
        verify(handler).handle(job.getTenantId(), job.getUserId(), job.getInitiatedBy());
        assertThat(job.getStatus()).isEqualTo(OffboardingJobStatus.COMPLETED);
        assertThat(pendingHandlerStep.getStatus()).isEqualTo(OffboardingStepStatus.COMPLETED);
    }

    @Test
    void backoffIsFiveMinutesTimesAttemptCountCappedAtThirty() {
        OffboardingJobEntity job = newJob(4, 10);
        OffboardingStepEntity failingStep =
                newStep(job.getId(), 0, "SESSION_REVOCATION", OffboardingStepStatus.PENDING);

        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(stepRepository.findAllByJobIdOrderBySequenceAsc(job.getId())).thenReturn(List.of(failingStep));
        doThrow(new RuntimeException("boom")).when(sessionRevocationGateway).revoke(any(), any());

        OffboardingStepExecutor executor = new OffboardingStepExecutor(
                jobRepository, stepRepository, sessionRevocationGateway, List.of(), eventPublisher);

        Instant before = Instant.now();
        executor.execute(job.getTenantId(), job.getId());

        assertThat(job.getAttemptCount()).isEqualTo(5);
        assertThat(job.getStatus()).isEqualTo(OffboardingJobStatus.FAILED);
        assertThat(job.getNextRetryAt()).isAfterOrEqualTo(before.plusSeconds(25 * 60));
        assertThat(failingStep.getStatus()).isEqualTo(OffboardingStepStatus.FAILED);
        assertThat(failingStep.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void jobBecomesPermanentlyFailedWhenAttemptCountReachesMaxAttempts() {
        OffboardingJobEntity job = newJob(2, 3);
        OffboardingStepEntity failingStep =
                newStep(job.getId(), 0, "SESSION_REVOCATION", OffboardingStepStatus.PENDING);

        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(stepRepository.findAllByJobIdOrderBySequenceAsc(job.getId())).thenReturn(List.of(failingStep));
        doThrow(new RuntimeException("boom")).when(sessionRevocationGateway).revoke(any(), any());

        OffboardingStepExecutor executor = new OffboardingStepExecutor(
                jobRepository, stepRepository, sessionRevocationGateway, List.of(), eventPublisher);

        executor.execute(job.getTenantId(), job.getId());

        assertThat(job.getAttemptCount()).isEqualTo(3);
        assertThat(job.getStatus()).isEqualTo(OffboardingJobStatus.PERMANENTLY_FAILED);
        assertThat(job.getNextRetryAt()).isNull();
    }

    @Test
    void executeNeverPropagatesAStepHandlerException() {
        OffboardingJobEntity job = newJob(0, 5);
        OffboardingStepEntity failingStep =
                newStep(job.getId(), 0, "SESSION_REVOCATION", OffboardingStepStatus.PENDING);

        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(stepRepository.findAllByJobIdOrderBySequenceAsc(job.getId())).thenReturn(List.of(failingStep));
        doThrow(new RuntimeException("boom")).when(sessionRevocationGateway).revoke(any(), any());

        OffboardingStepExecutor executor = new OffboardingStepExecutor(
                jobRepository, stepRepository, sessionRevocationGateway, List.of(), eventPublisher);

        assertThatCode(() -> executor.execute(job.getTenantId(), job.getId())).doesNotThrowAnyException();
    }

    @Test
    void terminalJobsAreANoOp() {
        OffboardingJobEntity job = newJob(5, 5);
        job.setStatus(OffboardingJobStatus.PERMANENTLY_FAILED);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        OffboardingStepExecutor executor = new OffboardingStepExecutor(
                jobRepository, stepRepository, sessionRevocationGateway, List.of(), eventPublisher);

        executor.execute(job.getTenantId(), job.getId());

        verifyNoInteractions(sessionRevocationGateway);
        verify(stepRepository, never()).findAllByJobIdOrderBySequenceAsc(any());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.OffboardingStepExecutorTest"`
Expected: FAIL to compile — `OffboardingStepExecutor` doesn't exist yet.

- [ ] **Step 3: Create `OffboardingStepExecutor`**

`gen-adm-starter/src/main/java/com/example/admsvc/application/impl/OffboardingStepExecutor.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.domain.enums.OffboardingJobStatus;
import com.example.admsvc.domain.enums.OffboardingStepStatus;
import com.example.admsvc.domain.port.OffboardingEventPublisher;
import com.example.admsvc.domain.port.OffboardingStepHandler;
import com.example.admsvc.domain.port.SessionRevocationGateway;
import com.example.admsvc.domain.port.TenantIdParam;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingStepEntity;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resumable, retry-only offboarding-step engine. {@code execute} skips any
 * step already COMPLETED and stops at the first failure — there is no
 * separate "resume" method; re-invoking {@code execute} on a FAILED job (below
 * {@code maxAttempts}) IS the resume path. Runs with no {@code GenAdmPrincipal}
 * reliably in scope (called from an {@code afterCommit} callback or a retry
 * endpoint), so tenant context comes from an explicit {@code @TenantIdParam}
 * argument, exactly like Phase 1's {@code bootstrapTenant}.
 */
@Service
public class OffboardingStepExecutor {

    public static final String SESSION_REVOCATION_STEP = "SESSION_REVOCATION";

    private final OffboardingJobRepository jobRepository;
    private final OffboardingStepRepository stepRepository;
    private final SessionRevocationGateway sessionRevocationGateway;
    private final Map<String, OffboardingStepHandler> handlersByName;
    private final OffboardingEventPublisher eventPublisher;

    public OffboardingStepExecutor(OffboardingJobRepository jobRepository,
                                    OffboardingStepRepository stepRepository,
                                    SessionRevocationGateway sessionRevocationGateway,
                                    List<OffboardingStepHandler> stepHandlers,
                                    OffboardingEventPublisher eventPublisher) {
        this.jobRepository = jobRepository;
        this.stepRepository = stepRepository;
        this.sessionRevocationGateway = sessionRevocationGateway;
        this.eventPublisher = eventPublisher;
        this.handlersByName = new HashMap<>();
        for (OffboardingStepHandler handler : stepHandlers) {
            handlersByName.put(handler.stepName(), handler);
        }
    }

    @Transactional
    public void execute(@TenantIdParam UUID tenantId, UUID jobId) {
        OffboardingJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Offboarding job not found: " + jobId));
        if (job.getStatus() == OffboardingJobStatus.COMPLETED
                || job.getStatus() == OffboardingJobStatus.PERMANENTLY_FAILED) {
            return;
        }

        List<OffboardingStepEntity> steps = stepRepository.findAllByJobIdOrderBySequenceAsc(jobId);
        for (OffboardingStepEntity step : steps) {
            if (step.getStatus() == OffboardingStepStatus.COMPLETED) {
                continue;
            }
            step.setAttemptNumber(step.getAttemptNumber() + 1);
            try {
                runStep(job, step);
            } catch (RuntimeException e) {
                failStep(job, step, e);
                return;
            }
            step.setStatus(OffboardingStepStatus.COMPLETED);
            stepRepository.save(step);
        }

        job.setStatus(OffboardingJobStatus.COMPLETED);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
        eventPublisher.onCompleted(job.getTenantId(), job.getUserId());
    }

    private void runStep(OffboardingJobEntity job, OffboardingStepEntity step) {
        if (step.getSequence() == 0) {
            sessionRevocationGateway.revoke(job.getTenantId(), job.getUserId());
            return;
        }
        OffboardingStepHandler handler = handlersByName.get(step.getStepName());
        if (handler == null) {
            throw new IllegalStateException("No OffboardingStepHandler registered for step: " + step.getStepName());
        }
        handler.handle(job.getTenantId(), job.getUserId(), job.getInitiatedBy());
    }

    private void failStep(OffboardingJobEntity job, OffboardingStepEntity step, RuntimeException e) {
        step.setStatus(OffboardingStepStatus.FAILED);
        step.setErrorMessage(e.getMessage());
        stepRepository.save(step);

        int attemptCount = job.getAttemptCount() + 1;
        job.setAttemptCount(attemptCount);
        if (attemptCount >= job.getMaxAttempts()) {
            job.setStatus(OffboardingJobStatus.PERMANENTLY_FAILED);
            job.setNextRetryAt(null);
        } else {
            job.setStatus(OffboardingJobStatus.FAILED);
            job.setNextRetryAt(Instant.now().plus(Duration.ofMinutes(Math.min(5L * attemptCount, 30L))));
        }
        jobRepository.save(job);
        eventPublisher.onFailed(job.getTenantId(), job.getUserId(), e.getMessage());
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.OffboardingStepExecutorTest"`
Expected: PASS (5 tests, 0 failures). No Docker required — pure Mockito.

- [ ] **Step 5: Write the failing "no default gateway bean" test**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/SessionRevocationGatewayRequiredTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.domain.port.OffboardingEventPublisher;
import com.example.admsvc.domain.port.OffboardingStepHandler;
import com.example.admsvc.domain.port.SessionRevocationGateway;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Proves Gen_ADM ships no default {@link SessionRevocationGateway} bean —
 * fixing source adm-svc's dead-code gap where a fully-coded
 * HttpAuthSessionRevocationGateway existed but nothing ever wired it in.
 * Uses a plain {@link AnnotationConfigApplicationContext} with mocked
 * repositories — no database, no Docker, genuinely runs in any environment.
 */
class SessionRevocationGatewayRequiredTest {

    @Configuration
    static class MissingGatewayConfig {

        @Bean
        OffboardingStepExecutor offboardingStepExecutor(OffboardingJobRepository jobRepository,
                                                          OffboardingStepRepository stepRepository,
                                                          SessionRevocationGateway gateway,
                                                          List<OffboardingStepHandler> handlers,
                                                          OffboardingEventPublisher publisher) {
            return new OffboardingStepExecutor(jobRepository, stepRepository, gateway, handlers, publisher);
        }

        @Bean
        OffboardingJobRepository jobRepository() {
            return mock(OffboardingJobRepository.class);
        }

        @Bean
        OffboardingStepRepository stepRepository() {
            return mock(OffboardingStepRepository.class);
        }

        @Bean
        OffboardingEventPublisher eventPublisher() {
            return mock(OffboardingEventPublisher.class);
        }
        // Deliberately no SessionRevocationGateway bean; an empty
        // List<OffboardingStepHandler> is valid and not under test here.
    }

    @Test
    void contextFailsToStartWithoutAConsumerSuppliedSessionRevocationGateway() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(MissingGatewayConfig.class);

        assertThatThrownBy(context::refresh)
                .isInstanceOf(UnsatisfiedDependencyException.class);
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.SessionRevocationGatewayRequiredTest"`
Expected: PASS (1 test, 0 failures). No Docker required.

- [ ] **Step 7: Run the full starter test suite**

Run: `./gradlew :gen-adm-starter:test`
Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add gen-adm-starter
git commit -m "feat: add resumable OffboardingStepExecutor with linear-backoff retry"
```

---

## Task 4: OffboardingService — initiate, status, retry

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/service/OffboardingService.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/OffboardingServiceImpl.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/OffboardingServiceImplTest.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/OffboardingIntegrationTest.java`

**Interfaces:**
- Consumes: `OffboardingJobRepository`, `OffboardingStepRepository` (Task 1); `OffboardingStepHandler` (Task 2); `OffboardingStepExecutor` (Task 3); `GenAdmNotFoundException` (Phase 1 Task 4).
- Produces: `OffboardingService.initiate(UUID tenantId, UUID userId, UUID initiatedBy, String reason): OffboardingJobEntity`, `.getJob(UUID tenantId, UUID jobId): OffboardingJobEntity`, `.retry(UUID tenantId, UUID jobId): OffboardingJobEntity` — Task 5's `OffboardingController` calls these exact signatures.

- [ ] **Step 1: Create the `OffboardingService` interface**

`gen-adm-starter/src/main/java/com/example/admsvc/application/service/OffboardingService.java`:
```java
package com.example.admsvc.application.service;

import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;

import java.util.UUID;

public interface OffboardingService {

    OffboardingJobEntity initiate(UUID tenantId, UUID userId, UUID initiatedBy, String reason);

    OffboardingJobEntity getJob(UUID tenantId, UUID jobId);

    OffboardingJobEntity retry(UUID tenantId, UUID jobId);
}
```

- [ ] **Step 2: Write the failing unit tests for `initiate` first**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/OffboardingServiceImplTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.domain.port.OffboardingStepHandler;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingStepEntity;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingStepRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Plain Mockito unit tests — no Spring context, no database. Covers what
 * OffboardingIntegrationTest (Testcontainers) cannot isolate cheaply:
 * step-zero ordering independent of handler registration order, and that
 * an afterCommit synchronization is actually registered.
 */
class OffboardingServiceImplTest {

    private final OffboardingJobRepository jobRepository = mock(OffboardingJobRepository.class);
    private final OffboardingStepRepository stepRepository = mock(OffboardingStepRepository.class);
    private final OffboardingStepExecutor stepExecutor = mock(OffboardingStepExecutor.class);

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private OffboardingStepHandler handler(String name) {
        OffboardingStepHandler handler = mock(OffboardingStepHandler.class);
        when(handler.stepName()).thenReturn(name);
        return handler;
    }

    @Test
    void sessionRevocationIsAlwaysSequenceZeroRegardlessOfHandlerRegistrationOrder() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID initiatedBy = UUID.randomUUID();
        OffboardingStepHandler handlerB = handler("TASK_B");
        OffboardingStepHandler handlerA = handler("TASK_A");

        when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> {
            OffboardingJobEntity job = inv.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        OffboardingServiceImpl service = new OffboardingServiceImpl(
                jobRepository, stepRepository, List.of(handlerB, handlerA), stepExecutor);

        service.initiate(tenantId, userId, initiatedBy, "left the company");

        ArgumentCaptor<OffboardingStepEntity> captor = ArgumentCaptor.forClass(OffboardingStepEntity.class);
        verify(stepRepository, times(3)).save(captor.capture());
        List<OffboardingStepEntity> saved = captor.getAllValues();

        assertThat(saved.get(0).getSequence()).isZero();
        assertThat(saved.get(0).getStepName()).isEqualTo(OffboardingStepExecutor.SESSION_REVOCATION_STEP);
        assertThat(saved.get(1).getSequence()).isEqualTo(1);
        assertThat(saved.get(1).getStepName()).isEqualTo("TASK_B");
        assertThat(saved.get(2).getSequence()).isEqualTo(2);
        assertThat(saved.get(2).getStepName()).isEqualTo("TASK_A");
    }

    @Test
    void initiateRegistersAnAfterCommitSynchronization() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID initiatedBy = UUID.randomUUID();

        when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> {
            OffboardingJobEntity job = inv.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        OffboardingServiceImpl service = new OffboardingServiceImpl(
                jobRepository, stepRepository, List.of(), stepExecutor);

        service.initiate(tenantId, userId, initiatedBy, "left the company");

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }
}
```

- [ ] **Step 3: Write the failing end-to-end integration test**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/OffboardingIntegrationTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.domain.enums.OffboardingJobStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.domain.port.OffboardingStepHandler;
import com.example.admsvc.domain.port.SessionRevocationGateway;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full flow: initiate -> afterCommit-triggered execution -> a forced first
 * failure on a pluggable step -> manual retry -> resumed completion. The
 * FlakyStepHandler fails once then succeeds, proving the executor resumes
 * from the failed step rather than re-running the already-COMPLETED
 * session-revocation step.
 */
@Testcontainers
@SpringBootTest(classes = OffboardingIntegrationTest.TestApp.class)
class OffboardingIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Component
    static class RecordingSessionRevocationGateway implements SessionRevocationGateway {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public void revoke(UUID tenantId, UUID userId) {
            calls.incrementAndGet();
        }
    }

    @Component
    static class FlakyStepHandler implements OffboardingStepHandler {
        final AtomicInteger attempts = new AtomicInteger();

        @Override
        public String stepName() {
            return "FLAKY_STEP";
        }

        @Override
        public void handle(UUID tenantId, UUID userId, UUID initiatedBy) {
            if (attempts.incrementAndGet() == 1) {
                throw new RuntimeException("simulated failure");
            }
        }
    }

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    @Autowired
    private OffboardingServiceImpl offboardingService;

    @Autowired
    private RecordingSessionRevocationGateway sessionRevocationGateway;

    @Autowired
    private FlakyStepHandler flakyStepHandler;

    private void authenticateAs(UUID tenantId, UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new TestPrincipal(tenantId, userId), null));
    }

    @Test
    void initiateRunsStepsInOrderAndCompletesAfterAResumedRetry() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID initiatedBy = UUID.randomUUID();
        authenticateAs(tenantId, initiatedBy);

        OffboardingJobEntity job = offboardingService.initiate(tenantId, userId, initiatedBy, "left the company");

        OffboardingJobEntity afterFirstRun = offboardingService.getJob(tenantId, job.getId());
        assertThat(afterFirstRun.getStatus()).isEqualTo(OffboardingJobStatus.FAILED);
        assertThat(afterFirstRun.getAttemptCount()).isEqualTo(1);
        assertThat(sessionRevocationGateway.calls.get()).isEqualTo(1);

        OffboardingJobEntity retried = offboardingService.retry(tenantId, job.getId());

        assertThat(retried.getStatus()).isEqualTo(OffboardingJobStatus.COMPLETED);
        assertThat(sessionRevocationGateway.calls.get()).isEqualTo(1);
        assertThat(flakyStepHandler.attempts.get()).isEqualTo(2);
    }

    @Test
    void retryOnACompletedJobIsANoOp() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID initiatedBy = UUID.randomUUID();
        authenticateAs(tenantId, initiatedBy);

        OffboardingJobEntity job = offboardingService.initiate(tenantId, userId, initiatedBy, "left the company");
        offboardingService.retry(tenantId, job.getId());
        OffboardingJobEntity completed = offboardingService.getJob(tenantId, job.getId());
        assertThat(completed.getStatus()).isEqualTo(OffboardingJobStatus.COMPLETED);

        int callsBeforeSecondRetry = sessionRevocationGateway.calls.get();
        OffboardingJobEntity secondRetry = offboardingService.retry(tenantId, job.getId());

        assertThat(secondRetry.getStatus()).isEqualTo(OffboardingJobStatus.COMPLETED);
        assertThat(sessionRevocationGateway.calls.get()).isEqualTo(callsBeforeSecondRetry);
    }
}
```

- [ ] **Step 4: Run both tests to verify they fail**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.OffboardingServiceImplTest" --tests "com.example.admsvc.application.impl.OffboardingIntegrationTest"`
Expected: FAIL to compile — `OffboardingServiceImpl` doesn't exist yet.

- [ ] **Step 5: Create `OffboardingServiceImpl`**

`gen-adm-starter/src/main/java/com/example/admsvc/application/impl/OffboardingServiceImpl.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.OffboardingService;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.OffboardingJobStatus;
import com.example.admsvc.domain.port.OffboardingStepHandler;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingStepEntity;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Service
public class OffboardingServiceImpl implements OffboardingService {

    private final OffboardingJobRepository jobRepository;
    private final OffboardingStepRepository stepRepository;
    private final List<OffboardingStepHandler> stepHandlers;
    private final OffboardingStepExecutor stepExecutor;

    public OffboardingServiceImpl(OffboardingJobRepository jobRepository,
                                   OffboardingStepRepository stepRepository,
                                   List<OffboardingStepHandler> stepHandlers,
                                   OffboardingStepExecutor stepExecutor) {
        this.jobRepository = jobRepository;
        this.stepRepository = stepRepository;
        this.stepHandlers = stepHandlers;
        this.stepExecutor = stepExecutor;
    }

    @Override
    @Transactional
    public OffboardingJobEntity initiate(UUID tenantId, UUID userId, UUID initiatedBy, String reason) {
        OffboardingJobEntity job = jobRepository.saveAndFlush(OffboardingJobEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .initiatedBy(initiatedBy)
                .reason(reason)
                .build());

        stepRepository.save(OffboardingStepEntity.builder()
                .jobId(job.getId())
                .tenantId(tenantId)
                .userId(userId)
                .sequence(0)
                .stepName(OffboardingStepExecutor.SESSION_REVOCATION_STEP)
                .build());

        int sequence = 1;
        for (OffboardingStepHandler handler : stepHandlers) {
            stepRepository.save(OffboardingStepEntity.builder()
                    .jobId(job.getId())
                    .tenantId(tenantId)
                    .userId(userId)
                    .sequence(sequence++)
                    .stepName(handler.stepName())
                    .build());
        }

        UUID jobId = job.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stepExecutor.execute(tenantId, jobId);
                }
            });
        }
        return job;
    }

    @Override
    @Transactional(readOnly = true)
    public OffboardingJobEntity getJob(UUID tenantId, UUID jobId) {
        return jobRepository.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new GenAdmNotFoundException("Offboarding job not found: " + jobId));
    }

    @Override
    @Transactional
    public OffboardingJobEntity retry(UUID tenantId, UUID jobId) {
        OffboardingJobEntity job = jobRepository.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new GenAdmNotFoundException("Offboarding job not found: " + jobId));
        if (job.getStatus() == OffboardingJobStatus.COMPLETED
                || job.getStatus() == OffboardingJobStatus.PERMANENTLY_FAILED) {
            return job;
        }
        stepExecutor.execute(tenantId, jobId);
        return jobRepository.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new GenAdmNotFoundException("Offboarding job not found: " + jobId));
    }
}
```

- [ ] **Step 6: Run both tests to verify they pass**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.OffboardingServiceImplTest"`
Expected: PASS (2 tests, 0 failures). No Docker required — pure Mockito.

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.OffboardingIntegrationTest"`
Expected: PASS (2 tests, 0 failures). Requires Docker running locally for Testcontainers.

- [ ] **Step 7: Run the full starter test suite**

Run: `./gradlew :gen-adm-starter:test`
Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add gen-adm-starter
git commit -m "feat: add OffboardingServiceImpl with afterCommit-triggered execution and manual retry"
```

---

## Task 5: REST layer — OffboardingController

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/InitiateOffboardingRequest.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/OffboardingJobResponse.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/controller/OffboardingController.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/api/controller/OffboardingControllerTest.java`

**Interfaces:**
- Consumes: `OffboardingService` (Task 4); `PermissionChecker`, `GenAdmPrincipal`, `GlobalExceptionHandler` (Phase 1 Tasks 4, 6) — unmodified.
- Produces: `POST /api/v1/offboarding`, `GET /api/v1/offboarding/{jobId}`, `POST /api/v1/offboarding/{jobId}/retry` — Task 6's smoke test exercises these routes.

- [ ] **Step 1: Create the DTOs**

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/InitiateOffboardingRequest.java`:
```java
package com.example.admsvc.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InitiateOffboardingRequest(@NotNull UUID userId, @NotBlank String reason) {
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/OffboardingJobResponse.java`:
```java
package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;

import java.time.Instant;
import java.util.UUID;

public record OffboardingJobResponse(UUID id, UUID userId, String status, int attemptCount,
                                      Instant nextRetryAt, Instant completedAt) {

    public static OffboardingJobResponse from(OffboardingJobEntity job) {
        return new OffboardingJobResponse(
                job.getId(),
                job.getUserId(),
                job.getStatus().name(),
                job.getAttemptCount(),
                job.getNextRetryAt(),
                job.getCompletedAt());
    }
}
```

- [ ] **Step 2: Write the failing controller test first**

`gen-adm-starter/src/test/java/com/example/admsvc/api/controller/OffboardingControllerTest.java`:
```java
package com.example.admsvc.api.controller;

import com.example.admsvc.api.advice.GlobalExceptionHandler;
import com.example.admsvc.application.service.OffboardingService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.OffboardingJobStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OffboardingControllerTest {

    private OffboardingService offboardingService;
    private PermissionChecker permissionChecker;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private final TestPrincipal principal = new TestPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        offboardingService = mock(OffboardingService.class);
        permissionChecker = mock(PermissionChecker.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OffboardingController(offboardingService, permissionChecker))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null));
    }

    @Test
    void initiatesAnOffboardingJobWhenPrincipalHasPermission() throws Exception {
        doNothing().when(permissionChecker).require(eq(principal), eq("adm:offboarding:manage"));
        UUID targetUserId = UUID.randomUUID();
        OffboardingJobEntity created = OffboardingJobEntity.builder()
                .id(UUID.randomUUID()).tenantId(principal.tenantId()).userId(targetUserId)
                .initiatedBy(principal.userId()).reason("left").status(OffboardingJobStatus.PENDING).build();
        when(offboardingService.initiate(eq(principal.tenantId()), eq(targetUserId), eq(principal.userId()), eq("left")))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/offboarding")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("userId", targetUserId.toString());
                            put("reason", "left");
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void returns403WhenPrincipalLacksPermission() throws Exception {
        doThrow(new GenAdmForbiddenException("Missing required permission: adm:offboarding:manage"))
                .when(permissionChecker).require(eq(principal), eq("adm:offboarding:manage"));

        mockMvc.perform(post("/api/v1/offboarding")
                        .contentType("application/json")
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"reason\":\"left\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void returns404ForACrossTenantJobLookup() throws Exception {
        UUID jobId = UUID.randomUUID();
        doNothing().when(permissionChecker).require(eq(principal), eq("adm:offboarding:manage"));
        when(offboardingService.getJob(eq(principal.tenantId()), eq(jobId)))
                .thenThrow(new GenAdmNotFoundException("Offboarding job not found: " + jobId));

        mockMvc.perform(get("/api/v1/offboarding/" + jobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.api.controller.OffboardingControllerTest"`
Expected: FAIL to compile — `OffboardingController` doesn't exist yet.

- [ ] **Step 4: Create `OffboardingController`**

`gen-adm-starter/src/main/java/com/example/admsvc/api/controller/OffboardingController.java`:
```java
package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.request.InitiateOffboardingRequest;
import com.example.admsvc.api.dto.response.OffboardingJobResponse;
import com.example.admsvc.application.service.OffboardingService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/offboarding")
public class OffboardingController {

    private static final String MANAGE_OFFBOARDING = "adm:offboarding:manage";

    private final OffboardingService offboardingService;
    private final PermissionChecker permissionChecker;

    public OffboardingController(OffboardingService offboardingService, PermissionChecker permissionChecker) {
        this.offboardingService = offboardingService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public OffboardingJobResponse initiate(@AuthenticationPrincipal GenAdmPrincipal principal,
                                            @Valid @RequestBody InitiateOffboardingRequest request) {
        permissionChecker.require(principal, MANAGE_OFFBOARDING);
        return OffboardingJobResponse.from(offboardingService.initiate(
                principal.tenantId(), request.userId(), principal.userId(), request.reason()));
    }

    @GetMapping("/{jobId}")
    public OffboardingJobResponse status(@AuthenticationPrincipal GenAdmPrincipal principal,
                                          @PathVariable UUID jobId) {
        permissionChecker.require(principal, MANAGE_OFFBOARDING);
        return OffboardingJobResponse.from(offboardingService.getJob(principal.tenantId(), jobId));
    }

    @PostMapping("/{jobId}/retry")
    public OffboardingJobResponse retry(@AuthenticationPrincipal GenAdmPrincipal principal,
                                         @PathVariable UUID jobId) {
        permissionChecker.require(principal, MANAGE_OFFBOARDING);
        return OffboardingJobResponse.from(offboardingService.retry(principal.tenantId(), jobId));
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.api.controller.OffboardingControllerTest"`
Expected: PASS (3 tests, 0 failures).

- [ ] **Step 6: Run the full starter test suite**

Run: `./gradlew :gen-adm-starter:test`
Expected: all tests pass. `GlobalExceptionHandler` needs no changes — its existing `@RestControllerAdvice(basePackages = "com.example.admsvc.api.controller")` already covers this new controller.

- [ ] **Step 7: Commit**

```bash
git add gen-adm-starter
git commit -m "feat: add offboarding REST controller (initiate/status/retry)"
```

---

## Task 6: Demo wiring, smoke test, docs

**Files:**
- Create: `gen-adm-demo/src/main/java/com/example/gendemo/DemoSessionRevocationGateway.java`
- Create: `gen-adm-demo/src/main/java/com/example/gendemo/DemoNotifyStepHandler.java`
- Modify: `scripts/smoke-test.sh` (append offboarding flow)
- Modify: `README.md` (add an Offboarding section)
- Modify: `docs/integration-guide.md` (add an Offboarding section)

**Interfaces:**
- Consumes: `SessionRevocationGateway`, `OffboardingStepHandler` (Task 2); `OffboardingController`'s routes (Task 5).
- Produces: nothing new consumed by later tasks — this is the terminal task before the final whole-branch review.

- [ ] **Step 1: Create the demo `SessionRevocationGateway`**

`gen-adm-demo/src/main/java/com/example/gendemo/DemoSessionRevocationGateway.java`:
```java
package com.example.gendemo;

import com.example.admsvc.domain.port.SessionRevocationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * FOR LOCAL DEMO ONLY — a real host application calls its own auth service's
 * session-revocation endpoint here (e.g. Gen_AUTH's
 * {@code /internal/auth/users/{userId}/revoke-sessions}). Gen_ADM requires a
 * bean for this port; startup fails without one.
 */
@Component
public class DemoSessionRevocationGateway implements SessionRevocationGateway {

    private static final Logger log = LoggerFactory.getLogger(DemoSessionRevocationGateway.class);

    @Override
    public void revoke(UUID tenantId, UUID userId) {
        log.info("Revoking sessions for user {} in tenant {}", userId, tenantId);
    }
}
```

- [ ] **Step 2: Create a sample pluggable step handler**

`gen-adm-demo/src/main/java/com/example/gendemo/DemoNotifyStepHandler.java`:
```java
package com.example.gendemo;

import com.example.admsvc.domain.port.OffboardingStepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sample optional step showing how a consumer plugs in additional
 * offboarding behavior beyond the mandatory session-revocation step.
 */
@Component
public class DemoNotifyStepHandler implements OffboardingStepHandler {

    private static final Logger log = LoggerFactory.getLogger(DemoNotifyStepHandler.class);

    @Override
    public String stepName() {
        return "NOTIFY_MANAGER";
    }

    @Override
    public void handle(UUID tenantId, UUID userId, UUID initiatedBy) {
        log.info("Notifying manager that user {} was offboarded (initiated by {})", userId, initiatedBy);
    }
}
```

- [ ] **Step 3: Run the full build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` — `gen-adm-demo`'s context loads with both new beans present, satisfying `SessionRevocationGateway`'s mandatory requirement.

- [ ] **Step 4: Append the offboarding flow to the smoke test**

Edit `scripts/smoke-test.sh` — append before the final `echo "Smoke test complete."` line:
```bash
echo "== Initiate offboarding for the second user =="
JOB_ID=$(curl -sf -X POST "$BASE_URL/api/v1/offboarding" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d "{\"userId\":\"$OTHER_USER_ID\",\"reason\":\"left the company\"}" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
echo "Created offboarding job: $JOB_ID"

echo "== Check offboarding job status =="
curl -sf "$BASE_URL/api/v1/offboarding/$JOB_ID" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID"
echo
```

- [ ] **Step 5: Update `README.md`**

Add a new section after the existing RBAC section (find it and match its heading level):
```markdown
## Offboarding Saga

Tenant-scoped, retry-only user offboarding. Session revocation runs as a
mandatory built-in step; add more steps by registering
`OffboardingStepHandler` beans. See `docs/integration-guide.md` for the full
walkthrough.

- `POST /api/v1/offboarding` — initiate (`{ "userId": "...", "reason": "..." }`)
- `GET /api/v1/offboarding/{jobId}` — check status
- `POST /api/v1/offboarding/{jobId}/retry` — manually retry a FAILED job

A consumer **must** provide a `SessionRevocationGateway` bean — there is no
default, and startup fails without one.
```

- [ ] **Step 6: Update `docs/integration-guide.md`**

Add a new section (match the existing document's heading level and code-sample style):
```markdown
## Offboarding Saga

Register a `SessionRevocationGateway` bean — this is mandatory, startup fails
without one:

\```java
@Component
public class MySessionRevocationGateway implements SessionRevocationGateway {
    @Override
    public void revoke(UUID tenantId, UUID userId) {
        // call your own auth service's session-revocation endpoint
    }
}
\```

Optionally register zero or more `OffboardingStepHandler` beans for anything
else that should happen on offboarding (task reassignment, ticket
reassignment, etc. — Gen_ADM ships none built in):

\```java
@Component
public class ReassignOpenTicketsStepHandler implements OffboardingStepHandler {
    @Override
    public String stepName() {
        return "REASSIGN_TICKETS";
    }

    @Override
    public void handle(UUID tenantId, UUID userId, UUID initiatedBy) {
        // your reassignment logic
    }
}
\```

Call `OffboardingService.initiate(tenantId, userId, initiatedBy, reason)` (or
`POST /api/v1/offboarding`) to start a job. Session revocation always runs
first; registered handlers run afterward in the order Spring injects them.
On any step's failure the job is marked `FAILED` with a `nextRetryAt`
(`min(5 × attemptCount, 30)` minutes out) — retry manually via
`POST /api/v1/offboarding/{jobId}/retry`. There is no compensation/rollback:
this is a retry-only saga, matching the source system it was ported from.
Gen_ADM does not ship a scheduled retry sweeper — polling
`GET /api/v1/offboarding/{jobId}` or building your own scheduler on top of
that endpoint is the consumer's responsibility if automatic retry is needed.
```

- [ ] **Step 7: Commit**

```bash
git add gen-adm-demo scripts/smoke-test.sh README.md docs/integration-guide.md
git commit -m "docs: wire offboarding into gen-adm-demo, smoke test, README, and integration guide"
```
