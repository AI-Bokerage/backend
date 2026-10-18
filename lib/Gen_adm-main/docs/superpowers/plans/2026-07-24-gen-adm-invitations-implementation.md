# Gen_ADM Invitations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a tenant-scoped member-invitation flow to Gen_ADM: create → token-authenticated accept (assigns roles to a caller-supplied userId) → cancel, with lazy expiry, a duplicate-invite guard, and no user-table or mail dependency.

**Architecture:** Additive to the existing `gen-adm-starter`/`gen-adm-demo` split, plus one small, additive touch to Phase 1's `UserRoleAssignmentService` (a new method, no existing signature changed). The `invitations` table deliberately has **no RLS** — `accept`'s token-only lookup would be permanently broken by a forced tenant policy, since tenant isn't known until after that lookup returns. `accept` itself is Gen_ADM's first public, no-principal endpoint: it is NOT `@Transactional` (so `TenantContextAspect` never tries to resolve a tenant for it), does its own tenant-context-free work directly against the RLS-less `invitations` table, then delegates to a separate `@Service` bean, `InvitationAcceptanceExecutor`, whose `@Transactional` method carries `@TenantIdParam` — the exact shape already established by `OffboardingStepExecutor.execute(@TenantIdParam UUID tenantId, UUID jobId)`.

**Tech Stack:** Same as Phases 1-3 — Java 21, Spring Boot 4.0.6, Gradle 9.4.1, PostgreSQL, Flyway, Spring Data JPA, Spring AOP, Lombok, Jackson, JUnit 5, Mockito, AssertJ, Testcontainers. No new dependencies.

## Global Constraints

- Package root stays `com.example.admsvc` — new code lives alongside existing code, not in a separate module.
- **`invitations` gets NO Flyway RLS migration and NO `ENABLE`/`FORCE ROW LEVEL SECURITY`.** This is the one exception to every other Gen_ADM table's pattern, and it's deliberate — see the spec's Architecture section. Tenant isolation for this table comes entirely from explicit `tenant_id` predicates in `create`'s duplicate check, `listActionable`, and `cancel`'s lookup — never from RLS.
- `accept(String token, UUID userId)` on `InvitationServiceImpl` must NOT be annotated `@Transactional`. If it were, `TenantContextAspect` would throw `GenAdmConfigException` before the token lookup that discovers the tenant ever ran (no principal, no `@TenantIdParam` on its own signature).
- `InvitationAcceptanceExecutor` is a genuinely separate `@Service` bean (not a private method on `InvitationServiceImpl`) — self-invocation within one class bypasses Spring's AOP proxy entirely, which would silently skip `TenantContextAspect` for the inner call. `completeAcceptance` carries `@TenantIdParam` on its `tenantId` parameter, mirroring `OffboardingStepExecutor.execute`'s exact shape.
- `UserRoleAssignmentService.assignRole(UUID tenantId, UUID userId, UUID roleId)` is **never modified**. A new method, `assignRoleFromInvitation(@TenantIdParam UUID tenantId, UUID userId, UUID roleId)`, is added alongside it — required because a direct call to `assignRole` from `completeAcceptance` would be independently intercepted by `TenantContextAspect` (a separate-bean call), and `assignRole` has no `@TenantIdParam`/principal of its own to resolve from.
- A single `expiresAt`, computed once at creation as `createdAt + gen-adm.invitation-ttl-days` (a new `GenAdmProperties.invitationTtlDays`, default `7`), covers the whole PENDING window — never recalculated.
- Lazy, read-time expiry only — no scheduler anywhere in this feature. `listActionable`, `create`'s duplicate-check, `cancel`, and `accept`'s pre-delegation check all flip an overdue `PENDING` row to `EXPIRED` before acting on it; acting on an already-overdue row is a conflict (409), never a silent success.
- Role validation at creation: every `roleId` in `CreateInvitationRequest.roleIds` must exist in the caller's tenant (`RoleRepository.existsByIdAndTenantId`, a new method added to the existing repository) — 400 (`GenAdmValidationException`) otherwise. No "deprecated role" or "impersonation role" concept exists on Gen_ADM's `RoleEntity` — don't invent one.
- Duplicate-invite guard: `create` rejects (409, `GenAdmConflictException`) if an active `PENDING` invitation already exists for that email in that tenant — checked after first lazily expiring any overdue row for that email.
- `accept` never creates a user account — `userId` is supplied by the caller (their own signup/auth flow already created the real account). `accept` only assigns the invitation's roles to that `userId` and flips the invitation to `ACCEPTED`.
- `create`/`listActionable`/`cancel` are ordinary `@Transactional` methods with `tenantId` as an explicit (non-`@TenantIdParam`) parameter — exactly like `OffboardingServiceImpl.initiate`/`getJob`/`retry` — meaning `TenantContextAspect` resolves tenant from the caller's `GenAdmPrincipal`, not from that parameter. Any test calling these methods directly must call `authenticateAs(...)` first (set `SecurityContextHolder`), exactly like `OffboardingIntegrationTest`/`ImpersonationIntegrationTest` already do — this is a Java-level aspect precondition, independent of RLS.
- Cross-tenant invitation lookups (`cancel`) surface as 404 (`GenAdmNotFoundException`), never 403 — same anti-enumeration convention as every other Gen_ADM feature.
- No separate audit-log table — `invitedByUserId`/`acceptedAt`/`cancelledAt`/`cancelledByUserId` on the invitation row itself are the audit trail.
- No mail/SMTP dependency anywhere in this plan. `create` returns the plaintext token in its response and fires an optional `InvitationEventPublisher.onCreated(...)` (no-op default) — nothing else.
- `InvitationController` lives at `/api/v1/invitations` (no `/adm/` path segment).
- Reuse `PermissionChecker`, `GenAdmPrincipal`, `GlobalExceptionHandler`, `TenantContextAspect` completely unmodified. No new exception subclasses.
- One final whole-branch review at the end of all tasks, not one per task.
- Known sandbox limitation carried forward from prior phases: Testcontainers-based tests (Tasks 1 and 5) can only be verified by code review in this sandbox (confirmed Docker-detection limitation, not a code defect). Testcontainers' default DB user in this test setup is a superuser, so **RLS itself is never actually exercised by any test in this suite** (including pre-existing ones) — only repository-level `tenant_id = ?` filtering is. This is a pre-existing, accepted limitation, not something this plan needs to fix.

---

## Task 1: Invitation persistence — enum, entity, repository, migration

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/InvitationStatus.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/InvitationEntity.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/InvitationRepository.java`
- Create: `gen-adm-starter/src/main/resources/db/migration/genadm/V7__create_invitations_table.sql`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/InvitationPersistenceIntegrationTest.java`

**Interfaces:**
- Consumes: the existing `classpath:db/migration/genadm` Flyway location. `V7` is the next free migration version — `V1`-`V6` already exist. **No RLS migration for this table** — see Global Constraints.
- Produces: `InvitationEntity` (`UUID id`, `UUID tenantId`, `String email`, `String tokenHash`, `InvitationStatus status`, `UUID invitedByUserId`, `String roleIdsJson` with `List<UUID> getRoleIds()`/`void setRoleIds(List<UUID>)` helpers, `Instant expiresAt`, `Instant acceptedAt`, `Instant cancelledAt`, `UUID cancelledByUserId`, `Long version`, `Instant createdAt`, `Instant updatedAt`, and a `@Transient String plaintextToken`) — later tasks depend on these exact field/method names. `InvitationRepository.findByTokenHash(String): Optional<InvitationEntity>`, `.findByIdAndTenantId(UUID, UUID): Optional<InvitationEntity>`, `.findAllByTenantIdAndStatus(UUID, InvitationStatus): List<InvitationEntity>`, `.findByTenantIdAndEmailAndStatus(UUID, String, InvitationStatus): Optional<InvitationEntity>` — Task 2/3 call these exact signatures.

- [ ] **Step 1: Create the status enum**

`gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/InvitationStatus.java`:
```java
package com.example.admsvc.domain.enums;

public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    CANCELLED,
    EXPIRED
}
```

- [ ] **Step 2: Write the failing persistence integration test first**

`gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/InvitationPersistenceIntegrationTest.java`:
```java
package com.example.admsvc.infrastructure.persistence;

import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
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
@SpringBootTest(classes = InvitationPersistenceIntegrationTest.TestApp.class)
class InvitationPersistenceIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private InvitationRepository repository;

    private InvitationEntity newInvitation(UUID tenantId, String email) {
        InvitationEntity invitation = InvitationEntity.builder()
                .tenantId(tenantId)
                .email(email)
                .tokenHash(UUID.randomUUID().toString())
                .invitedByUserId(UUID.randomUUID())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        invitation.setRoleIds(List.of(UUID.randomUUID()));
        return invitation;
    }

    @Test
    void savesAndFindsByTokenHashDefaultingToPending() {
        UUID tenantId = UUID.randomUUID();
        InvitationEntity saved = repository.saveAndFlush(newInvitation(tenantId, "a@example.com"));

        Optional<InvitationEntity> found = repository.findByTokenHash(saved.getTokenHash());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(found.get().getRoleIds()).hasSize(1);
    }

    @Test
    void findByIdAndTenantIdExcludesTheWrongTenant() {
        UUID tenantId = UUID.randomUUID();
        InvitationEntity saved = repository.saveAndFlush(newInvitation(tenantId, "b@example.com"));

        assertThat(repository.findByIdAndTenantId(saved.getId(), tenantId)).isPresent();
        assertThat(repository.findByIdAndTenantId(saved.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void findsAllPendingInvitationsForATenant() {
        UUID tenantId = UUID.randomUUID();
        InvitationEntity pending = repository.saveAndFlush(newInvitation(tenantId, "c@example.com"));
        InvitationEntity cancelled = newInvitation(tenantId, "d@example.com");
        cancelled.setStatus(InvitationStatus.CANCELLED);
        repository.saveAndFlush(cancelled);
        repository.saveAndFlush(newInvitation(UUID.randomUUID(), "e@example.com")); // different tenant

        List<InvitationEntity> pendingOnly = repository.findAllByTenantIdAndStatus(tenantId, InvitationStatus.PENDING);
        assertThat(pendingOnly).extracting(InvitationEntity::getId).containsExactly(pending.getId());
    }

    @Test
    void findsAPendingInvitationByTenantAndEmailForTheDuplicateGuard() {
        UUID tenantId = UUID.randomUUID();
        InvitationEntity saved = repository.saveAndFlush(newInvitation(tenantId, "f@example.com"));

        Optional<InvitationEntity> found = repository.findByTenantIdAndEmailAndStatus(
                tenantId, "f@example.com", InvitationStatus.PENDING);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.persistence.InvitationPersistenceIntegrationTest"`
Expected: FAIL — compile error, `InvitationEntity`/`InvitationRepository` do not exist yet.

- [ ] **Step 4: Create the entity**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/InvitationEntity.java`:
```java
package com.example.admsvc.infrastructure.persistence.entity;

import com.example.admsvc.domain.enums.InvitationStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invitations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "invited_by_user_id", nullable = false)
    private UUID invitedByUserId;

    /**
     * JSON array of role UUIDs to assign when the invitation is accepted,
     * e.g. '["uuid1","uuid2"]'. Plain TEXT column — written once at
     * creation, read once at accept, never queried by role.
     */
    @Column(name = "role_ids", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String roleIdsJson = "[]";

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * One-time plaintext token, populated only on the object returned by
     * {@code create()} — never persisted, never retrievable again after
     * that call returns.
     */
    @Transient
    private String plaintextToken;

    public List<UUID> getRoleIds() {
        try {
            return MAPPER.readValue(roleIdsJson, new TypeReference<List<UUID>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    public void setRoleIds(List<UUID> roleIds) {
        try {
            this.roleIdsJson = MAPPER.writeValueAsString(roleIds == null ? List.of() : roleIds);
        } catch (JsonProcessingException e) {
            this.roleIdsJson = "[]";
        }
    }

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = Instant.now();
        if (status == null) {
            status = InvitationStatus.PENDING;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
```

- [ ] **Step 5: Create the repository**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/InvitationRepository.java`:
```java
package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {

    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    Optional<InvitationEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<InvitationEntity> findAllByTenantIdAndStatus(UUID tenantId, InvitationStatus status);

    Optional<InvitationEntity> findByTenantIdAndEmailAndStatus(UUID tenantId, String email, InvitationStatus status);
}
```

- [ ] **Step 6: Create the migration (no RLS)**

`gen-adm-starter/src/main/resources/db/migration/genadm/V7__create_invitations_table.sql`:
```sql
-- No RLS on this table — accept() looks up by token only, before tenant is
-- known. A forced tenant_id policy would block that lookup unconditionally.
-- Tenant isolation for create/list/cancel comes from explicit tenant_id
-- predicates in those queries instead. See docs/superpowers/specs/
-- 2026-07-24-gen-adm-invitations-design.md for the full rationale.

CREATE TABLE invitations (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    token_hash            VARCHAR(64) NOT NULL UNIQUE,
    status                VARCHAR(20) NOT NULL,
    invited_by_user_id    UUID NOT NULL,
    role_ids              TEXT NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    accepted_at           TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    cancelled_by_user_id  UUID,
    version               BIGINT,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_invitations_tenant_email_status
    ON invitations(tenant_id, email, status);
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.persistence.InvitationPersistenceIntegrationTest"`
Expected: PASS (4 tests) — subject to the known Testcontainers/Docker sandbox limitation noted in Global Constraints; verify by code review if Docker isn't available here.

- [ ] **Step 8: Commit**

```bash
git add gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/InvitationStatus.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/InvitationEntity.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/InvitationRepository.java \
        gen-adm-starter/src/main/resources/db/migration/genadm/V7__create_invitations_table.sql \
        gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/InvitationPersistenceIntegrationTest.java
git commit -m "feat: add invitation persistence (entity, repository, migration — deliberately no RLS)"
```

---

## Task 2: Invitation event port + core service (create/list/cancel)

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/port/InvitationEventPublisher.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/event/NoOpInvitationEventPublisher.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/config/InvitationConfig.java`
- Modify: `gen-adm-starter/src/main/java/com/example/admsvc/config/properties/GenAdmProperties.java`
- Modify: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/RoleRepository.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/service/InvitationService.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/InvitationServiceImpl.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/InvitationServiceImplTest.java`

**Interfaces:**
- Consumes: `InvitationRepository`/`InvitationEntity`/`InvitationStatus` from Task 1 (exact signatures above). `GenAdmValidationException`, `GenAdmConflictException`, `GenAdmNotFoundException` from `com.example.admsvc.common.exception` (all pre-existing).
- Produces: `InvitationService` with `create(UUID tenantId, UUID invitedByUserId, String email, List<UUID> roleIds): InvitationEntity`, `listActionable(UUID tenantId): List<InvitationEntity>`, `cancel(UUID tenantId, UUID invitationId, UUID cancelledByUserId): InvitationEntity` — **Task 3 adds a 4th method, `accept`, to this same interface and modifies `InvitationServiceImpl`'s constructor.** `InvitationEventPublisher.onCreated(UUID, UUID, String, String)` — optional override point, `NoOpInvitationEventPublisher` is the default bean. `RoleRepository.existsByIdAndTenantId(UUID, UUID): boolean` — new method Task 2 adds to the existing repository; no other task depends on it besides this one's own `create()`.

- [ ] **Step 1: Create the event publisher port and no-op default**

`gen-adm-starter/src/main/java/com/example/admsvc/domain/port/InvitationEventPublisher.java`:
```java
package com.example.admsvc.domain.port;

import java.util.UUID;

/**
 * Optional synchronous hook for a consumer to react to a new invitation
 * (e.g. actually send the invite email) without Gen_ADM owning a mail/SMTP
 * dependency. Not an outbox — no persistence, no retry of the notification
 * itself. Defaults to a no-op
 * ({@link com.example.admsvc.infrastructure.event.NoOpInvitationEventPublisher}).
 */
public interface InvitationEventPublisher {

    void onCreated(UUID tenantId, UUID invitationId, String email, String plaintextToken);
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/event/NoOpInvitationEventPublisher.java`:
```java
package com.example.admsvc.infrastructure.event;

import com.example.admsvc.domain.port.InvitationEventPublisher;

import java.util.UUID;

public class NoOpInvitationEventPublisher implements InvitationEventPublisher {

    @Override
    public void onCreated(UUID tenantId, UUID invitationId, String email, String plaintextToken) {
    }
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/config/InvitationConfig.java`:
```java
package com.example.admsvc.config;

import com.example.admsvc.domain.port.InvitationEventPublisher;
import com.example.admsvc.infrastructure.event.NoOpInvitationEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InvitationConfig {

    @Bean
    @ConditionalOnMissingBean(InvitationEventPublisher.class)
    public InvitationEventPublisher invitationEventPublisher() {
        return new NoOpInvitationEventPublisher();
    }
}
```

- [ ] **Step 2: Add the configurable TTL property**

Replace the full contents of `gen-adm-starter/src/main/java/com/example/admsvc/config/properties/GenAdmProperties.java`:
```java
package com.example.admsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * No permission codes are hardcoded anywhere in Gen_ADM — the consuming
 * app supplies its own catalog here, and {@link com.example.admsvc.infrastructure.startup.PermissionCatalogInitializer}
 * upserts it into the {@code permissions} table on startup.
 */
@Data
@ConfigurationProperties(prefix = "gen-adm")
public class GenAdmProperties {

    private List<PermissionDefinition> permissions = new ArrayList<>();

    /** Invitation expiry window in days, applied once at creation. */
    private int invitationTtlDays = 7;

    public record PermissionDefinition(String code, String description) {
    }
}
```

- [ ] **Step 3: Add the role-existence check to RoleRepository**

Replace the full contents of `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/RoleRepository.java`:
```java
package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    Optional<RoleEntity> findByTenantIdAndName(UUID tenantId, String name);

    List<RoleEntity> findAllByTenantId(UUID tenantId);

    boolean existsByTenantId(UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
}
```

- [ ] **Step 4: Create the service interface**

`gen-adm-starter/src/main/java/com/example/admsvc/application/service/InvitationService.java`:
```java
package com.example.admsvc.application.service;

import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;

import java.util.List;
import java.util.UUID;

public interface InvitationService {

    InvitationEntity create(UUID tenantId, UUID invitedByUserId, String email, List<UUID> roleIds);

    List<InvitationEntity> listActionable(UUID tenantId);

    InvitationEntity cancel(UUID tenantId, UUID invitationId, UUID cancelledByUserId);
}
```

- [ ] **Step 5: Write the failing unit test first**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/InvitationServiceImplTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmValidationException;
import com.example.admsvc.config.properties.GenAdmProperties;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.domain.port.InvitationEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceImplTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private InvitationEventPublisher eventPublisher;

    private InvitationServiceImpl service;

    @BeforeEach
    void setUp() {
        GenAdmProperties properties = new GenAdmProperties();
        properties.setInvitationTtlDays(7);
        service = new InvitationServiceImpl(invitationRepository, roleRepository, eventPublisher, properties);
        lenient().when(invitationRepository.saveAndFlush(any())).thenAnswer(inv -> {
            InvitationEntity invitation = inv.getArgument(0);
            if (invitation.getId() == null) {
                invitation.setId(UUID.randomUUID());
            }
            if (invitation.getStatus() == null) {
                invitation.setStatus(InvitationStatus.PENDING);
            }
            return invitation;
        });
    }

    @Test
    void createRejectsARoleThatDoesNotExistInTheTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(roleRepository.existsByIdAndTenantId(roleId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(tenantId, UUID.randomUUID(), "a@example.com", List.of(roleId)))
                .isInstanceOf(GenAdmValidationException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createPersistsAPendingInvitationAndFiresOnCreated() {
        UUID tenantId = UUID.randomUUID();
        UUID invitedBy = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(roleRepository.existsByIdAndTenantId(roleId, tenantId)).thenReturn(true);
        when(invitationRepository.findByTenantIdAndEmailAndStatus(tenantId, "a@example.com", InvitationStatus.PENDING))
                .thenReturn(Optional.empty());

        InvitationEntity invitation = service.create(tenantId, invitedBy, "a@example.com", List.of(roleId));

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitation.getEmail()).isEqualTo("a@example.com");
        assertThat(invitation.getInvitedByUserId()).isEqualTo(invitedBy);
        assertThat(invitation.getRoleIds()).containsExactly(roleId);
        assertThat(invitation.getExpiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
        assertThat(invitation.getPlaintextToken()).isNotBlank();
        verify(eventPublisher).onCreated(eq(tenantId), any(), eq("a@example.com"), eq(invitation.getPlaintextToken()));
    }

    @Test
    void createRejectsADuplicatePendingInvitationForTheSameEmail() {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(roleRepository.existsByIdAndTenantId(roleId, tenantId)).thenReturn(true);
        InvitationEntity existing = InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).email("a@example.com")
                .status(InvitationStatus.PENDING).expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        when(invitationRepository.findByTenantIdAndEmailAndStatus(tenantId, "a@example.com", InvitationStatus.PENDING))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(tenantId, UUID.randomUUID(), "a@example.com", List.of(roleId)))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void createAllowsANewInvitationWhenThePriorOneForThatEmailHasExpired() {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(roleRepository.existsByIdAndTenantId(roleId, tenantId)).thenReturn(true);
        InvitationEntity overdue = InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).email("a@example.com")
                .status(InvitationStatus.PENDING).expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)).build();
        when(invitationRepository.findByTenantIdAndEmailAndStatus(tenantId, "a@example.com", InvitationStatus.PENDING))
                .thenReturn(Optional.of(overdue));

        InvitationEntity invitation = service.create(tenantId, UUID.randomUUID(), "a@example.com", List.of(roleId));

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(overdue.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
    }

    @Test
    void listActionableFiltersOutInvitationsExpiredDuringTheCall() {
        UUID tenantId = UUID.randomUUID();
        InvitationEntity fresh = InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        InvitationEntity overdue = InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)).build();
        when(invitationRepository.findAllByTenantIdAndStatus(tenantId, InvitationStatus.PENDING))
                .thenReturn(List.of(fresh, overdue));

        List<InvitationEntity> actionable = service.listActionable(tenantId);

        assertThat(actionable).extracting(InvitationEntity::getId).containsExactly(fresh.getId());
        assertThat(overdue.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
    }

    @Test
    void cancelFlipsAPendingInvitationToCancelled() {
        UUID tenantId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID cancelledBy = UUID.randomUUID();
        InvitationEntity invitation = InvitationEntity.builder()
                .id(invitationId).tenantId(tenantId).status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        when(invitationRepository.findByIdAndTenantId(invitationId, tenantId)).thenReturn(Optional.of(invitation));

        InvitationEntity cancelled = service.cancel(tenantId, invitationId, cancelledBy);

        assertThat(cancelled.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
        assertThat(cancelled.getCancelledByUserId()).isEqualTo(cancelledBy);
    }

    @Test
    void cancelThrowsConflictWhenNotPending() {
        UUID tenantId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        InvitationEntity invitation = InvitationEntity.builder()
                .id(invitationId).tenantId(tenantId).status(InvitationStatus.CANCELLED)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        when(invitationRepository.findByIdAndTenantId(invitationId, tenantId)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.cancel(tenantId, invitationId, UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.InvitationServiceImplTest"`
Expected: FAIL — compile error, `InvitationServiceImpl` does not exist yet.

- [ ] **Step 7: Write the implementation**

`gen-adm-starter/src/main/java/com/example/admsvc/application/impl/InvitationServiceImpl.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.InvitationService;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.common.exception.GenAdmValidationException;
import com.example.admsvc.config.properties.GenAdmProperties;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.domain.port.InvitationEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class InvitationServiceImpl implements InvitationService {

    private final InvitationRepository invitationRepository;
    private final RoleRepository roleRepository;
    private final InvitationEventPublisher eventPublisher;
    private final GenAdmProperties properties;

    public InvitationServiceImpl(InvitationRepository invitationRepository,
                                  RoleRepository roleRepository,
                                  InvitationEventPublisher eventPublisher,
                                  GenAdmProperties properties) {
        this.invitationRepository = invitationRepository;
        this.roleRepository = roleRepository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    @Override
    @Transactional
    public InvitationEntity create(UUID tenantId, UUID invitedByUserId, String email, List<UUID> roleIds) {
        for (UUID roleId : roleIds) {
            if (!roleRepository.existsByIdAndTenantId(roleId, tenantId)) {
                throw new GenAdmValidationException("Role " + roleId + " does not exist in this tenant.");
            }
        }

        invitationRepository.findByTenantIdAndEmailAndStatus(tenantId, email, InvitationStatus.PENDING)
                .map(this::expireIfOverdue)
                .filter(existing -> existing.getStatus() == InvitationStatus.PENDING)
                .ifPresent(existing -> {
                    throw new GenAdmConflictException("An active invitation already exists for " + email);
                });

        String plaintextToken = UUID.randomUUID().toString();
        InvitationEntity invitation = InvitationEntity.builder()
                .tenantId(tenantId)
                .email(email)
                .tokenHash(hashToken(plaintextToken))
                .invitedByUserId(invitedByUserId)
                .expiresAt(Instant.now().plus(properties.getInvitationTtlDays(), ChronoUnit.DAYS))
                .plaintextToken(plaintextToken)
                .build();
        invitation.setRoleIds(roleIds);

        InvitationEntity saved = invitationRepository.saveAndFlush(invitation);
        eventPublisher.onCreated(tenantId, saved.getId(), email, plaintextToken);
        return saved;
    }

    @Override
    @Transactional
    public List<InvitationEntity> listActionable(UUID tenantId) {
        return invitationRepository.findAllByTenantIdAndStatus(tenantId, InvitationStatus.PENDING).stream()
                .map(this::expireIfOverdue)
                .filter(invitation -> invitation.getStatus() == InvitationStatus.PENDING)
                .toList();
    }

    @Override
    @Transactional
    public InvitationEntity cancel(UUID tenantId, UUID invitationId, UUID cancelledByUserId) {
        InvitationEntity invitation = expireIfOverdue(findOrThrow(tenantId, invitationId));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new GenAdmConflictException("Invitation is not pending: " + invitationId);
        }
        invitation.setStatus(InvitationStatus.CANCELLED);
        invitation.setCancelledAt(Instant.now());
        invitation.setCancelledByUserId(cancelledByUserId);
        return invitationRepository.saveAndFlush(invitation);
    }

    private InvitationEntity findOrThrow(UUID tenantId, UUID invitationId) {
        return invitationRepository.findByIdAndTenantId(invitationId, tenantId)
                .orElseThrow(() -> new GenAdmNotFoundException("Invitation not found: " + invitationId));
    }

    private InvitationEntity expireIfOverdue(InvitationEntity invitation) {
        if (invitation.getStatus() == InvitationStatus.PENDING && Instant.now().isAfter(invitation.getExpiresAt())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            return invitationRepository.saveAndFlush(invitation);
        }
        return invitation;
    }

    private static String hashToken(String plaintext) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.InvitationServiceImplTest"`
Expected: PASS (7 tests) — plain Mockito, no Spring context, no database; expected to actually run here.

- [ ] **Step 9: Commit**

```bash
git add gen-adm-starter/src/main/java/com/example/admsvc/domain/port/InvitationEventPublisher.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/event/NoOpInvitationEventPublisher.java \
        gen-adm-starter/src/main/java/com/example/admsvc/config/InvitationConfig.java \
        gen-adm-starter/src/main/java/com/example/admsvc/config/properties/GenAdmProperties.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/RoleRepository.java \
        gen-adm-starter/src/main/java/com/example/admsvc/application/service/InvitationService.java \
        gen-adm-starter/src/main/java/com/example/admsvc/application/impl/InvitationServiceImpl.java \
        gen-adm-starter/src/test/java/com/example/admsvc/application/impl/InvitationServiceImplTest.java
git commit -m "feat: add InvitationService (create/list/cancel) with lazy expiry and duplicate-invite guard"
```

---

## Task 3: Token-authenticated accept — executor split + UserRoleAssignmentService extension

**Files:**
- Modify: `gen-adm-starter/src/main/java/com/example/admsvc/application/service/UserRoleAssignmentService.java`
- Modify: `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/UserRoleAssignmentServiceImpl.java`
- Modify: `gen-adm-starter/src/main/java/com/example/admsvc/application/service/InvitationService.java`
- Modify: `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/InvitationServiceImpl.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/InvitationAcceptanceExecutor.java`
- Modify: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/UserRoleAssignmentServiceImplTest.java`
- Modify: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/InvitationServiceImplTest.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/InvitationAcceptanceExecutorTest.java`

**Interfaces:**
- Consumes: `InvitationRepository`/`InvitationEntity`/`InvitationStatus` (Task 1), `UserRoleAssignmentService`/`UserRoleAssignmentEntity` (pre-existing), `TenantIdParam` (pre-existing, `com.example.admsvc.domain.port`), `GenAdmNotFoundException`/`GenAdmConflictException` (pre-existing).
- Produces: `UserRoleAssignmentService.assignRoleFromInvitation(@TenantIdParam UUID tenantId, UUID userId, UUID roleId): UserRoleAssignmentEntity` — no other task depends on this besides this one. `InvitationService.accept(String token, UUID userId): InvitationEntity` — Task 4's controller calls this exact signature. `InvitationAcceptanceExecutor.completeAcceptance(UUID tenantId, UUID invitationId, UUID userId): InvitationEntity` — used only internally by `InvitationServiceImpl.accept`.

- [ ] **Step 1: Add the failing test for the new UserRoleAssignmentService method**

Add these two tests to the end of `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/UserRoleAssignmentServiceImplTest.java`, just before its final closing `}`:
```java

    @Test
    void assignRoleFromInvitationDelegatesToAssignRole() {
        UUID roleId = UUID.randomUUID();
        when(assignmentRepository.findByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId))
                .thenReturn(Optional.empty());
        when(assignmentRepository.save(any(UserRoleAssignmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserRoleAssignmentEntity assignment = service.assignRoleFromInvitation(tenantId, userId, roleId);

        assertThat(assignment.getTenantId()).isEqualTo(tenantId);
        assertThat(assignment.getUserId()).isEqualTo(userId);
        assertThat(assignment.getRoleId()).isEqualTo(roleId);
    }

    @Test
    void assignRoleFromInvitationRejectsADuplicateAssignment() {
        UUID roleId = UUID.randomUUID();
        when(assignmentRepository.findByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId))
                .thenReturn(Optional.of(UserRoleAssignmentEntity.builder()
                        .tenantId(tenantId).userId(userId).roleId(roleId).build()));

        assertThatThrownBy(() -> service.assignRoleFromInvitation(tenantId, userId, roleId))
                .isInstanceOf(GenAdmConflictException.class);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.UserRoleAssignmentServiceImplTest"`
Expected: FAIL — compile error, `assignRoleFromInvitation` does not exist yet.

- [ ] **Step 3: Add the method to the interface and implementation**

Replace the full contents of `gen-adm-starter/src/main/java/com/example/admsvc/application/service/UserRoleAssignmentService.java`:
```java
package com.example.admsvc.application.service;

import com.example.admsvc.domain.port.TenantIdParam;
import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface UserRoleAssignmentService {

    UserRoleAssignmentEntity assignRole(UUID tenantId, UUID userId, UUID roleId);

    void revokeRole(UUID tenantId, UUID userId, UUID roleId);

    List<UserRoleAssignmentEntity> listAssignments(UUID tenantId, UUID userId);

    Set<String> effectivePermissionCodes(UUID tenantId, UUID userId);

    /**
     * Programmatic-only, permission-check-free path for a fresh tenant's
     * first role — no HTTP route exists for this. Callable from a host
     * app's own tenant-provisioning flow with no authenticated principal
     * in context, which is why {@code tenantId} is {@code @TenantIdParam}-
     * annotated: {@link com.example.admsvc.infrastructure.security.TenantContextAspect}
     * uses it directly instead of looking for a principal.
     * Throws {@link com.example.admsvc.common.exception.GenAdmConflictException}
     * if the tenant already has at least one role.
     */
    UserRoleAssignmentEntity bootstrapTenant(
            @TenantIdParam UUID tenantId, UUID userId, String roleName, Set<String> permissionCodes);

    /**
     * Same assignment as {@link #assignRole}, for the invitation-accept
     * flow — which, like {@link #bootstrapTenant}, has no authenticated
     * principal in scope (it's a public, token-authenticated endpoint).
     * {@code tenantId} is resolved by the caller (from the invitation row
     * itself) and passed explicitly for {@code TenantContextAspect} to use.
     */
    UserRoleAssignmentEntity assignRoleFromInvitation(@TenantIdParam UUID tenantId, UUID userId, UUID roleId);
}
```

Add this method to the end of `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/UserRoleAssignmentServiceImpl.java`, just before its final closing `}` (and add the `TenantIdParam` import if not already present — it already is, from `bootstrapTenant`):
```java

    @Override
    @Transactional
    public UserRoleAssignmentEntity assignRoleFromInvitation(@TenantIdParam UUID tenantId, UUID userId, UUID roleId) {
        return assignRole(tenantId, userId, roleId);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.UserRoleAssignmentServiceImplTest"`
Expected: PASS (7 tests) — plain Mockito, no Spring context, no database.

- [ ] **Step 5: Write the failing test for the executor**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/InvitationAcceptanceExecutorTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.UserRoleAssignmentService;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationAcceptanceExecutorTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private UserRoleAssignmentService userRoleAssignmentService;

    private InvitationAcceptanceExecutor executor;

    // NOT a field initializer: MockitoExtension populates @Mock fields via
    // postProcessTestInstance, which runs after field initializers but
    // before @BeforeEach — building `executor` inline above would capture
    // both dependencies as null.
    @BeforeEach
    void setUp() {
        executor = new InvitationAcceptanceExecutor(invitationRepository, userRoleAssignmentService);
    }

    @Test
    void assignsEveryRoleAndFlipsToAccepted() {
        UUID tenantId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleA = UUID.randomUUID();
        UUID roleB = UUID.randomUUID();
        InvitationEntity invitation = InvitationEntity.builder()
                .id(invitationId).tenantId(tenantId).status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        invitation.setRoleIds(List.of(roleA, roleB));
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(invitationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        InvitationEntity result = executor.completeAcceptance(tenantId, invitationId, userId);

        assertThat(result.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(result.getAcceptedAt()).isNotNull();
        verify(userRoleAssignmentService).assignRoleFromInvitation(tenantId, userId, roleA);
        verify(userRoleAssignmentService).assignRoleFromInvitation(tenantId, userId, roleB);
    }

    @Test
    void throwsNotFoundWhenInvitationIsMissing() {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executor.completeAcceptance(UUID.randomUUID(), invitationId, UUID.randomUUID()))
                .isInstanceOf(GenAdmNotFoundException.class);
    }

    @Test
    void aMidLoopAssignmentFailurePreventsTheAcceptedFlip() {
        UUID tenantId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleA = UUID.randomUUID();
        InvitationEntity invitation = InvitationEntity.builder()
                .id(invitationId).tenantId(tenantId).status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        invitation.setRoleIds(List.of(roleA));
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        doThrow(new RuntimeException("boom")).when(userRoleAssignmentService)
                .assignRoleFromInvitation(tenantId, userId, roleA);

        assertThatThrownBy(() -> executor.completeAcceptance(tenantId, invitationId, userId))
                .isInstanceOf(RuntimeException.class);

        verify(invitationRepository, never()).saveAndFlush(any());
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.InvitationAcceptanceExecutorTest"`
Expected: FAIL — compile error, `InvitationAcceptanceExecutor` does not exist yet.

- [ ] **Step 7: Create the executor**

`gen-adm-starter/src/main/java/com/example/admsvc/application/impl/InvitationAcceptanceExecutor.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.UserRoleAssignmentService;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.domain.port.TenantIdParam;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Completes invitation acceptance once {@link InvitationServiceImpl#accept}'s
 * token lookup has resolved the invitation's own tenantId. Runs with no
 * {@code GenAdmPrincipal} in scope (public, token-authenticated endpoint),
 * so tenant context comes from an explicit {@code @TenantIdParam} argument,
 * exactly like Phase 2's {@code OffboardingStepExecutor}. A genuinely
 * separate {@code @Service} bean (not a private method on
 * {@code InvitationServiceImpl}) — self-invocation within one class bypasses
 * Spring's AOP proxy, which would silently skip {@code TenantContextAspect}
 * for this call.
 */
@Service
public class InvitationAcceptanceExecutor {

    private final InvitationRepository invitationRepository;
    private final UserRoleAssignmentService userRoleAssignmentService;

    public InvitationAcceptanceExecutor(InvitationRepository invitationRepository,
                                         UserRoleAssignmentService userRoleAssignmentService) {
        this.invitationRepository = invitationRepository;
        this.userRoleAssignmentService = userRoleAssignmentService;
    }

    @Transactional
    public InvitationEntity completeAcceptance(@TenantIdParam UUID tenantId, UUID invitationId, UUID userId) {
        InvitationEntity invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new GenAdmNotFoundException("Invitation not found: " + invitationId));

        for (UUID roleId : invitation.getRoleIds()) {
            userRoleAssignmentService.assignRoleFromInvitation(tenantId, userId, roleId);
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        return invitationRepository.saveAndFlush(invitation);
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.InvitationAcceptanceExecutorTest"`
Expected: PASS (3 tests) — plain Mockito, no Spring context, no database.

- [ ] **Step 9: Add the failing tests for `accept` to InvitationServiceImplTest**

In `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/InvitationServiceImplTest.java`:

Add this import alongside the existing ones:
```java
import com.example.admsvc.common.exception.GenAdmNotFoundException;
```

Add a new mock field right after the existing `@Mock private InvitationEventPublisher eventPublisher;` line:
```java

    @Mock
    private InvitationAcceptanceExecutor acceptanceExecutor;
```

Change the `service = new InvitationServiceImpl(...)` line inside `setUp()` to pass the new mock as a 5th constructor argument:
```java
        service = new InvitationServiceImpl(invitationRepository, roleRepository, eventPublisher, properties, acceptanceExecutor);
```

Add these four tests to the end of the class, just before its final closing `}`:
```java

    @Test
    void acceptThrowsNotFoundForAnUnknownToken() {
        when(invitationRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept("bogus-token", UUID.randomUUID()))
                .isInstanceOf(GenAdmNotFoundException.class);
        verifyNoInteractions(acceptanceExecutor);
    }

    @Test
    void acceptExpiresAnOverdueInvitationAndThrowsConflict() {
        InvitationEntity invitation = InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)).build();
        when(invitationRepository.findByTokenHash(any())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.accept("some-token", UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        verifyNoInteractions(acceptanceExecutor);
    }

    @Test
    void acceptThrowsConflictWhenNotPending() {
        InvitationEntity invitation = InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).status(InvitationStatus.CANCELLED)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        when(invitationRepository.findByTokenHash(any())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.accept("some-token", UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
        verifyNoInteractions(acceptanceExecutor);
    }

    @Test
    void acceptDelegatesToExecutorForAValidPendingInvitation() {
        UUID tenantId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InvitationEntity invitation = InvitationEntity.builder()
                .id(invitationId).tenantId(tenantId).status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        InvitationEntity accepted = InvitationEntity.builder()
                .id(invitationId).tenantId(tenantId).status(InvitationStatus.ACCEPTED).build();
        when(invitationRepository.findByTokenHash(any())).thenReturn(Optional.of(invitation));
        when(acceptanceExecutor.completeAcceptance(tenantId, invitationId, userId)).thenReturn(accepted);

        InvitationEntity result = service.accept("some-token", userId);

        assertThat(result.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        verify(acceptanceExecutor).completeAcceptance(tenantId, invitationId, userId);
    }
```

- [ ] **Step 10: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.InvitationServiceImplTest"`
Expected: FAIL — compile error, `InvitationServiceImpl` has no 5-arg constructor and no `accept` method yet.

- [ ] **Step 11: Add `accept` to the interface and implementation**

Add this method to `gen-adm-starter/src/main/java/com/example/admsvc/application/service/InvitationService.java`, inside the interface body:
```java

    InvitationEntity accept(String token, UUID userId);
```

Replace the full contents of `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/InvitationServiceImpl.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.InvitationService;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.common.exception.GenAdmValidationException;
import com.example.admsvc.config.properties.GenAdmProperties;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.domain.port.InvitationEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class InvitationServiceImpl implements InvitationService {

    private final InvitationRepository invitationRepository;
    private final RoleRepository roleRepository;
    private final InvitationEventPublisher eventPublisher;
    private final GenAdmProperties properties;
    private final InvitationAcceptanceExecutor acceptanceExecutor;

    public InvitationServiceImpl(InvitationRepository invitationRepository,
                                  RoleRepository roleRepository,
                                  InvitationEventPublisher eventPublisher,
                                  GenAdmProperties properties,
                                  InvitationAcceptanceExecutor acceptanceExecutor) {
        this.invitationRepository = invitationRepository;
        this.roleRepository = roleRepository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.acceptanceExecutor = acceptanceExecutor;
    }

    @Override
    @Transactional
    public InvitationEntity create(UUID tenantId, UUID invitedByUserId, String email, List<UUID> roleIds) {
        for (UUID roleId : roleIds) {
            if (!roleRepository.existsByIdAndTenantId(roleId, tenantId)) {
                throw new GenAdmValidationException("Role " + roleId + " does not exist in this tenant.");
            }
        }

        invitationRepository.findByTenantIdAndEmailAndStatus(tenantId, email, InvitationStatus.PENDING)
                .map(this::expireIfOverdue)
                .filter(existing -> existing.getStatus() == InvitationStatus.PENDING)
                .ifPresent(existing -> {
                    throw new GenAdmConflictException("An active invitation already exists for " + email);
                });

        String plaintextToken = UUID.randomUUID().toString();
        InvitationEntity invitation = InvitationEntity.builder()
                .tenantId(tenantId)
                .email(email)
                .tokenHash(hashToken(plaintextToken))
                .invitedByUserId(invitedByUserId)
                .expiresAt(Instant.now().plus(properties.getInvitationTtlDays(), ChronoUnit.DAYS))
                .plaintextToken(plaintextToken)
                .build();
        invitation.setRoleIds(roleIds);

        InvitationEntity saved = invitationRepository.saveAndFlush(invitation);
        eventPublisher.onCreated(tenantId, saved.getId(), email, plaintextToken);
        return saved;
    }

    @Override
    @Transactional
    public List<InvitationEntity> listActionable(UUID tenantId) {
        return invitationRepository.findAllByTenantIdAndStatus(tenantId, InvitationStatus.PENDING).stream()
                .map(this::expireIfOverdue)
                .filter(invitation -> invitation.getStatus() == InvitationStatus.PENDING)
                .toList();
    }

    @Override
    public InvitationEntity accept(String token, UUID userId) {
        String tokenHash = hashToken(token);
        InvitationEntity invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new GenAdmNotFoundException("Invitation not found or already used."));

        invitation = expireIfOverdue(invitation);
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new GenAdmConflictException("This invitation is no longer pending.");
        }

        return acceptanceExecutor.completeAcceptance(invitation.getTenantId(), invitation.getId(), userId);
    }

    @Override
    @Transactional
    public InvitationEntity cancel(UUID tenantId, UUID invitationId, UUID cancelledByUserId) {
        InvitationEntity invitation = expireIfOverdue(findOrThrow(tenantId, invitationId));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new GenAdmConflictException("Invitation is not pending: " + invitationId);
        }
        invitation.setStatus(InvitationStatus.CANCELLED);
        invitation.setCancelledAt(Instant.now());
        invitation.setCancelledByUserId(cancelledByUserId);
        return invitationRepository.saveAndFlush(invitation);
    }

    private InvitationEntity findOrThrow(UUID tenantId, UUID invitationId) {
        return invitationRepository.findByIdAndTenantId(invitationId, tenantId)
                .orElseThrow(() -> new GenAdmNotFoundException("Invitation not found: " + invitationId));
    }

    private InvitationEntity expireIfOverdue(InvitationEntity invitation) {
        if (invitation.getStatus() == InvitationStatus.PENDING && Instant.now().isAfter(invitation.getExpiresAt())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            return invitationRepository.saveAndFlush(invitation);
        }
        return invitation;
    }

    private static String hashToken(String plaintext) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

Note: `accept` has **no `@Transactional`** — this is deliberate, see Global Constraints. Every other method keeps its annotation unchanged.

- [ ] **Step 12: Run tests to verify they pass**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.InvitationServiceImplTest" --tests "com.example.admsvc.application.impl.InvitationAcceptanceExecutorTest" --tests "com.example.admsvc.application.impl.UserRoleAssignmentServiceImplTest"`
Expected: PASS (11 + 3 + 7 = 21 tests) — plain Mockito, no Spring context, no database.

- [ ] **Step 13: Commit**

```bash
git add gen-adm-starter/src/main/java/com/example/admsvc/application/service/UserRoleAssignmentService.java \
        gen-adm-starter/src/main/java/com/example/admsvc/application/impl/UserRoleAssignmentServiceImpl.java \
        gen-adm-starter/src/main/java/com/example/admsvc/application/service/InvitationService.java \
        gen-adm-starter/src/main/java/com/example/admsvc/application/impl/InvitationServiceImpl.java \
        gen-adm-starter/src/main/java/com/example/admsvc/application/impl/InvitationAcceptanceExecutor.java \
        gen-adm-starter/src/test/java/com/example/admsvc/application/impl/UserRoleAssignmentServiceImplTest.java \
        gen-adm-starter/src/test/java/com/example/admsvc/application/impl/InvitationServiceImplTest.java \
        gen-adm-starter/src/test/java/com/example/admsvc/application/impl/InvitationAcceptanceExecutorTest.java
git commit -m "feat: add token-authenticated invitation accept (executor split, assignRoleFromInvitation)"
```

---

## Task 4: REST layer — InvitationController

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/CreateInvitationRequest.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/AcceptInvitationRequest.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/InvitationResponse.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/controller/InvitationController.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/api/controller/InvitationControllerTest.java`

**Interfaces:**
- Consumes: `InvitationService` (Task 2/3, exact signatures: `create(UUID, UUID, String, List<UUID>)`, `listActionable(UUID)`, `cancel(UUID, UUID, UUID)`, `accept(String, UUID)`), `PermissionChecker.has(GenAdmPrincipal, String): boolean` / `.require(GenAdmPrincipal, String): void` (pre-existing), `GenAdmPrincipal.tenantId()`/`.userId()` (pre-existing), `GlobalExceptionHandler` (pre-existing, unmodified).
- Produces: REST routes under `/api/v1/invitations` — `POST /`, `GET /`, `POST /{id}/cancel`, `POST /{token}/accept`. No other task depends on `InvitationResponse` — it is the terminal DTO.

- [ ] **Step 1: Create the request and response DTOs**

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/CreateInvitationRequest.java`:
```java
package com.example.admsvc.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record CreateInvitationRequest(
        @NotBlank @Email String email,
        @NotEmpty List<UUID> roleIds) {
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/AcceptInvitationRequest.java`:
```java
package com.example.admsvc.api.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AcceptInvitationRequest(@NotNull UUID userId) {
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/InvitationResponse.java`:
```java
package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvitationResponse(
        UUID id,
        UUID tenantId,
        String email,
        String status,
        List<UUID> roleIds,
        UUID invitedByUserId,
        Instant expiresAt,
        Instant acceptedAt,
        Instant cancelledAt,
        UUID cancelledByUserId,
        Instant createdAt,
        String token) {

    public static InvitationResponse from(InvitationEntity invitation) {
        return build(invitation, null);
    }

    public static InvitationResponse forCreate(InvitationEntity invitation, String plaintextToken) {
        return build(invitation, plaintextToken);
    }

    private static InvitationResponse build(InvitationEntity invitation, String token) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getTenantId(),
                invitation.getEmail(),
                invitation.getStatus().name(),
                invitation.getRoleIds(),
                invitation.getInvitedByUserId(),
                invitation.getExpiresAt(),
                invitation.getAcceptedAt(),
                invitation.getCancelledAt(),
                invitation.getCancelledByUserId(),
                invitation.getCreatedAt(),
                token);
    }
}
```

- [ ] **Step 2: Write the failing controller test first**

`gen-adm-starter/src/test/java/com/example/admsvc/api/controller/InvitationControllerTest.java`:
```java
package com.example.admsvc.api.controller;

import com.example.admsvc.api.advice.GlobalExceptionHandler;
import com.example.admsvc.application.service.InvitationService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InvitationControllerTest {

    private static final String MANAGE_PERM = "adm:invitations:manage";

    private InvitationService invitationService;
    private PermissionChecker permissionChecker;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private final TestPrincipal principal = new TestPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        invitationService = mock(InvitationService.class);
        permissionChecker = mock(PermissionChecker.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InvitationController(invitationService, permissionChecker))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null));
    }

    private InvitationEntity invitation(InvitationStatus status, String token) {
        InvitationEntity entity = InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(principal.tenantId()).email("a@example.com")
                .status(status).invitedByUserId(principal.userId())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .plaintextToken(token)
                .build();
        entity.setRoleIds(List.of(UUID.randomUUID()));
        return entity;
    }

    @Test
    void createsAnInvitationWhenPrincipalHasPermissionAndReturnsTheToken() throws Exception {
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        InvitationEntity created = invitation(InvitationStatus.PENDING, "plaintext-token-123");
        UUID roleId = created.getRoleIds().get(0);
        when(invitationService.create(eq(principal.tenantId()), eq(principal.userId()), eq("a@example.com"), eq(List.of(roleId))))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/invitations")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("email", "a@example.com");
                            put("roleIds", List.of(roleId.toString()));
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.token").value("plaintext-token-123"));
    }

    @Test
    void returns403WhenPrincipalLacksPermissionToCreate() throws Exception {
        doThrow(new GenAdmForbiddenException("Missing required permission: " + MANAGE_PERM))
                .when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));

        mockMvc.perform(post("/api/v1/invitations")
                        .contentType("application/json")
                        .content("{\"email\":\"a@example.com\",\"roleIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void listNeverExposesTheToken() throws Exception {
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        when(invitationService.listActionable(principal.tenantId()))
                .thenReturn(List.of(invitation(InvitationStatus.PENDING, "should-not-appear")));

        mockMvc.perform(get("/api/v1/invitations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].token").doesNotExist());
    }

    @Test
    void cancelReturns404ForACrossTenantInvitation() throws Exception {
        UUID invitationId = UUID.randomUUID();
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        when(invitationService.cancel(eq(principal.tenantId()), eq(invitationId), eq(principal.userId())))
                .thenThrow(new GenAdmNotFoundException("Invitation not found: " + invitationId));

        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void acceptRequiresNoPermissionCheckAndNeverExposesTheToken() throws Exception {
        InvitationEntity accepted = invitation(InvitationStatus.ACCEPTED, "should-not-appear");
        UUID newUserId = UUID.randomUUID();
        when(invitationService.accept(eq("some-token"), eq(newUserId))).thenReturn(accepted);

        mockMvc.perform(post("/api/v1/invitations/some-token/accept")
                        .contentType("application/json")
                        .content("{\"userId\":\"" + newUserId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.token").doesNotExist());
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void acceptReturns409ForAnAlreadyUsedToken() throws Exception {
        when(invitationService.accept(eq("used-token"), any()))
                .thenThrow(new GenAdmConflictException("This invitation is no longer pending."));

        mockMvc.perform(post("/api/v1/invitations/used-token/accept")
                        .contentType("application/json")
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.api.controller.InvitationControllerTest"`
Expected: FAIL — compile error, `InvitationController` does not exist yet.

- [ ] **Step 4: Write the controller**

`gen-adm-starter/src/main/java/com/example/admsvc/api/controller/InvitationController.java`:
```java
package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.request.AcceptInvitationRequest;
import com.example.admsvc.api.dto.request.CreateInvitationRequest;
import com.example.admsvc.api.dto.response.InvitationResponse;
import com.example.admsvc.application.service.InvitationService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private static final String MANAGE_INVITATIONS = "adm:invitations:manage";

    private final InvitationService invitationService;
    private final PermissionChecker permissionChecker;

    public InvitationController(InvitationService invitationService, PermissionChecker permissionChecker) {
        this.invitationService = invitationService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public InvitationResponse create(@AuthenticationPrincipal GenAdmPrincipal principal,
                                      @Valid @RequestBody CreateInvitationRequest request) {
        permissionChecker.require(principal, MANAGE_INVITATIONS);
        InvitationEntity created = invitationService.create(
                principal.tenantId(), principal.userId(), request.email(), request.roleIds());
        return InvitationResponse.forCreate(created, created.getPlaintextToken());
    }

    @GetMapping
    public List<InvitationResponse> list(@AuthenticationPrincipal GenAdmPrincipal principal) {
        permissionChecker.require(principal, MANAGE_INVITATIONS);
        return invitationService.listActionable(principal.tenantId()).stream()
                .map(InvitationResponse::from)
                .toList();
    }

    @PostMapping("/{id}/cancel")
    public InvitationResponse cancel(@AuthenticationPrincipal GenAdmPrincipal principal,
                                      @PathVariable UUID id) {
        permissionChecker.require(principal, MANAGE_INVITATIONS);
        return InvitationResponse.from(
                invitationService.cancel(principal.tenantId(), id, principal.userId()));
    }

    @PostMapping("/{token}/accept")
    public InvitationResponse accept(@PathVariable String token,
                                      @Valid @RequestBody AcceptInvitationRequest request) {
        return InvitationResponse.from(invitationService.accept(token, request.userId()));
    }
}
```

Note: `accept` takes no `@AuthenticationPrincipal` parameter at all and calls no `permissionChecker` method — it is the only endpoint in this controller (and in Gen_ADM) that resolves no principal.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.api.controller.InvitationControllerTest"`
Expected: PASS (6 tests) — MockMvc standalone setup, no Spring context, no database.

- [ ] **Step 6: Commit**

```bash
git add gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/CreateInvitationRequest.java \
        gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/AcceptInvitationRequest.java \
        gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/InvitationResponse.java \
        gen-adm-starter/src/main/java/com/example/admsvc/api/controller/InvitationController.java \
        gen-adm-starter/src/test/java/com/example/admsvc/api/controller/InvitationControllerTest.java
git commit -m "feat: add invitations REST controller (create/list/cancel/accept)"
```

---

## Task 5: Full-flow integration test

**Files:**
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/InvitationIntegrationTest.java`

**Interfaces:**
- Consumes: `InvitationServiceImpl` (Tasks 2/3) wired through the real Spring context (`GenAdmAutoConfiguration`, `TenantContextAspect`, Flyway migrations from Task 1), `RoleRepository`/`UserRoleAssignmentRepository` (pre-existing, for fixtures and assertions), `InvitationEventPublisher` (Task 2) overridden by a recording test bean.
- Produces: nothing consumed by later tasks — verification-only.

- [ ] **Step 1: Write the integration test**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/InvitationIntegrationTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.domain.port.InvitationEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import com.example.admsvc.infrastructure.persistence.repository.UserRoleAssignmentRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = InvitationIntegrationTest.TestApp.class)
class InvitationIntegrationTest {

    @SpringBootApplication
    static class TestApp {

        @Bean
        RecordingInvitationEventPublisher recordingInvitationEventPublisher() {
            return new RecordingInvitationEventPublisher();
        }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static class RecordingInvitationEventPublisher implements InvitationEventPublisher {
        final List<String> events = new ArrayList<>();

        @Override
        public void onCreated(UUID tenantId, UUID invitationId, String email, String plaintextToken) {
            events.add("CREATED:" + email);
        }
    }

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    @Autowired
    private InvitationServiceImpl invitationService;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleAssignmentRepository assignmentRepository;

    @Autowired
    private RecordingInvitationEventPublisher eventPublisher;

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

    private UUID createRole(UUID tenantId) {
        return roleRepository.saveAndFlush(RoleEntity.builder()
                .tenantId(tenantId).name("member-" + UUID.randomUUID()).build()).getId();
    }

    @Test
    void createAcceptFlowAssignsRolesAndMarksAccepted() {
        UUID tenantId = UUID.randomUUID();
        UUID invitedBy = UUID.randomUUID();
        authenticateAs(tenantId, invitedBy);
        UUID roleId = createRole(tenantId);

        InvitationEntity created = invitationService.create(tenantId, invitedBy, "new@example.com", List.of(roleId));
        assertThat(created.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(created.getPlaintextToken()).isNotBlank();
        assertThat(eventPublisher.events).containsExactly("CREATED:new@example.com");

        // accept is a public, token-authenticated endpoint — prove it works
        // with no principal in scope at all, not just that one happens to
        // be left over from the create() call above.
        SecurityContextHolder.clearContext();
        UUID newUserId = UUID.randomUUID();
        InvitationEntity accepted = invitationService.accept(created.getPlaintextToken(), newUserId);

        assertThat(accepted.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        List<UserRoleAssignmentEntity> assignments = assignmentRepository.findAllByTenantIdAndUserId(tenantId, newUserId);
        assertThat(assignments).extracting(UserRoleAssignmentEntity::getRoleId).containsExactly(roleId);
    }

    @Test
    void createCancelFlow() {
        UUID tenantId = UUID.randomUUID();
        UUID invitedBy = UUID.randomUUID();
        authenticateAs(tenantId, invitedBy);
        UUID roleId = createRole(tenantId);

        InvitationEntity created = invitationService.create(tenantId, invitedBy, "cancel@example.com", List.of(roleId));
        InvitationEntity cancelled = invitationService.cancel(tenantId, created.getId(), invitedBy);

        assertThat(cancelled.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
        assertThat(invitationService.listActionable(tenantId)).isEmpty();
    }

    @Test
    void duplicatePendingInvitationForTheSameEmailIsRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID invitedBy = UUID.randomUUID();
        authenticateAs(tenantId, invitedBy);
        UUID roleId = createRole(tenantId);
        invitationService.create(tenantId, invitedBy, "dup@example.com", List.of(roleId));

        assertThatThrownBy(() -> invitationService.create(tenantId, invitedBy, "dup@example.com", List.of(roleId)))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void anExpiredInvitationCannotBeAccepted() {
        UUID tenantId = UUID.randomUUID();
        UUID invitedBy = UUID.randomUUID();
        authenticateAs(tenantId, invitedBy);
        UUID roleId = createRole(tenantId);
        InvitationEntity created = invitationService.create(tenantId, invitedBy, "expired@example.com", List.of(roleId));

        InvitationEntity stored = invitationRepository.findById(created.getId()).orElseThrow();
        stored.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        invitationRepository.saveAndFlush(stored);

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> invitationService.accept(created.getPlaintextToken(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void acceptingAnUnknownTokenIs404() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> invitationService.accept("no-such-token", UUID.randomUUID()))
                .isInstanceOf(GenAdmNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.InvitationIntegrationTest"`
Expected: PASS (5 tests) — subject to the known Testcontainers/Docker sandbox limitation noted in Global Constraints; verify by code review if Docker isn't available here.

- [ ] **Step 3: Run the full module test suite**

Run: `./gradlew :gen-adm-starter:test`
Expected: PASS — all Phase 1-3 and invitations tests green together, no cross-test interference.

- [ ] **Step 4: Commit**

```bash
git add gen-adm-starter/src/test/java/com/example/admsvc/application/impl/InvitationIntegrationTest.java
git commit -m "test: add full-flow invitation integration test (create/accept, cancel, duplicate/expiry guards)"
```

---

## Task 6: Demo wiring, smoke test, docs

**Files:**
- Modify: `gen-adm-demo/src/main/resources/application.yaml`
- Modify: `scripts/smoke-test.sh`
- Modify: `README.md`
- Modify: `docs/integration-guide.md`

**Interfaces:**
- Consumes: `POST/GET /api/v1/invitations`, `POST /api/v1/invitations/{id}/cancel`, `POST /api/v1/invitations/{token}/accept` (Task 4), `adm:invitations:manage` permission code (new in this task), the existing `PUT /api/v1/roles/{id}/permissions` route (Phase 1, unused by this task's smoke-test addition but already present).
- Produces: nothing — terminal wiring/documentation task.

- [ ] **Step 1: Add the permission code to the demo's catalog**

In `gen-adm-demo/src/main/resources/application.yaml`, extend the `gen-adm.permissions` list (currently ending at `adm:impersonation:manage`) by appending:
```yaml
    - code: adm:invitations:manage
      description: Create, list, and cancel member invitations
```

- [ ] **Step 2: Extend the smoke test**

In `scripts/smoke-test.sh`, add `"adm:invitations:manage"` to the owner's `permissionCodes` list in the bootstrap call (the existing line 11):
```bash
curl -sf -X POST "$BASE_URL/internal/bootstrap" \
  -H "Content-Type: application/json" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"userId\":\"$USER_ID\",\"roleName\":\"owner\",\"permissionCodes\":[\"adm:roles:manage\",\"adm:offboarding:manage\",\"adm:impersonation:request\",\"adm:invitations:manage\"]}"
echo
```

Then append at the end of the file, after the existing "End the session as the original requester" block and before "Smoke test complete.":
```bash
echo "== Create an invitation for a new hire, granting the viewer role =="
INVITE_RESPONSE=$(curl -sf -X POST "$BASE_URL/api/v1/invitations" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d "{\"email\":\"newhire@example.com\",\"roleIds\":[\"$ROLE_ID\"]}")
echo "$INVITE_RESPONSE"
INVITE_TOKEN=$(echo "$INVITE_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo "== Accept the invitation as a brand-new user (no auth headers needed — the token is the auth) =="
NEW_USER_ID="$(uuidgen)"
curl -sf -X POST "$BASE_URL/api/v1/invitations/$INVITE_TOKEN/accept" \
  -H "Content-Type: application/json" \
  -d "{\"userId\":\"$NEW_USER_ID\"}"
echo

echo "== List invitations (the accepted one should no longer appear) =="
curl -sf "$BASE_URL/api/v1/invitations" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID"
echo
```
Keep the final `echo "Smoke test complete."` as the last line of the file.

- [ ] **Step 3: Update README.md**

In `README.md`, append after the existing "Impersonation Requests" section:
```markdown

## Invitations

Tenant-scoped member invitations: create an invitation for an email with a
set of roles, the invitee accepts with a one-time token (no login
required — the token itself is the authentication), and the roles are
assigned to a userId the invitee's own signup/auth flow already created.
See `docs/integration-guide.md` for the full walkthrough.

- `POST /api/v1/invitations` — create (`{ "email": "...", "roleIds": ["..."] }`), requires `adm:invitations:manage`. The response's `token` field is shown only this once.
- `GET /api/v1/invitations` — list pending invitations, requires `adm:invitations:manage`
- `POST /api/v1/invitations/{id}/cancel` — requires `adm:invitations:manage`
- `POST /api/v1/invitations/{token}/accept` — public, `{ "userId": "..." }`; no permission or authenticated principal required

Gen_ADM does not create the invited user's account or send the invitation
email — it returns the plaintext token in the create response and fires an
optional `InvitationEventPublisher.onCreated(...)` hook (no-op by default)
so the consuming application can deliver it however it wants.
```

- [ ] **Step 4: Update docs/integration-guide.md**

In `docs/integration-guide.md`, append after the existing "## 7. Impersonation Requests" section:
```markdown

## 8. Invitations

No mandatory bean to register. Optionally register an
`InvitationEventPublisher` bean to actually deliver the invitation (email,
Slack, etc.) — the default is a no-op, and `create`'s response already
carries the plaintext token regardless:

```java
@Component
public class MyInvitationEventPublisher implements InvitationEventPublisher {
    @Override
    public void onCreated(UUID tenantId, UUID invitationId, String email, String plaintextToken) {
        // send your own invitation email/notification using plaintextToken
    }
}
```

Expiry is configurable — `gen-adm.invitation-ttl-days` (default `7`):

```yaml
gen-adm:
  invitation-ttl-days: 14
```

State machine: `PENDING → ACCEPTED` (token-authenticated accept),
`PENDING → CANCELLED` (admin cancel), `PENDING → EXPIRED` once `expiresAt`
passes — checked lazily on every read/mutate, no scheduler. Unlike every
other Gen_ADM table, `invitations` has **no row-level security** — `accept`
looks up by token only, before any tenant is known, and a forced tenant
policy would block that lookup unconditionally. Tenant isolation for
create/list/cancel comes from explicit `tenant_id` predicates in those
queries instead.

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/v1/invitations` | `{email, roleIds}` | 400 if any `roleId` doesn't exist in the caller's tenant; 409 if an active invitation for that email already exists; response includes the plaintext `token`, shown only this once |
| GET | `/api/v1/invitations` | — | Lists `PENDING` invitations for the caller's tenant |
| POST | `/api/v1/invitations/{id}/cancel` | — | 409 if not `PENDING` |
| POST | `/api/v1/invitations/{token}/accept` | `{userId}` | Public — no permission or principal required. 404 for an unknown token, 409 if not `PENDING` (including already-expired). Assigns the invitation's roles to `userId` and marks `ACCEPTED`. |

Gen_ADM never creates the invited user's account — `userId` is supplied by
the caller, created by the consumer's own signup/auth flow before calling
`accept`. See
`docs/superpowers/specs/2026-07-24-gen-adm-invitations-design.md` for the
full design rationale, including why this table deliberately has no RLS.
```

- [ ] **Step 5: Run the full build**

Run: `./gradlew build`
Expected: PASS — full build including all modules and tests.

- [ ] **Step 6: Commit**

```bash
git add gen-adm-demo/src/main/resources/application.yaml scripts/smoke-test.sh README.md docs/integration-guide.md
git commit -m "docs: wire invitations into gen-adm-demo, smoke test, README, and integration guide"
```

---

## Final review

After all six tasks are complete and committed, do one whole-branch review pass (not per-task): re-read `InvitationServiceImpl`/`InvitationAcceptanceExecutor`/`UserRoleAssignmentServiceImpl` alongside `docs/superpowers/specs/2026-07-24-gen-adm-invitations-design.md` and confirm every guard (role-existence, duplicate-invite, lazy expiry, no-RLS token lookup, self-invocation-avoiding executor split, `assignRoleFromInvitation` additive-only) is actually exercised by at least one test, and that `README.md`/`docs/integration-guide.md` describe the shipped behavior exactly, not an earlier draft of it.
