# Gen_ADM Impersonation Requests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a self-owned, intra-tenant impersonation-consent workflow to Gen_ADM: a privileged user requests to act as another user in the same tenant, a second privileged user (never the requester) grants or denies consent, and the resulting active session can be ended by the impersonator themselves or by an admin.

**Architecture:** Additive to the existing `gen-adm-starter`/`gen-adm-demo` split — no changes to Phase 1 (RBAC) or Phase 2's (Offboarding) public surface. One new table, `impersonation_sessions`, tenant-scoped with RLS, drives a five-state machine (`PENDING_CONSENT`, `ACTIVE`, `DENIED`, `ENDED`, `EXPIRED`) entirely inside `ImpersonationSessionServiceImpl`. Expiry is lazy and read-time: every mutating/listing method flips overdue `PENDING_CONSENT`/`ACTIVE` rows to `EXPIRED` before acting, so there is no scheduler anywhere in this plan. This is a deliberate departure from the CPMS source (`docs/source-audit-notes.md`, `pre-context/adm-svc/.../ImpersonationRequestServiceImpl.java`), where ADM-SVC was a stateless proxy to a separate SUP-SVC — Gen_ADM has no SUP-SVC equivalent and no cross-tenant superadmin identity, so it owns the session lifecycle itself, the same move already made for the Offboarding Saga.

**Tech Stack:** Same as Phase 1/2 — Java 21, Spring Boot 4.0.6, Gradle 9.4.1, PostgreSQL, Flyway, Spring Data JPA, Spring AOP, Lombok, JUnit 5, Mockito, Testcontainers. No new dependencies.

## Global Constraints

- Package root stays `com.example.admsvc` — new code lives alongside Phase 1/2's, not in a separate module.
- Impersonation is **intra-tenant only** — `requestedByUserId` and `targetUserId` are both implicitly the same tenant's users (Gen_ADM has no cross-tenant principal concept; see `docs/superpowers/specs/2026-07-24-gen-adm-impersonation-design.md`).
- Self-impersonation guard: `create()` throws `GenAdmValidationException` (400) if `requestedByUserId.equals(targetUserId)`.
- Self-approval guard: `approve()`/`reject()` throw `GenAdmForbiddenException` (403) if the reviewer is the requester.
- Ending a session: the impersonator (`actorId.equals(requestedByUserId)`) may always end their own `ACTIVE` session with no permission check; anyone else must be `privileged` (holds `adm:impersonation:manage`, checked by the controller) or the service throws `GenAdmForbiddenException`.
- A single `expiresAt`, set once at creation as `createdAt + ttlMinutes`, covers both "must be approved by" (while `PENDING_CONSENT`) and "session must end by" (while `ACTIVE`) — never recalculated, never a second deadline field.
- Lazy, read-time expiry only — **no scheduler anywhere in this plan**. `listActionable`, `approve`, `reject`, and `end` all call a shared `expireIfOverdue` helper first; acting on an already-overdue row throws `GenAdmConflictException` (409), it never silently succeeds against a stale state.
- `ImpersonationSessionServiceImpl.create(...)` deliberately has **no bounds check on `ttlMinutes`** — `1 <= ttlMinutes <= 480` is enforced only by `@Min`/`@Max` on `CreateImpersonationRequest` at the REST boundary. This lets Task 4's integration test construct an already-expired session (`ttlMinutes` ≤ 0) directly through the service without sleeping in a test.
- Cross-tenant session lookups surface as 404 (`GenAdmNotFoundException`), never 403 — same anti-enumeration convention as Phase 1/2.
- No separate audit-log table — `reviewedByUserId`/`reviewedAt`/`endedByUserId`/`endedAt` on the session row itself are the audit trail.
- No JWT/session-token minting for "acting as" the target user — Gen_ADM only tracks whether impersonation is currently authorized and active; actually executing it is the consuming application's job, out of scope for this plan.
- Reuse Phase 1/2's `TenantContextAspect`, `GenAdmPrincipal`, `PermissionChecker`, `GlobalExceptionHandler` completely unmodified — every new `@Transactional` method goes in `com.example.admsvc.application.impl` exactly like existing services, so the existing aspect pointcut already covers it. No new exception subclasses, no aspect changes.
- One final whole-branch review at the end of all tasks, not one per task — same convention as Phase 1/2.
- Known sandbox limitation carried forward from Phase 1/2: Testcontainers-based tests (Tasks 1 and 4) can only be verified by code review in this sandbox (confirmed Docker-detection limitation, not a code defect). Task 2's unit test and Task 3's controller test use no database/Spring context and are expected to actually run and pass here.

---

## Task 1: Impersonation persistence — enum, entity, repository, migrations

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/ImpersonationSessionStatus.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/ImpersonationSessionEntity.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/ImpersonationSessionRepository.java`
- Create: `gen-adm-starter/src/main/resources/db/migration/genadm/V5__create_impersonation_sessions_table.sql`
- Create: `gen-adm-starter/src/main/resources/db/migration/genadm/V6__enable_impersonation_sessions_rls.sql`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/ImpersonationPersistenceIntegrationTest.java`

**Interfaces:**
- Consumes: the existing `classpath:db/migration/genadm` Flyway location (`FlywayConfig`) and the RLS pattern from `V2__enable_rls.sql`/`V4__enable_offboarding_rls.sql`. `V5`/`V6` are the next free migration versions — `V1`–`V4` already exist.
- Produces: `ImpersonationSessionEntity` (`UUID id`, `UUID tenantId`, `UUID requestedByUserId`, `UUID targetUserId`, `String reason`, `ImpersonationSessionStatus status`, `UUID reviewedByUserId`, `Instant reviewedAt`, `UUID endedByUserId`, `Instant endedAt`, `Instant expiresAt`, `Long version`, `Instant createdAt`, `Instant updatedAt`) — later tasks depend on these exact field names. `ImpersonationSessionRepository.findByIdAndTenantId(UUID, UUID): Optional<ImpersonationSessionEntity>` and `findAllByTenantIdAndStatusIn(UUID, List<ImpersonationSessionStatus>): List<ImpersonationSessionEntity>` — Task 2's service calls these exact signatures.

- [ ] **Step 1: Create the status enum**

`gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/ImpersonationSessionStatus.java`:
```java
package com.example.admsvc.domain.enums;

public enum ImpersonationSessionStatus {
    PENDING_CONSENT,
    ACTIVE,
    DENIED,
    ENDED,
    EXPIRED
}
```

- [ ] **Step 2: Write the failing persistence integration test first**

`gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/ImpersonationPersistenceIntegrationTest.java`:
```java
package com.example.admsvc.infrastructure.persistence;

import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;
import com.example.admsvc.infrastructure.persistence.repository.ImpersonationSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = ImpersonationPersistenceIntegrationTest.TestApp.class)
class ImpersonationPersistenceIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ImpersonationSessionRepository repository;

    private ImpersonationSessionEntity newSession(UUID tenantId) {
        return ImpersonationSessionEntity.builder()
                .tenantId(tenantId)
                .requestedByUserId(UUID.randomUUID())
                .targetUserId(UUID.randomUUID())
                .reason("support ticket #42")
                .expiresAt(Instant.now().plus(60, ChronoUnit.MINUTES))
                .build();
    }

    @Test
    void savesAndFindsASessionByIdAndTenantDefaultingToPendingConsent() {
        UUID tenantId = UUID.randomUUID();
        ImpersonationSessionEntity session = repository.saveAndFlush(newSession(tenantId));

        Optional<ImpersonationSessionEntity> found = repository.findByIdAndTenantId(session.getId(), tenantId);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(ImpersonationSessionStatus.PENDING_CONSENT);
        assertThat(found.get().getReviewedByUserId()).isNull();
        assertThat(found.get().getEndedByUserId()).isNull();
    }

    @Test
    void aSessionIsInvisibleWhenLookedUpUnderTheWrongTenant() {
        UUID tenantId = UUID.randomUUID();
        ImpersonationSessionEntity session = repository.saveAndFlush(newSession(tenantId));

        Optional<ImpersonationSessionEntity> found = repository.findByIdAndTenantId(session.getId(), UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    void findsAllActionableSessionsForATenantFilteredByStatus() {
        UUID tenantId = UUID.randomUUID();
        ImpersonationSessionEntity pending = repository.saveAndFlush(newSession(tenantId));
        ImpersonationSessionEntity denied = newSession(tenantId);
        denied.setStatus(ImpersonationSessionStatus.DENIED);
        repository.saveAndFlush(denied);
        repository.saveAndFlush(newSession(UUID.randomUUID())); // different tenant, must not appear

        List<ImpersonationSessionEntity> actionable = repository.findAllByTenantIdAndStatusIn(
                tenantId, List.of(ImpersonationSessionStatus.PENDING_CONSENT, ImpersonationSessionStatus.ACTIVE));

        assertThat(actionable).extracting(ImpersonationSessionEntity::getId).containsExactly(pending.getId());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.persistence.ImpersonationPersistenceIntegrationTest"`
Expected: FAIL — compile error, `ImpersonationSessionEntity`/`ImpersonationSessionRepository` do not exist yet.

- [ ] **Step 4: Create the entity**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/ImpersonationSessionEntity.java`:
```java
package com.example.admsvc.infrastructure.persistence.entity;

import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "impersonation_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpersonationSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ImpersonationSessionStatus status;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "ended_by_user_id")
    private UUID endedByUserId;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

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
            status = ImpersonationSessionStatus.PENDING_CONSENT;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
```

- [ ] **Step 5: Create the repository**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/ImpersonationSessionRepository.java`:
```java
package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImpersonationSessionRepository extends JpaRepository<ImpersonationSessionEntity, UUID> {

    Optional<ImpersonationSessionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ImpersonationSessionEntity> findAllByTenantIdAndStatusIn(UUID tenantId, List<ImpersonationSessionStatus> statuses);
}
```

- [ ] **Step 6: Create the migrations**

`gen-adm-starter/src/main/resources/db/migration/genadm/V5__create_impersonation_sessions_table.sql`:
```sql
CREATE TABLE impersonation_sessions (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    requested_by_user_id  UUID NOT NULL,
    target_user_id        UUID NOT NULL,
    reason                VARCHAR(500) NOT NULL,
    status                VARCHAR(30) NOT NULL,
    reviewed_by_user_id   UUID,
    reviewed_at           TIMESTAMPTZ,
    ended_by_user_id      UUID,
    ended_at              TIMESTAMPTZ,
    expires_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_impersonation_sessions_tenant_status
    ON impersonation_sessions(tenant_id, status);
```

`gen-adm-starter/src/main/resources/db/migration/genadm/V6__enable_impersonation_sessions_rls.sql`:
```sql
ALTER TABLE impersonation_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE impersonation_sessions FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_impersonation_sessions ON impersonation_sessions
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.persistence.ImpersonationPersistenceIntegrationTest"`
Expected: PASS (3 tests) — subject to the known Testcontainers/Docker sandbox limitation noted in Global Constraints; verify by code review if Docker isn't available here.

- [ ] **Step 8: Commit**

```bash
git add gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/ImpersonationSessionStatus.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/ImpersonationSessionEntity.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/ImpersonationSessionRepository.java \
        gen-adm-starter/src/main/resources/db/migration/genadm/V5__create_impersonation_sessions_table.sql \
        gen-adm-starter/src/main/resources/db/migration/genadm/V6__enable_impersonation_sessions_rls.sql \
        gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/ImpersonationPersistenceIntegrationTest.java
git commit -m "feat: add impersonation session persistence (entity, repository, RLS migrations)"
```

---

## Task 2: Domain port + ImpersonationSessionService

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/port/ImpersonationEventPublisher.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/event/NoOpImpersonationEventPublisher.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/config/ImpersonationConfig.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/service/ImpersonationSessionService.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/ImpersonationSessionServiceImpl.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/ImpersonationSessionServiceImplTest.java`

**Interfaces:**
- Consumes: `ImpersonationSessionRepository` and `ImpersonationSessionEntity`/`ImpersonationSessionStatus` from Task 1 (exact signatures above); `GenAdmValidationException`, `GenAdmForbiddenException`, `GenAdmConflictException`, `GenAdmNotFoundException` from `com.example.admsvc.common.exception` (all pre-existing).
- Produces: `ImpersonationSessionService` with
  ```java
  ImpersonationSessionEntity create(UUID tenantId, UUID requestedByUserId, UUID targetUserId, String reason, int ttlMinutes);
  List<ImpersonationSessionEntity> listActionable(UUID tenantId);
  ImpersonationSessionEntity approve(UUID tenantId, UUID sessionId, UUID reviewerId);
  ImpersonationSessionEntity reject(UUID tenantId, UUID sessionId, UUID reviewerId);
  ImpersonationSessionEntity end(UUID tenantId, UUID sessionId, UUID actorId, boolean privileged);
  ```
  Task 3's controller calls these exact signatures. `ImpersonationEventPublisher` (`onGranted`, `onDenied`, `onEnded`, each `(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId)`) is optional for a consumer to override; `NoOpImpersonationEventPublisher` is the default bean.

- [ ] **Step 1: Create the event publisher port and no-op default**

`gen-adm-starter/src/main/java/com/example/admsvc/domain/port/ImpersonationEventPublisher.java`:
```java
package com.example.admsvc.domain.port;

import java.util.UUID;

/**
 * Optional synchronous hook for a consumer to react to an impersonation
 * session's consent outcome (e.g. fire its own security notification)
 * without Gen_ADM owning a broker dependency. Not an outbox — no
 * persistence, no retry of the notification itself. Defaults to a no-op
 * ({@link com.example.admsvc.infrastructure.event.NoOpImpersonationEventPublisher}).
 */
public interface ImpersonationEventPublisher {

    void onGranted(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId);

    void onDenied(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId);

    void onEnded(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId);
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/event/NoOpImpersonationEventPublisher.java`:
```java
package com.example.admsvc.infrastructure.event;

import com.example.admsvc.domain.port.ImpersonationEventPublisher;

import java.util.UUID;

public class NoOpImpersonationEventPublisher implements ImpersonationEventPublisher {

    @Override
    public void onGranted(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
    }

    @Override
    public void onDenied(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
    }

    @Override
    public void onEnded(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
    }
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/config/ImpersonationConfig.java`:
```java
package com.example.admsvc.config;

import com.example.admsvc.domain.port.ImpersonationEventPublisher;
import com.example.admsvc.infrastructure.event.NoOpImpersonationEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ImpersonationConfig {

    @Bean
    @ConditionalOnMissingBean(ImpersonationEventPublisher.class)
    public ImpersonationEventPublisher impersonationEventPublisher() {
        return new NoOpImpersonationEventPublisher();
    }
}
```

- [ ] **Step 2: Create the service interface**

`gen-adm-starter/src/main/java/com/example/admsvc/application/service/ImpersonationSessionService.java`:
```java
package com.example.admsvc.application.service;

import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;

import java.util.List;
import java.util.UUID;

public interface ImpersonationSessionService {

    ImpersonationSessionEntity create(UUID tenantId, UUID requestedByUserId, UUID targetUserId,
                                       String reason, int ttlMinutes);

    List<ImpersonationSessionEntity> listActionable(UUID tenantId);

    ImpersonationSessionEntity approve(UUID tenantId, UUID sessionId, UUID reviewerId);

    ImpersonationSessionEntity reject(UUID tenantId, UUID sessionId, UUID reviewerId);

    ImpersonationSessionEntity end(UUID tenantId, UUID sessionId, UUID actorId, boolean privileged);
}
```

- [ ] **Step 3: Write the failing unit test first**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/ImpersonationSessionServiceImplTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.common.exception.GenAdmValidationException;
import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import com.example.admsvc.domain.port.ImpersonationEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;
import com.example.admsvc.infrastructure.persistence.repository.ImpersonationSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImpersonationSessionServiceImplTest {

    private final ImpersonationSessionRepository repository = mock(ImpersonationSessionRepository.class);
    private final ImpersonationEventPublisher eventPublisher = mock(ImpersonationEventPublisher.class);
    private ImpersonationSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ImpersonationSessionServiceImpl(repository, eventPublisher);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            ImpersonationSessionEntity session = inv.getArgument(0);
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            if (session.getStatus() == null) {
                session.setStatus(ImpersonationSessionStatus.PENDING_CONSENT);
            }
            return session;
        });
    }

    private ImpersonationSessionEntity pendingSession(UUID tenantId, UUID requestedBy, UUID target, Instant expiresAt) {
        return ImpersonationSessionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .requestedByUserId(requestedBy)
                .targetUserId(target)
                .reason("support ticket #42")
                .status(ImpersonationSessionStatus.PENDING_CONSENT)
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    void createRejectsRequestingToImpersonateYourself() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(tenantId, userId, userId, "why not", 30))
                .isInstanceOf(GenAdmValidationException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createPersistsAPendingConsentSessionWithComputedExpiry() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        ImpersonationSessionEntity session = service.create(tenantId, requestedBy, target, "support ticket #42", 30);

        assertThat(session.getTenantId()).isEqualTo(tenantId);
        assertThat(session.getRequestedByUserId()).isEqualTo(requestedBy);
        assertThat(session.getTargetUserId()).isEqualTo(target);
        assertThat(session.getStatus()).isEqualTo(ImpersonationSessionStatus.PENDING_CONSENT);
        assertThat(session.getExpiresAt()).isAfter(Instant.now().plus(29, ChronoUnit.MINUTES));
    }

    @Test
    void approveFlipsToActiveAndNotifiesGranted() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, requestedBy, UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        ImpersonationSessionEntity approved = service.approve(tenantId, session.getId(), reviewer);

        assertThat(approved.getStatus()).isEqualTo(ImpersonationSessionStatus.ACTIVE);
        assertThat(approved.getReviewedByUserId()).isEqualTo(reviewer);
        assertThat(approved.getReviewedAt()).isNotNull();
        verify(eventPublisher).onGranted(tenantId, session.getId(), requestedBy, session.getTargetUserId());
    }

    @Test
    void approveRejectsTheRequesterApprovingTheirOwnRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, requestedBy, UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.approve(tenantId, session.getId(), requestedBy))
                .isInstanceOf(GenAdmForbiddenException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void approveThrowsConflictWhenSessionIsNotPendingConsent() {
        UUID tenantId = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        session.setStatus(ImpersonationSessionStatus.DENIED);
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.approve(tenantId, session.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void approveExpiresAnOverdueSessionInsteadOfApprovingIt() {
        UUID tenantId = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().minus(1, ChronoUnit.MINUTES));
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.approve(tenantId, session.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
        assertThat(session.getStatus()).isEqualTo(ImpersonationSessionStatus.EXPIRED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void approveThrowsNotFoundForACrossTenantLookup() {
        UUID sessionId = UUID.randomUUID();
        when(repository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(UUID.randomUUID(), sessionId, UUID.randomUUID()))
                .isInstanceOf(GenAdmNotFoundException.class);
    }

    @Test
    void rejectFlipsToDeniedAndNotifiesDenied() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, requestedBy, UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        ImpersonationSessionEntity rejected = service.reject(tenantId, session.getId(), reviewer);

        assertThat(rejected.getStatus()).isEqualTo(ImpersonationSessionStatus.DENIED);
        verify(eventPublisher).onDenied(tenantId, session.getId(), requestedBy, session.getTargetUserId());
    }

    @Test
    void endAllowsTheImpersonatorToEndTheirOwnSessionWithoutPrivilege() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, requestedBy, UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        session.setStatus(ImpersonationSessionStatus.ACTIVE);
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        ImpersonationSessionEntity ended = service.end(tenantId, session.getId(), requestedBy, false);

        assertThat(ended.getStatus()).isEqualTo(ImpersonationSessionStatus.ENDED);
        assertThat(ended.getEndedByUserId()).isEqualTo(requestedBy);
        verify(eventPublisher).onEnded(tenantId, session.getId(), requestedBy, session.getTargetUserId());
    }

    @Test
    void endRejectsANonRequesterWithoutPrivilege() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, requestedBy, UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        session.setStatus(ImpersonationSessionStatus.ACTIVE);
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.end(tenantId, session.getId(), UUID.randomUUID(), false))
                .isInstanceOf(GenAdmForbiddenException.class);
    }

    @Test
    void endAllowsAPrivilegedActorEvenIfNotTheRequester() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, requestedBy, UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        session.setStatus(ImpersonationSessionStatus.ACTIVE);
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        ImpersonationSessionEntity ended = service.end(tenantId, session.getId(), admin, true);

        assertThat(ended.getStatus()).isEqualTo(ImpersonationSessionStatus.ENDED);
        assertThat(ended.getEndedByUserId()).isEqualTo(admin);
    }

    @Test
    void endThrowsConflictWhenSessionIsNotActive() {
        UUID tenantId = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.end(tenantId, session.getId(), UUID.randomUUID(), true))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void listActionableFiltersOutSessionsExpiredDuringTheCall() {
        UUID tenantId = UUID.randomUUID();
        ImpersonationSessionEntity fresh = pendingSession(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        ImpersonationSessionEntity overdue = pendingSession(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().minus(1, ChronoUnit.MINUTES));
        when(repository.findAllByTenantIdAndStatusIn(tenantId,
                List.of(ImpersonationSessionStatus.PENDING_CONSENT, ImpersonationSessionStatus.ACTIVE)))
                .thenReturn(List.of(fresh, overdue));

        List<ImpersonationSessionEntity> actionable = service.listActionable(tenantId);

        assertThat(actionable).extracting(ImpersonationSessionEntity::getId).containsExactly(fresh.getId());
        assertThat(overdue.getStatus()).isEqualTo(ImpersonationSessionStatus.EXPIRED);
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.ImpersonationSessionServiceImplTest"`
Expected: FAIL — compile error, `ImpersonationSessionServiceImpl` does not exist yet.

- [ ] **Step 5: Write the implementation**

`gen-adm-starter/src/main/java/com/example/admsvc/application/impl/ImpersonationSessionServiceImpl.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.ImpersonationSessionService;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.common.exception.GenAdmValidationException;
import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import com.example.admsvc.domain.port.ImpersonationEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;
import com.example.admsvc.infrastructure.persistence.repository.ImpersonationSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ImpersonationSessionServiceImpl implements ImpersonationSessionService {

    private final ImpersonationSessionRepository repository;
    private final ImpersonationEventPublisher eventPublisher;

    public ImpersonationSessionServiceImpl(ImpersonationSessionRepository repository,
                                            ImpersonationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ImpersonationSessionEntity create(UUID tenantId, UUID requestedByUserId, UUID targetUserId,
                                              String reason, int ttlMinutes) {
        if (requestedByUserId.equals(targetUserId)) {
            throw new GenAdmValidationException("Cannot request to impersonate yourself");
        }
        return repository.saveAndFlush(ImpersonationSessionEntity.builder()
                .tenantId(tenantId)
                .requestedByUserId(requestedByUserId)
                .targetUserId(targetUserId)
                .reason(reason)
                .expiresAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES))
                .build());
    }

    @Override
    @Transactional
    public List<ImpersonationSessionEntity> listActionable(UUID tenantId) {
        return repository.findAllByTenantIdAndStatusIn(tenantId,
                        List.of(ImpersonationSessionStatus.PENDING_CONSENT, ImpersonationSessionStatus.ACTIVE))
                .stream()
                .map(this::expireIfOverdue)
                .filter(session -> session.getStatus() != ImpersonationSessionStatus.EXPIRED)
                .toList();
    }

    @Override
    @Transactional
    public ImpersonationSessionEntity approve(UUID tenantId, UUID sessionId, UUID reviewerId) {
        ImpersonationSessionEntity session = expireIfOverdue(findOrThrow(tenantId, sessionId));
        if (session.getStatus() != ImpersonationSessionStatus.PENDING_CONSENT) {
            throw new GenAdmConflictException("Impersonation session is not awaiting consent: " + sessionId);
        }
        if (reviewerId.equals(session.getRequestedByUserId())) {
            throw new GenAdmForbiddenException("Cannot approve your own impersonation request");
        }
        session.setStatus(ImpersonationSessionStatus.ACTIVE);
        session.setReviewedByUserId(reviewerId);
        session.setReviewedAt(Instant.now());
        ImpersonationSessionEntity saved = repository.saveAndFlush(session);
        eventPublisher.onGranted(tenantId, saved.getId(), saved.getRequestedByUserId(), saved.getTargetUserId());
        return saved;
    }

    @Override
    @Transactional
    public ImpersonationSessionEntity reject(UUID tenantId, UUID sessionId, UUID reviewerId) {
        ImpersonationSessionEntity session = expireIfOverdue(findOrThrow(tenantId, sessionId));
        if (session.getStatus() != ImpersonationSessionStatus.PENDING_CONSENT) {
            throw new GenAdmConflictException("Impersonation session is not awaiting consent: " + sessionId);
        }
        if (reviewerId.equals(session.getRequestedByUserId())) {
            throw new GenAdmForbiddenException("Cannot reject your own impersonation request");
        }
        session.setStatus(ImpersonationSessionStatus.DENIED);
        session.setReviewedByUserId(reviewerId);
        session.setReviewedAt(Instant.now());
        ImpersonationSessionEntity saved = repository.saveAndFlush(session);
        eventPublisher.onDenied(tenantId, saved.getId(), saved.getRequestedByUserId(), saved.getTargetUserId());
        return saved;
    }

    @Override
    @Transactional
    public ImpersonationSessionEntity end(UUID tenantId, UUID sessionId, UUID actorId, boolean privileged) {
        ImpersonationSessionEntity session = expireIfOverdue(findOrThrow(tenantId, sessionId));
        if (session.getStatus() != ImpersonationSessionStatus.ACTIVE) {
            throw new GenAdmConflictException("Impersonation session is not active: " + sessionId);
        }
        if (!privileged && !actorId.equals(session.getRequestedByUserId())) {
            throw new GenAdmForbiddenException("Only the impersonator or a privileged reviewer can end this session");
        }
        session.setStatus(ImpersonationSessionStatus.ENDED);
        session.setEndedByUserId(actorId);
        session.setEndedAt(Instant.now());
        ImpersonationSessionEntity saved = repository.saveAndFlush(session);
        eventPublisher.onEnded(tenantId, saved.getId(), saved.getRequestedByUserId(), saved.getTargetUserId());
        return saved;
    }

    private ImpersonationSessionEntity findOrThrow(UUID tenantId, UUID sessionId) {
        return repository.findByIdAndTenantId(sessionId, tenantId)
                .orElseThrow(() -> new GenAdmNotFoundException("Impersonation session not found: " + sessionId));
    }

    private ImpersonationSessionEntity expireIfOverdue(ImpersonationSessionEntity session) {
        boolean pendingOrActive = session.getStatus() == ImpersonationSessionStatus.PENDING_CONSENT
                || session.getStatus() == ImpersonationSessionStatus.ACTIVE;
        if (pendingOrActive && Instant.now().isAfter(session.getExpiresAt())) {
            session.setStatus(ImpersonationSessionStatus.EXPIRED);
            return repository.saveAndFlush(session);
        }
        return session;
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.ImpersonationSessionServiceImplTest"`
Expected: PASS (13 tests) — plain Mockito, no Spring context, no database; expected to actually run here.

- [ ] **Step 7: Commit**

```bash
git add gen-adm-starter/src/main/java/com/example/admsvc/domain/port/ImpersonationEventPublisher.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/event/NoOpImpersonationEventPublisher.java \
        gen-adm-starter/src/main/java/com/example/admsvc/config/ImpersonationConfig.java \
        gen-adm-starter/src/main/java/com/example/admsvc/application/service/ImpersonationSessionService.java \
        gen-adm-starter/src/main/java/com/example/admsvc/application/impl/ImpersonationSessionServiceImpl.java \
        gen-adm-starter/src/test/java/com/example/admsvc/application/impl/ImpersonationSessionServiceImplTest.java
git commit -m "feat: add ImpersonationSessionService with consent/expiry/self-approval guards"
```

---

## Task 3: REST layer — ImpersonationSessionController

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/CreateImpersonationRequest.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/ImpersonationSessionResponse.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/controller/ImpersonationSessionController.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/api/controller/ImpersonationSessionControllerTest.java`

**Interfaces:**
- Consumes: `ImpersonationSessionService` (Task 2, exact signatures above), `PermissionChecker.has(GenAdmPrincipal, String): boolean` / `.require(GenAdmPrincipal, String): void` (pre-existing), `GenAdmPrincipal.tenantId()`/`.userId()` (pre-existing), `GlobalExceptionHandler` (pre-existing, unmodified).
- Produces: REST routes under `/api/v1/impersonation-requests` — `POST /`, `GET /`, `POST /{id}/approve`, `POST /{id}/reject`, `POST /{id}/end`. `ImpersonationSessionResponse.from(ImpersonationSessionEntity): ImpersonationSessionResponse` — no other task depends on this, it is the terminal DTO.

- [ ] **Step 1: Create the request and response DTOs**

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/CreateImpersonationRequest.java`:
```java
package com.example.admsvc.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateImpersonationRequest(
        @NotNull UUID targetUserId,
        @NotBlank String reason,
        @Min(1) @Max(480) int ttlMinutes) {
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/ImpersonationSessionResponse.java`:
```java
package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;

import java.time.Instant;
import java.util.UUID;

public record ImpersonationSessionResponse(
        UUID id,
        UUID tenantId,
        UUID requestedByUserId,
        UUID targetUserId,
        String reason,
        String status,
        UUID reviewedByUserId,
        Instant reviewedAt,
        UUID endedByUserId,
        Instant endedAt,
        Instant expiresAt,
        Instant createdAt) {

    public static ImpersonationSessionResponse from(ImpersonationSessionEntity session) {
        return new ImpersonationSessionResponse(
                session.getId(),
                session.getTenantId(),
                session.getRequestedByUserId(),
                session.getTargetUserId(),
                session.getReason(),
                session.getStatus().name(),
                session.getReviewedByUserId(),
                session.getReviewedAt(),
                session.getEndedByUserId(),
                session.getEndedAt(),
                session.getExpiresAt(),
                session.getCreatedAt());
    }
}
```

- [ ] **Step 2: Write the failing controller test first**

`gen-adm-starter/src/test/java/com/example/admsvc/api/controller/ImpersonationSessionControllerTest.java`:
```java
package com.example.admsvc.api.controller;

import com.example.admsvc.api.advice.GlobalExceptionHandler;
import com.example.admsvc.application.service.ImpersonationSessionService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ImpersonationSessionControllerTest {

    private static final String REQUEST_PERM = "adm:impersonation:request";
    private static final String MANAGE_PERM = "adm:impersonation:manage";

    private ImpersonationSessionService impersonationSessionService;
    private PermissionChecker permissionChecker;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private final TestPrincipal principal = new TestPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        impersonationSessionService = mock(ImpersonationSessionService.class);
        permissionChecker = mock(PermissionChecker.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ImpersonationSessionController(impersonationSessionService, permissionChecker))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null));
    }

    private ImpersonationSessionEntity session(ImpersonationSessionStatus status, UUID requestedBy, UUID target) {
        return ImpersonationSessionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(principal.tenantId())
                .requestedByUserId(requestedBy)
                .targetUserId(target)
                .reason("support ticket #42")
                .status(status)
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build();
    }

    @Test
    void createsASessionWhenPrincipalHasRequestPermission() throws Exception {
        doNothing().when(permissionChecker).require(eq(principal), eq(REQUEST_PERM));
        UUID targetUserId = UUID.randomUUID();
        ImpersonationSessionEntity created = session(ImpersonationSessionStatus.PENDING_CONSENT,
                principal.userId(), targetUserId);
        when(impersonationSessionService.create(eq(principal.tenantId()), eq(principal.userId()),
                eq(targetUserId), eq("support ticket #42"), eq(30))).thenReturn(created);

        mockMvc.perform(post("/api/v1/impersonation-requests")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("targetUserId", targetUserId.toString());
                            put("reason", "support ticket #42");
                            put("ttlMinutes", 30);
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_CONSENT"));
    }

    @Test
    void returns403WhenPrincipalLacksRequestPermission() throws Exception {
        doThrow(new GenAdmForbiddenException("Missing required permission: " + REQUEST_PERM))
                .when(permissionChecker).require(eq(principal), eq(REQUEST_PERM));

        mockMvc.perform(post("/api/v1/impersonation-requests")
                        .contentType("application/json")
                        .content("{\"targetUserId\":\"" + UUID.randomUUID()
                                + "\",\"reason\":\"support ticket #42\",\"ttlMinutes\":30}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void listRequiresManagePermission() throws Exception {
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        when(impersonationSessionService.listActionable(principal.tenantId())).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/impersonation-requests"))
                .andExpect(status().isOk());
        verify(permissionChecker).require(principal, MANAGE_PERM);
    }

    @Test
    void approveReturns404ForACrossTenantSession() throws Exception {
        UUID sessionId = UUID.randomUUID();
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        when(impersonationSessionService.approve(eq(principal.tenantId()), eq(sessionId), eq(principal.userId())))
                .thenThrow(new GenAdmNotFoundException("Impersonation session not found: " + sessionId));

        mockMvc.perform(post("/api/v1/impersonation-requests/" + sessionId + "/approve"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void endPassesPrivilegedFalseWhenPrincipalLacksManagePermission() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(permissionChecker.has(principal, MANAGE_PERM)).thenReturn(false);
        ImpersonationSessionEntity ended = session(ImpersonationSessionStatus.ENDED, principal.userId(), UUID.randomUUID());
        when(impersonationSessionService.end(principal.tenantId(), sessionId, principal.userId(), false))
                .thenReturn(ended);

        mockMvc.perform(post("/api/v1/impersonation-requests/" + sessionId + "/end"))
                .andExpect(status().isOk());
        verify(impersonationSessionService).end(principal.tenantId(), sessionId, principal.userId(), false);
    }

    @Test
    void endPassesPrivilegedTrueWhenPrincipalHasManagePermission() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(permissionChecker.has(principal, MANAGE_PERM)).thenReturn(true);
        ImpersonationSessionEntity ended = session(ImpersonationSessionStatus.ENDED, UUID.randomUUID(), UUID.randomUUID());
        when(impersonationSessionService.end(principal.tenantId(), sessionId, principal.userId(), true))
                .thenReturn(ended);

        mockMvc.perform(post("/api/v1/impersonation-requests/" + sessionId + "/end"))
                .andExpect(status().isOk());
        verify(impersonationSessionService).end(principal.tenantId(), sessionId, principal.userId(), true);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.api.controller.ImpersonationSessionControllerTest"`
Expected: FAIL — compile error, `ImpersonationSessionController` does not exist yet.

- [ ] **Step 4: Write the controller**

`gen-adm-starter/src/main/java/com/example/admsvc/api/controller/ImpersonationSessionController.java`:
```java
package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.request.CreateImpersonationRequest;
import com.example.admsvc.api.dto.response.ImpersonationSessionResponse;
import com.example.admsvc.application.service.ImpersonationSessionService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/impersonation-requests")
public class ImpersonationSessionController {

    private static final String REQUEST_IMPERSONATION = "adm:impersonation:request";
    private static final String MANAGE_IMPERSONATION = "adm:impersonation:manage";

    private final ImpersonationSessionService impersonationSessionService;
    private final PermissionChecker permissionChecker;

    public ImpersonationSessionController(ImpersonationSessionService impersonationSessionService,
                                           PermissionChecker permissionChecker) {
        this.impersonationSessionService = impersonationSessionService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public ImpersonationSessionResponse create(@AuthenticationPrincipal GenAdmPrincipal principal,
                                                @Valid @RequestBody CreateImpersonationRequest request) {
        permissionChecker.require(principal, REQUEST_IMPERSONATION);
        return ImpersonationSessionResponse.from(impersonationSessionService.create(
                principal.tenantId(), principal.userId(), request.targetUserId(),
                request.reason(), request.ttlMinutes()));
    }

    @GetMapping
    public List<ImpersonationSessionResponse> list(@AuthenticationPrincipal GenAdmPrincipal principal) {
        permissionChecker.require(principal, MANAGE_IMPERSONATION);
        return impersonationSessionService.listActionable(principal.tenantId()).stream()
                .map(ImpersonationSessionResponse::from)
                .toList();
    }

    @PostMapping("/{sessionId}/approve")
    public ImpersonationSessionResponse approve(@AuthenticationPrincipal GenAdmPrincipal principal,
                                                 @PathVariable UUID sessionId) {
        permissionChecker.require(principal, MANAGE_IMPERSONATION);
        return ImpersonationSessionResponse.from(
                impersonationSessionService.approve(principal.tenantId(), sessionId, principal.userId()));
    }

    @PostMapping("/{sessionId}/reject")
    public ImpersonationSessionResponse reject(@AuthenticationPrincipal GenAdmPrincipal principal,
                                                @PathVariable UUID sessionId) {
        permissionChecker.require(principal, MANAGE_IMPERSONATION);
        return ImpersonationSessionResponse.from(
                impersonationSessionService.reject(principal.tenantId(), sessionId, principal.userId()));
    }

    @PostMapping("/{sessionId}/end")
    public ImpersonationSessionResponse end(@AuthenticationPrincipal GenAdmPrincipal principal,
                                             @PathVariable UUID sessionId) {
        boolean privileged = permissionChecker.has(principal, MANAGE_IMPERSONATION);
        return ImpersonationSessionResponse.from(
                impersonationSessionService.end(principal.tenantId(), sessionId, principal.userId(), privileged));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.api.controller.ImpersonationSessionControllerTest"`
Expected: PASS (6 tests) — MockMvc standalone setup, no Spring context, no database; expected to actually run here.

- [ ] **Step 6: Commit**

```bash
git add gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/CreateImpersonationRequest.java \
        gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/ImpersonationSessionResponse.java \
        gen-adm-starter/src/main/java/com/example/admsvc/api/controller/ImpersonationSessionController.java \
        gen-adm-starter/src/test/java/com/example/admsvc/api/controller/ImpersonationSessionControllerTest.java
git commit -m "feat: add impersonation-requests REST controller (create/list/approve/reject/end)"
```

---

## Task 4: Full-flow integration test

**Files:**
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/ImpersonationIntegrationTest.java`

**Interfaces:**
- Consumes: `ImpersonationSessionServiceImpl` (Task 2) wired through the real Spring context (`GenAdmAutoConfiguration`, `TenantContextAspect`, Flyway migrations from Task 1), `ImpersonationEventPublisher` (Task 2) overridden by a recording test bean.
- Produces: nothing consumed by later tasks — this is a verification-only task.

- [ ] **Step 1: Write the integration test**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/ImpersonationIntegrationTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.domain.port.ImpersonationEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = ImpersonationIntegrationTest.TestApp.class)
class ImpersonationIntegrationTest {

    @SpringBootApplication
    static class TestApp {

        @Bean
        RecordingImpersonationEventPublisher recordingImpersonationEventPublisher() {
            return new RecordingImpersonationEventPublisher();
        }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static class RecordingImpersonationEventPublisher implements ImpersonationEventPublisher {
        final List<String> events = new ArrayList<>();

        @Override
        public void onGranted(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
            events.add("GRANTED");
        }

        @Override
        public void onDenied(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
            events.add("DENIED");
        }

        @Override
        public void onEnded(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
            events.add("ENDED");
        }
    }

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    @Autowired
    private ImpersonationSessionServiceImpl impersonationSessionService;

    @Autowired
    private RecordingImpersonationEventPublisher eventPublisher;

    // A Spring singleton bean under this class's cached context, so its
    // event list persists across test methods. Reset before every test so
    // each method is independent regardless of run order.
    @BeforeEach
    void resetRecordedEvents() {
        eventPublisher.events.clear();
    }

    private void authenticateAs(UUID tenantId, UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new TestPrincipal(tenantId, userId), null));
    }

    @Test
    void requestApproveThenSelfEndFlow() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        ImpersonationSessionEntity created = impersonationSessionService.create(
                tenantId, requestedBy, target, "support ticket #42", 30);
        assertThat(created.getStatus()).isEqualTo(ImpersonationSessionStatus.PENDING_CONSENT);

        ImpersonationSessionEntity approved = impersonationSessionService.approve(tenantId, created.getId(), reviewer);
        assertThat(approved.getStatus()).isEqualTo(ImpersonationSessionStatus.ACTIVE);

        ImpersonationSessionEntity ended = impersonationSessionService.end(tenantId, created.getId(), requestedBy, false);
        assertThat(ended.getStatus()).isEqualTo(ImpersonationSessionStatus.ENDED);
        assertThat(ended.getEndedByUserId()).isEqualTo(requestedBy);

        assertThat(eventPublisher.events).containsExactly("GRANTED", "ENDED");
    }

    @Test
    void requestThenRejectFlow() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        ImpersonationSessionEntity created = impersonationSessionService.create(
                tenantId, requestedBy, UUID.randomUUID(), "support ticket #43", 30);

        ImpersonationSessionEntity rejected = impersonationSessionService.reject(tenantId, created.getId(), reviewer);

        assertThat(rejected.getStatus()).isEqualTo(ImpersonationSessionStatus.DENIED);
        assertThat(eventPublisher.events).containsExactly("DENIED");
    }

    @Test
    void selfApprovalIsRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        ImpersonationSessionEntity created = impersonationSessionService.create(
                tenantId, requestedBy, UUID.randomUUID(), "support ticket #44", 30);

        assertThatThrownBy(() -> impersonationSessionService.approve(tenantId, created.getId(), requestedBy))
                .isInstanceOf(GenAdmForbiddenException.class);
    }

    @Test
    void crossTenantLookupIs404() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        ImpersonationSessionEntity created = impersonationSessionService.create(
                tenantId, requestedBy, UUID.randomUUID(), "support ticket #45", 30);

        assertThatThrownBy(() -> impersonationSessionService.approve(
                UUID.randomUUID(), created.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmNotFoundException.class);
    }

    @Test
    void anAlreadyExpiredRequestCannotBeApproved() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        // ttlMinutes <= 0 is rejected by CreateImpersonationRequest's @Min(1)
        // at the REST boundary, but the service itself has no lower bound
        // (see Global Constraints) — used here to build an overdue row
        // deterministically instead of sleeping in a test.
        ImpersonationSessionEntity created = impersonationSessionService.create(
                tenantId, requestedBy, UUID.randomUUID(), "support ticket #46", -5);

        assertThatThrownBy(() -> impersonationSessionService.approve(tenantId, created.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);

        ImpersonationSessionEntity reloaded = impersonationSessionService.listActionable(tenantId).stream()
                .filter(s -> s.getId().equals(created.getId()))
                .findFirst()
                .orElse(null);
        assertThat(reloaded).isNull(); // EXPIRED sessions are filtered out of the actionable list
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.ImpersonationIntegrationTest"`
Expected: PASS (5 tests) — subject to the known Testcontainers/Docker sandbox limitation noted in Global Constraints; verify by code review if Docker isn't available here.

- [ ] **Step 3: Run the full module test suite**

Run: `./gradlew :gen-adm-starter:test`
Expected: PASS — all Phase 1/2/impersonation tests green together, no cross-test interference.

- [ ] **Step 4: Commit**

```bash
git add gen-adm-starter/src/test/java/com/example/admsvc/application/impl/ImpersonationIntegrationTest.java
git commit -m "test: add full-flow impersonation integration test (request/approve/end, reject, guards)"
```

---

## Task 5: Demo wiring, smoke test, docs

**Files:**
- Modify: `gen-adm-demo/src/main/resources/application.yaml`
- Modify: `scripts/smoke-test.sh`
- Modify: `README.md`
- Modify: `docs/integration-guide.md`

**Interfaces:**
- Consumes: `POST/GET /api/v1/impersonation-requests`, `POST /api/v1/impersonation-requests/{id}/{approve,reject,end}` (Task 3), `adm:impersonation:request`/`adm:impersonation:manage` permission codes (new in this task), the existing `PUT /api/v1/roles/{id}/permissions` route (Phase 1).
- Produces: nothing — this is the terminal wiring/documentation task.

- [ ] **Step 1: Add the two permission codes to the demo's catalog**

In `gen-adm-demo/src/main/resources/application.yaml`, extend the `gen-adm.permissions` list (currently ending at `adm:offboarding:manage`, line 35-36) by appending:
```yaml
    - code: adm:impersonation:request
      description: Request to impersonate another user in the same tenant
    - code: adm:impersonation:manage
      description: Approve, reject, list, and force-end impersonation sessions
```

- [ ] **Step 2: Extend the smoke test**

In `scripts/smoke-test.sh`, add `"adm:impersonation:request"` to the owner's `permissionCodes` list in the bootstrap call (the existing line 11):
```bash
curl -sf -X POST "$BASE_URL/internal/bootstrap" \
  -H "Content-Type: application/json" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"userId\":\"$USER_ID\",\"roleName\":\"owner\",\"permissionCodes\":[\"adm:roles:manage\",\"adm:offboarding:manage\",\"adm:impersonation:request\"]}"
echo
```

Then append at the end of the file, after the existing "Check offboarding job status" block and before "Smoke test complete.":
```bash
echo "== Grant adm:impersonation:manage to the viewer role =="
curl -sf -X PUT "$BASE_URL/api/v1/roles/$ROLE_ID/permissions" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d '{"permissionCodes":["adm:impersonation:manage"]}'
echo

echo "== Request to impersonate the second user =="
SESSION_ID=$(curl -sf -X POST "$BASE_URL/api/v1/impersonation-requests" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d "{\"targetUserId\":\"$OTHER_USER_ID\",\"reason\":\"support ticket #42\",\"ttlMinutes\":30}" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
echo "Created impersonation session: $SESSION_ID"

echo "== Approve as the second user (now holding adm:impersonation:manage) =="
curl -sf -X POST "$BASE_URL/api/v1/impersonation-requests/$SESSION_ID/approve" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo

echo "== End the session as the original requester =="
curl -sf -X POST "$BASE_URL/api/v1/impersonation-requests/$SESSION_ID/end" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID"
echo
```
Keep the final `echo "Smoke test complete."` as the last line of the file.

- [ ] **Step 3: Update README.md**

In `README.md`, append after the existing "Offboarding Saga" section (ends at line 43, "...and startup fails without one."):
```markdown

## Impersonation Requests

Tenant-scoped, intra-tenant impersonation consent workflow: a privileged
user requests to act as another user in the same tenant, a second
privileged user grants or denies consent, and the resulting active session
can be ended by either the impersonator or an admin. See
`docs/integration-guide.md` for the full walkthrough.

- `POST /api/v1/impersonation-requests` — request (`{ "targetUserId": "...", "reason": "...", "ttlMinutes": 30 }`), requires `adm:impersonation:request`
- `GET /api/v1/impersonation-requests` — list pending/active sessions, requires `adm:impersonation:manage`
- `POST /api/v1/impersonation-requests/{id}/approve` / `/reject` — requires `adm:impersonation:manage`; the reviewer cannot be the requester
- `POST /api/v1/impersonation-requests/{id}/end` — the impersonator can end their own session; anyone else needs `adm:impersonation:manage`

Gen_ADM only tracks consent and session state — it does not mint or scope
any token for actually acting as the impersonated user; that execution step
is the consuming application's responsibility.
```

- [ ] **Step 4: Update docs/integration-guide.md**

In `docs/integration-guide.md`, append after the existing "## 6. Offboarding Saga" section (ends at line 135, "...if automatic retry is needed."):
```markdown

## 7. Impersonation Requests

No mandatory bean to register — unlike offboarding's `SessionRevocationGateway`,
this feature has no required extension point. Optionally register an
`ImpersonationEventPublisher` bean to react to consent decisions (e.g. fire a
security notification); the default is a no-op:

```java
@Component
public class MyImpersonationEventPublisher implements ImpersonationEventPublisher {
    @Override
    public void onGranted(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
        // notify your security/audit system
    }

    @Override
    public void onDenied(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
    }

    @Override
    public void onEnded(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
    }
}
```

State machine: `PENDING_CONSENT → ACTIVE → ENDED` (self-end or admin
force-end), `PENDING_CONSENT → DENIED`, and `PENDING_CONSENT|ACTIVE →
EXPIRED` once `expiresAt` passes — checked lazily on every read/mutate, no
scheduler. `expiresAt` is set once at creation (`createdAt + ttlMinutes`)
and covers both "must be approved by" and "session must end by".

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/v1/impersonation-requests` | `{targetUserId, reason, ttlMinutes}` | `ttlMinutes` must be 1-480; 400 if `targetUserId` equals the caller |
| GET | `/api/v1/impersonation-requests` | — | Lists `PENDING_CONSENT`/`ACTIVE` sessions for the caller's tenant |
| POST | `/api/v1/impersonation-requests/{id}/approve` | — | 403 if the reviewer is the requester; 409 if not `PENDING_CONSENT` |
| POST | `/api/v1/impersonation-requests/{id}/reject` | — | Same guards as approve |
| POST | `/api/v1/impersonation-requests/{id}/end` | — | No permission needed if the caller is the requester; otherwise requires `adm:impersonation:manage`; 409 if not `ACTIVE` |

Impersonation is **intra-tenant only** — Gen_ADM has no cross-tenant
superadmin identity, so `requestedByUserId` and `targetUserId` are both
implicitly the caller's own tenant. Gen_ADM tracks consent and session
state only; it does not mint or scope a token for actually acting as the
impersonated user. See
`docs/superpowers/specs/2026-07-24-gen-adm-impersonation-design.md` for the
full design rationale.
```

- [ ] **Step 5: Run the full build**

Run: `./gradlew build`
Expected: PASS — full build including all modules and tests.

- [ ] **Step 6: Commit**

```bash
git add gen-adm-demo/src/main/resources/application.yaml scripts/smoke-test.sh README.md docs/integration-guide.md
git commit -m "docs: wire impersonation requests into gen-adm-demo, smoke test, README, and integration guide"
```

---

## Final review

After all five tasks are complete and committed, do one whole-branch review pass (not per-task): re-read `ImpersonationSessionServiceImpl` alongside `docs/superpowers/specs/2026-07-24-gen-adm-impersonation-design.md` and confirm every guard (self-impersonation, self-approval, self-end-vs-privileged, lazy expiry, cross-tenant 404) is actually exercised by at least one test, and that `README.md`/`docs/integration-guide.md` describe the shipped behavior exactly, not an earlier draft of it.
