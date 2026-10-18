# Gen_ADM Support Tickets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a tenant-scoped support-ticket request/lifecycle feature to Gen_ADM: any tenant member can file a ticket and check on their own; an admin (holding `adm:tickets:manage`) can list all tickets and move one through `OPEN → IN_PROGRESS → RESOLVED → CLOSED`.

**Architecture:** Additive to the existing `gen-adm-starter`/`gen-adm-demo` split — no changes to any prior phase's public surface, and no new pattern beyond what offboarding/impersonation already established (this feature needs no executor-split or no-RLS exception — unlike invitations, there is no token-only/no-principal lookup here). `SupportTicketEntity` is a single self-contained table with normal tenant-scoped RLS; `SupportTicketServiceImpl` is one ordinary `@Transactional`-per-method service, no lazy-expiry, no TTL, no separate executor bean.

**Tech Stack:** Same as prior phases — Java 21, Spring Boot 4.0.6, Gradle 9.4.1, PostgreSQL, Flyway, Spring Data JPA, Spring AOP, Lombok, JUnit 5, Mockito, AssertJ, Testcontainers. No new dependencies.

## Global Constraints

- Package root stays `com.example.admsvc` — new code lives alongside existing code, not in a separate module.
- `type`/`priority` enum values are exactly `TECHNICAL`/`BILLING`/`ACCOUNT`/`GENERAL`/`OTHER` and `LOW`/`MEDIUM`/`HIGH`/`URGENT` respectively — lifted verbatim from the CPMS source, do not rename or reorder.
- State machine: `OPEN → IN_PROGRESS` (`start`), `IN_PROGRESS → RESOLVED` (`resolve`), and `close` is valid from **any** non-`CLOSED` state (`OPEN`, `IN_PROGRESS`, or `RESOLVED`) — not only from `RESOLVED`. `start` requires exactly `OPEN` (409 otherwise); `resolve` requires exactly `IN_PROGRESS` (409 otherwise); `close` requires anything but `CLOSED` (409 otherwise).
- `create` and `listMine` require only an authenticated `GenAdmPrincipal` — **no** `permissionChecker.require(...)` call for either. `listAll`/`start`/`resolve`/`close` all require `adm:tickets:manage`.
- No lazy expiry, no TTL, no scheduler — this feature has no time-based state transitions at all, unlike impersonation/invitations.
- `support_tickets` gets normal `ENABLE`/`FORCE ROW LEVEL SECURITY` (unlike `invitations`, which deliberately has none) — there is no token-only lookup path here, so the standard tenant policy applies without conflict.
- Cross-tenant ticket lookups (`start`/`resolve`/`close`) surface as 404 (`GenAdmNotFoundException`), never 403 — same anti-enumeration convention as every prior feature.
- No separate audit-log table — `startedAt`, `resolvedAt`/`resolvedByUserId`, `closedAt`/`closedByUserId` on the ticket row itself are the audit trail.
- No support-desk functionality beyond the ticket record and its lifecycle (no comments, attachments, SLAs, assignee). An optional `TicketEventPublisher.onCreated(...)` port (no-op default) is the only extension point, mirroring `InvitationEventPublisher`.
- `SupportTicketController` lives at `/api/v1/support-tickets` (no `/adm/` path segment).
- Reuse `PermissionChecker`, `GenAdmPrincipal`, `GlobalExceptionHandler`, `TenantContextAspect` completely unmodified. No new exception subclasses — reuse `GenAdmConflictException`/`GenAdmNotFoundException`.
- One final whole-branch review at the end of all tasks, not one per task.
- Known sandbox limitation carried forward from prior phases: Testcontainers-based tests (Tasks 1 and 4) can only be verified by code review in this sandbox (confirmed Docker-detection limitation, not a code defect). Testcontainers' default DB user in this test setup is a superuser, so RLS itself is never actually enforced in this test suite (a pre-existing, accepted limitation affecting every prior feature's persistence tests too) — only repository-level `tenant_id = ?` filtering is exercised.

---

## Task 1: Support ticket persistence — enums, entity, repository, migrations

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/SupportTicketType.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/SupportTicketPriority.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/SupportTicketStatus.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/SupportTicketEntity.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/SupportTicketRepository.java`
- Create: `gen-adm-starter/src/main/resources/db/migration/genadm/V8__create_support_tickets_table.sql`
- Create: `gen-adm-starter/src/main/resources/db/migration/genadm/V9__enable_support_tickets_rls.sql`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/SupportTicketPersistenceIntegrationTest.java`

**Interfaces:**
- Consumes: the existing `classpath:db/migration/genadm` Flyway location. `V8`/`V9` are the next free migration versions — `V1`-`V7` already exist (`V7` — invitations — has no RLS companion, so `V8` is free regardless).
- Produces: `SupportTicketEntity` (`UUID id`, `UUID tenantId`, `UUID requestedByUserId`, `SupportTicketType type`, `String subject`, `String description`, `SupportTicketPriority priority`, `SupportTicketStatus status`, `Instant startedAt`, `Instant resolvedAt`, `UUID resolvedByUserId`, `Instant closedAt`, `UUID closedByUserId`, `Long version`, `Instant createdAt`, `Instant updatedAt`) — later tasks depend on these exact field names. `SupportTicketRepository.findByIdAndTenantId(UUID, UUID): Optional<SupportTicketEntity>`, `.findAllByTenantId(UUID): List<SupportTicketEntity>`, `.findAllByTenantIdAndRequestedByUserId(UUID, UUID): List<SupportTicketEntity>` — Task 2 calls these exact signatures.

- [ ] **Step 1: Create the enums**

`gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/SupportTicketType.java`:
```java
package com.example.admsvc.domain.enums;

public enum SupportTicketType {
    TECHNICAL,
    BILLING,
    ACCOUNT,
    GENERAL,
    OTHER
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/SupportTicketPriority.java`:
```java
package com.example.admsvc.domain.enums;

public enum SupportTicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/SupportTicketStatus.java`:
```java
package com.example.admsvc.domain.enums;

public enum SupportTicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}
```

- [ ] **Step 2: Write the failing persistence integration test first**

`gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/SupportTicketPersistenceIntegrationTest.java`:
```java
package com.example.admsvc.infrastructure.persistence;

import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketStatus;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;
import com.example.admsvc.infrastructure.persistence.repository.SupportTicketRepository;
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
@SpringBootTest(classes = SupportTicketPersistenceIntegrationTest.TestApp.class)
class SupportTicketPersistenceIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private SupportTicketRepository repository;

    private SupportTicketEntity newTicket(UUID tenantId, UUID requestedBy) {
        return SupportTicketEntity.builder()
                .tenantId(tenantId)
                .requestedByUserId(requestedBy)
                .type(SupportTicketType.TECHNICAL)
                .subject("Unable to access dashboard")
                .description("Users are receiving 500 errors while opening dashboard pages.")
                .priority(SupportTicketPriority.HIGH)
                .build();
    }

    @Test
    void savesAndFindsByIdAndTenantDefaultingToOpen() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        SupportTicketEntity saved = repository.saveAndFlush(newTicket(tenantId, requestedBy));

        Optional<SupportTicketEntity> found = repository.findByIdAndTenantId(saved.getId(), tenantId);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(SupportTicketStatus.OPEN);
        assertThat(found.get().getResolvedByUserId()).isNull();
        assertThat(found.get().getClosedByUserId()).isNull();
    }

    @Test
    void aTicketIsInvisibleWhenLookedUpUnderTheWrongTenant() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity saved = repository.saveAndFlush(newTicket(tenantId, UUID.randomUUID()));

        assertThat(repository.findByIdAndTenantId(saved.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void findsAllTicketsForATenantRegardlessOfRequester() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity a = repository.saveAndFlush(newTicket(tenantId, UUID.randomUUID()));
        SupportTicketEntity b = repository.saveAndFlush(newTicket(tenantId, UUID.randomUUID()));
        repository.saveAndFlush(newTicket(UUID.randomUUID(), UUID.randomUUID())); // different tenant

        List<SupportTicketEntity> all = repository.findAllByTenantId(tenantId);
        assertThat(all).extracting(SupportTicketEntity::getId).containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @Test
    void findsOnlyTheRequestersOwnTicketsForListMine() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        SupportTicketEntity mine = repository.saveAndFlush(newTicket(tenantId, requestedBy));
        repository.saveAndFlush(newTicket(tenantId, UUID.randomUUID())); // someone else's, same tenant

        List<SupportTicketEntity> mineOnly = repository.findAllByTenantIdAndRequestedByUserId(tenantId, requestedBy);
        assertThat(mineOnly).extracting(SupportTicketEntity::getId).containsExactly(mine.getId());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.persistence.SupportTicketPersistenceIntegrationTest"`
Expected: FAIL — compile error, `SupportTicketEntity`/`SupportTicketRepository` do not exist yet.

- [ ] **Step 4: Create the entity**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/SupportTicketEntity.java`:
```java
package com.example.admsvc.infrastructure.persistence.entity;

import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketStatus;
import com.example.admsvc.domain.enums.SupportTicketType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_tickets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private SupportTicketType type;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "description", nullable = false, length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private SupportTicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupportTicketStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by_user_id")
    private UUID closedByUserId;

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
            status = SupportTicketStatus.OPEN;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
```

- [ ] **Step 5: Create the repository**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/SupportTicketRepository.java`:
```java
package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicketEntity, UUID> {

    Optional<SupportTicketEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SupportTicketEntity> findAllByTenantId(UUID tenantId);

    List<SupportTicketEntity> findAllByTenantIdAndRequestedByUserId(UUID tenantId, UUID requestedByUserId);
}
```

- [ ] **Step 6: Create the migrations**

`gen-adm-starter/src/main/resources/db/migration/genadm/V8__create_support_tickets_table.sql`:
```sql
CREATE TABLE support_tickets (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    requested_by_user_id  UUID NOT NULL,
    type                  VARCHAR(20) NOT NULL,
    subject               VARCHAR(255) NOT NULL,
    description           VARCHAR(5000) NOT NULL,
    priority              VARCHAR(20) NOT NULL,
    status                VARCHAR(20) NOT NULL,
    started_at            TIMESTAMPTZ,
    resolved_at           TIMESTAMPTZ,
    resolved_by_user_id   UUID,
    closed_at             TIMESTAMPTZ,
    closed_by_user_id     UUID,
    version               BIGINT,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_support_tickets_tenant_status
    ON support_tickets(tenant_id, status);

CREATE INDEX idx_support_tickets_tenant_requested_by
    ON support_tickets(tenant_id, requested_by_user_id);
```

`gen-adm-starter/src/main/resources/db/migration/genadm/V9__enable_support_tickets_rls.sql`:
```sql
ALTER TABLE support_tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE support_tickets FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_support_tickets ON support_tickets
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.persistence.SupportTicketPersistenceIntegrationTest"`
Expected: PASS (4 tests) — subject to the known Testcontainers/Docker sandbox limitation noted in Global Constraints; verify by code review if Docker isn't available here.

- [ ] **Step 8: Commit**

```bash
git add gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/SupportTicketType.java \
        gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/SupportTicketPriority.java \
        gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/SupportTicketStatus.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/SupportTicketEntity.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/SupportTicketRepository.java \
        gen-adm-starter/src/main/resources/db/migration/genadm/V8__create_support_tickets_table.sql \
        gen-adm-starter/src/main/resources/db/migration/genadm/V9__enable_support_tickets_rls.sql \
        gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/SupportTicketPersistenceIntegrationTest.java
git commit -m "feat: add support ticket persistence (entity, repository, RLS migrations)"
```

---

## Task 2: Event port + SupportTicketService (create/listMine/listAll/start/resolve/close)

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/port/TicketEventPublisher.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/event/NoOpTicketEventPublisher.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/config/TicketConfig.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/service/SupportTicketService.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/SupportTicketServiceImpl.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/SupportTicketServiceImplTest.java`

**Interfaces:**
- Consumes: `SupportTicketRepository`/`SupportTicketEntity`/`SupportTicketType`/`SupportTicketPriority`/`SupportTicketStatus` from Task 1 (exact signatures above). `GenAdmConflictException`, `GenAdmNotFoundException` from `com.example.admsvc.common.exception` (pre-existing).
- Produces: `SupportTicketService` with
  ```java
  SupportTicketEntity create(UUID tenantId, UUID requestedByUserId, SupportTicketType type,
          String subject, String description, SupportTicketPriority priority);
  List<SupportTicketEntity> listMine(UUID tenantId, UUID requestedByUserId);
  List<SupportTicketEntity> listAll(UUID tenantId);
  SupportTicketEntity start(UUID tenantId, UUID ticketId);
  SupportTicketEntity resolve(UUID tenantId, UUID ticketId, UUID resolvedByUserId);
  SupportTicketEntity close(UUID tenantId, UUID ticketId, UUID closedByUserId);
  ```
  Task 3's controller calls these exact signatures. `TicketEventPublisher.onCreated(UUID, UUID, SupportTicketType, SupportTicketPriority)` — optional override point, `NoOpTicketEventPublisher` is the default bean.

- [ ] **Step 1: Create the event publisher port and no-op default**

`gen-adm-starter/src/main/java/com/example/admsvc/domain/port/TicketEventPublisher.java`:
```java
package com.example.admsvc.domain.port;

import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketType;

import java.util.UUID;

/**
 * Optional synchronous hook for a consumer to react to a new support ticket
 * (e.g. forward it into a real support tool — Zendesk, Jira, etc.) without
 * Gen_ADM owning that integration itself. Not an outbox — no persistence,
 * no retry of the notification itself. Defaults to a no-op
 * ({@link com.example.admsvc.infrastructure.event.NoOpTicketEventPublisher}).
 */
public interface TicketEventPublisher {

    void onCreated(UUID tenantId, UUID ticketId, SupportTicketType type, SupportTicketPriority priority);
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/event/NoOpTicketEventPublisher.java`:
```java
package com.example.admsvc.infrastructure.event;

import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.domain.port.TicketEventPublisher;

import java.util.UUID;

public class NoOpTicketEventPublisher implements TicketEventPublisher {

    @Override
    public void onCreated(UUID tenantId, UUID ticketId, SupportTicketType type, SupportTicketPriority priority) {
    }
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/config/TicketConfig.java`:
```java
package com.example.admsvc.config;

import com.example.admsvc.domain.port.TicketEventPublisher;
import com.example.admsvc.infrastructure.event.NoOpTicketEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TicketConfig {

    @Bean
    @ConditionalOnMissingBean(TicketEventPublisher.class)
    public TicketEventPublisher ticketEventPublisher() {
        return new NoOpTicketEventPublisher();
    }
}
```

- [ ] **Step 2: Create the service interface**

`gen-adm-starter/src/main/java/com/example/admsvc/application/service/SupportTicketService.java`:
```java
package com.example.admsvc.application.service;

import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;

import java.util.List;
import java.util.UUID;

public interface SupportTicketService {

    SupportTicketEntity create(UUID tenantId, UUID requestedByUserId, SupportTicketType type,
                                String subject, String description, SupportTicketPriority priority);

    List<SupportTicketEntity> listMine(UUID tenantId, UUID requestedByUserId);

    List<SupportTicketEntity> listAll(UUID tenantId);

    SupportTicketEntity start(UUID tenantId, UUID ticketId);

    SupportTicketEntity resolve(UUID tenantId, UUID ticketId, UUID resolvedByUserId);

    SupportTicketEntity close(UUID tenantId, UUID ticketId, UUID closedByUserId);
}
```

- [ ] **Step 3: Write the failing unit test first**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/SupportTicketServiceImplTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketStatus;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.domain.port.TicketEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;
import com.example.admsvc.infrastructure.persistence.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceImplTest {

    @Mock
    private SupportTicketRepository repository;

    @Mock
    private TicketEventPublisher eventPublisher;

    private SupportTicketServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SupportTicketServiceImpl(repository, eventPublisher);
        lenient().when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            SupportTicketEntity ticket = inv.getArgument(0);
            if (ticket.getId() == null) {
                ticket.setId(UUID.randomUUID());
            }
            if (ticket.getStatus() == null) {
                ticket.setStatus(SupportTicketStatus.OPEN);
            }
            return ticket;
        });
    }

    private SupportTicketEntity ticket(UUID tenantId, SupportTicketStatus status) {
        return SupportTicketEntity.builder()
                .id(UUID.randomUUID()).tenantId(tenantId)
                .requestedByUserId(UUID.randomUUID())
                .type(SupportTicketType.TECHNICAL)
                .subject("s").description("d")
                .priority(SupportTicketPriority.HIGH)
                .status(status)
                .build();
    }

    @Test
    void createPersistsAnOpenTicketAndFiresOnCreated() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();

        SupportTicketEntity ticket = service.create(tenantId, requestedBy, SupportTicketType.BILLING,
                "Invoice question", "Why was I charged twice?", SupportTicketPriority.LOW);

        assertThat(ticket.getStatus()).isEqualTo(SupportTicketStatus.OPEN);
        assertThat(ticket.getTenantId()).isEqualTo(tenantId);
        assertThat(ticket.getRequestedByUserId()).isEqualTo(requestedBy);
        verify(eventPublisher).onCreated(eq(tenantId), any(), eq(SupportTicketType.BILLING), eq(SupportTicketPriority.LOW));
    }

    @Test
    void listMineDelegatesToTheRequesterScopedQuery() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        SupportTicketEntity mine = ticket(tenantId, SupportTicketStatus.OPEN);
        when(repository.findAllByTenantIdAndRequestedByUserId(tenantId, requestedBy)).thenReturn(List.of(mine));

        List<SupportTicketEntity> result = service.listMine(tenantId, requestedBy);

        assertThat(result).containsExactly(mine);
    }

    @Test
    void listAllDelegatesToTheTenantScopedQuery() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity a = ticket(tenantId, SupportTicketStatus.OPEN);
        when(repository.findAllByTenantId(tenantId)).thenReturn(List.of(a));

        assertThat(service.listAll(tenantId)).containsExactly(a);
    }

    @Test
    void startFlipsAnOpenTicketToInProgress() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.OPEN);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        SupportTicketEntity started = service.start(tenantId, ticket.getId());

        assertThat(started.getStatus()).isEqualTo(SupportTicketStatus.IN_PROGRESS);
        assertThat(started.getStartedAt()).isNotNull();
    }

    @Test
    void startThrowsConflictWhenNotOpen() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.IN_PROGRESS);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.start(tenantId, ticket.getId()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void resolveFlipsAnInProgressTicketToResolved() {
        UUID tenantId = UUID.randomUUID();
        UUID resolvedBy = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.IN_PROGRESS);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        SupportTicketEntity resolved = service.resolve(tenantId, ticket.getId(), resolvedBy);

        assertThat(resolved.getStatus()).isEqualTo(SupportTicketStatus.RESOLVED);
        assertThat(resolved.getResolvedByUserId()).isEqualTo(resolvedBy);
        assertThat(resolved.getResolvedAt()).isNotNull();
    }

    @Test
    void resolveThrowsConflictWhenNotInProgress() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.OPEN);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.resolve(tenantId, ticket.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void closeIsAllowedFromOpenWithoutGoingThroughInProgressOrResolved() {
        UUID tenantId = UUID.randomUUID();
        UUID closedBy = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.OPEN);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        SupportTicketEntity closed = service.close(tenantId, ticket.getId(), closedBy);

        assertThat(closed.getStatus()).isEqualTo(SupportTicketStatus.CLOSED);
        assertThat(closed.getClosedByUserId()).isEqualTo(closedBy);
    }

    @Test
    void closeIsAllowedFromInProgress() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.IN_PROGRESS);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        assertThat(service.close(tenantId, ticket.getId(), UUID.randomUUID()).getStatus())
                .isEqualTo(SupportTicketStatus.CLOSED);
    }

    @Test
    void closeIsAllowedFromResolved() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.RESOLVED);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        assertThat(service.close(tenantId, ticket.getId(), UUID.randomUUID()).getStatus())
                .isEqualTo(SupportTicketStatus.CLOSED);
    }

    @Test
    void closeThrowsConflictWhenAlreadyClosed() {
        UUID tenantId = UUID.randomUUID();
        SupportTicketEntity ticket = ticket(tenantId, SupportTicketStatus.CLOSED);
        when(repository.findByIdAndTenantId(ticket.getId(), tenantId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.close(tenantId, ticket.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void throwsNotFoundForACrossTenantLookup() {
        UUID ticketId = UUID.randomUUID();
        when(repository.findByIdAndTenantId(eq(ticketId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(UUID.randomUUID(), ticketId))
                .isInstanceOf(GenAdmNotFoundException.class);
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.SupportTicketServiceImplTest"`
Expected: FAIL — compile error, `SupportTicketServiceImpl` does not exist yet.

- [ ] **Step 5: Write the implementation**

`gen-adm-starter/src/main/java/com/example/admsvc/application/impl/SupportTicketServiceImpl.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.SupportTicketService;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketStatus;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.domain.port.TicketEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;
import com.example.admsvc.infrastructure.persistence.repository.SupportTicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SupportTicketServiceImpl implements SupportTicketService {

    private final SupportTicketRepository repository;
    private final TicketEventPublisher eventPublisher;

    public SupportTicketServiceImpl(SupportTicketRepository repository, TicketEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public SupportTicketEntity create(UUID tenantId, UUID requestedByUserId, SupportTicketType type,
                                       String subject, String description, SupportTicketPriority priority) {
        SupportTicketEntity saved = repository.saveAndFlush(SupportTicketEntity.builder()
                .tenantId(tenantId)
                .requestedByUserId(requestedByUserId)
                .type(type)
                .subject(subject)
                .description(description)
                .priority(priority)
                .build());
        eventPublisher.onCreated(tenantId, saved.getId(), type, priority);
        return saved;
    }

    @Override
    @Transactional
    public List<SupportTicketEntity> listMine(UUID tenantId, UUID requestedByUserId) {
        return repository.findAllByTenantIdAndRequestedByUserId(tenantId, requestedByUserId);
    }

    @Override
    @Transactional
    public List<SupportTicketEntity> listAll(UUID tenantId) {
        return repository.findAllByTenantId(tenantId);
    }

    @Override
    @Transactional
    public SupportTicketEntity start(UUID tenantId, UUID ticketId) {
        SupportTicketEntity ticket = findOrThrow(tenantId, ticketId);
        if (ticket.getStatus() != SupportTicketStatus.OPEN) {
            throw new GenAdmConflictException("Ticket is not open: " + ticketId);
        }
        ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
        ticket.setStartedAt(Instant.now());
        return repository.saveAndFlush(ticket);
    }

    @Override
    @Transactional
    public SupportTicketEntity resolve(UUID tenantId, UUID ticketId, UUID resolvedByUserId) {
        SupportTicketEntity ticket = findOrThrow(tenantId, ticketId);
        if (ticket.getStatus() != SupportTicketStatus.IN_PROGRESS) {
            throw new GenAdmConflictException("Ticket is not in progress: " + ticketId);
        }
        ticket.setStatus(SupportTicketStatus.RESOLVED);
        ticket.setResolvedAt(Instant.now());
        ticket.setResolvedByUserId(resolvedByUserId);
        return repository.saveAndFlush(ticket);
    }

    @Override
    @Transactional
    public SupportTicketEntity close(UUID tenantId, UUID ticketId, UUID closedByUserId) {
        SupportTicketEntity ticket = findOrThrow(tenantId, ticketId);
        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            throw new GenAdmConflictException("Ticket is already closed: " + ticketId);
        }
        ticket.setStatus(SupportTicketStatus.CLOSED);
        ticket.setClosedAt(Instant.now());
        ticket.setClosedByUserId(closedByUserId);
        return repository.saveAndFlush(ticket);
    }

    private SupportTicketEntity findOrThrow(UUID tenantId, UUID ticketId) {
        return repository.findByIdAndTenantId(ticketId, tenantId)
                .orElseThrow(() -> new GenAdmNotFoundException("Support ticket not found: " + ticketId));
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.SupportTicketServiceImplTest"`
Expected: PASS (12 tests) — plain Mockito, no Spring context, no database; expected to actually run here.

- [ ] **Step 7: Commit**

```bash
git add gen-adm-starter/src/main/java/com/example/admsvc/domain/port/TicketEventPublisher.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/event/NoOpTicketEventPublisher.java \
        gen-adm-starter/src/main/java/com/example/admsvc/config/TicketConfig.java \
        gen-adm-starter/src/main/java/com/example/admsvc/application/service/SupportTicketService.java \
        gen-adm-starter/src/main/java/com/example/admsvc/application/impl/SupportTicketServiceImpl.java \
        gen-adm-starter/src/test/java/com/example/admsvc/application/impl/SupportTicketServiceImplTest.java
git commit -m "feat: add SupportTicketService with OPEN/IN_PROGRESS/RESOLVED/CLOSED lifecycle"
```

---

## Task 3: REST layer — SupportTicketController

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/CreateSupportTicketRequest.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/SupportTicketResponse.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/controller/SupportTicketController.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/api/controller/SupportTicketControllerTest.java`

**Interfaces:**
- Consumes: `SupportTicketService` (Task 2, exact signatures above), `PermissionChecker.require(GenAdmPrincipal, String): void` (pre-existing), `GenAdmPrincipal.tenantId()`/`.userId()` (pre-existing), `GlobalExceptionHandler` (pre-existing, unmodified).
- Produces: REST routes under `/api/v1/support-tickets` — `POST /`, `GET /mine`, `GET /`, `POST /{id}/start`, `POST /{id}/resolve`, `POST /{id}/close`. No other task depends on `SupportTicketResponse` — it is the terminal DTO.

- [ ] **Step 1: Create the request and response DTOs**

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/CreateSupportTicketRequest.java`:
```java
package com.example.admsvc.api.dto.request;

import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
        @NotNull SupportTicketType type,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank @Size(max = 5000) String description,
        @NotNull SupportTicketPriority priority) {
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/SupportTicketResponse.java`:
```java
package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;

import java.time.Instant;
import java.util.UUID;

public record SupportTicketResponse(
        UUID id,
        UUID tenantId,
        UUID requestedByUserId,
        String type,
        String subject,
        String description,
        String priority,
        String status,
        Instant startedAt,
        Instant resolvedAt,
        UUID resolvedByUserId,
        Instant closedAt,
        UUID closedByUserId,
        Instant createdAt) {

    public static SupportTicketResponse from(SupportTicketEntity ticket) {
        return new SupportTicketResponse(
                ticket.getId(),
                ticket.getTenantId(),
                ticket.getRequestedByUserId(),
                ticket.getType().name(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getPriority().name(),
                ticket.getStatus().name(),
                ticket.getStartedAt(),
                ticket.getResolvedAt(),
                ticket.getResolvedByUserId(),
                ticket.getClosedAt(),
                ticket.getClosedByUserId(),
                ticket.getCreatedAt());
    }
}
```

- [ ] **Step 2: Write the failing controller test first**

`gen-adm-starter/src/test/java/com/example/admsvc/api/controller/SupportTicketControllerTest.java`:
```java
package com.example.admsvc.api.controller;

import com.example.admsvc.api.advice.GlobalExceptionHandler;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.application.service.SupportTicketService;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketStatus;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SupportTicketControllerTest {

    private static final String MANAGE_PERM = "adm:tickets:manage";

    private SupportTicketService supportTicketService;
    private PermissionChecker permissionChecker;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private final TestPrincipal principal = new TestPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        supportTicketService = mock(SupportTicketService.class);
        permissionChecker = mock(PermissionChecker.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SupportTicketController(supportTicketService, permissionChecker))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null));
    }

    private SupportTicketEntity ticket(SupportTicketStatus status) {
        return SupportTicketEntity.builder()
                .id(UUID.randomUUID()).tenantId(principal.tenantId())
                .requestedByUserId(principal.userId())
                .type(SupportTicketType.TECHNICAL)
                .subject("s").description("d")
                .priority(SupportTicketPriority.HIGH)
                .status(status)
                .build();
    }

    @Test
    void createRequiresOnlyAnAuthenticatedPrincipalNoPermissionCheck() throws Exception {
        SupportTicketEntity created = ticket(SupportTicketStatus.OPEN);
        when(supportTicketService.create(eq(principal.tenantId()), eq(principal.userId()),
                eq(SupportTicketType.TECHNICAL), eq("Can't log in"), eq("Getting a 500 error"),
                eq(SupportTicketPriority.URGENT))).thenReturn(created);

        mockMvc.perform(post("/api/v1/support-tickets")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("type", "TECHNICAL");
                            put("subject", "Can't log in");
                            put("description", "Getting a 500 error");
                            put("priority", "URGENT");
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void listMineRequiresOnlyAnAuthenticatedPrincipalNoPermissionCheck() throws Exception {
        when(supportTicketService.listMine(principal.tenantId(), principal.userId()))
                .thenReturn(List.of(ticket(SupportTicketStatus.OPEN)));

        mockMvc.perform(get("/api/v1/support-tickets/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"));
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void listAllRequiresManagePermission() throws Exception {
        doThrow(new GenAdmForbiddenException("Missing required permission: " + MANAGE_PERM))
                .when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));

        mockMvc.perform(get("/api/v1/support-tickets"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void startReturns404ForACrossTenantTicket() throws Exception {
        UUID ticketId = UUID.randomUUID();
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        when(supportTicketService.start(principal.tenantId(), ticketId))
                .thenThrow(new GenAdmNotFoundException("Support ticket not found: " + ticketId));

        mockMvc.perform(post("/api/v1/support-tickets/" + ticketId + "/start"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void resolveRequiresManagePermissionAndPassesResolvedBy() throws Exception {
        UUID ticketId = UUID.randomUUID();
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        when(supportTicketService.resolve(principal.tenantId(), ticketId, principal.userId()))
                .thenReturn(ticket(SupportTicketStatus.RESOLVED));

        mockMvc.perform(post("/api/v1/support-tickets/" + ticketId + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        verify(supportTicketService).resolve(principal.tenantId(), ticketId, principal.userId());
    }

    @Test
    void closeRequiresManagePermissionAndPassesClosedBy() throws Exception {
        UUID ticketId = UUID.randomUUID();
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        when(supportTicketService.close(principal.tenantId(), ticketId, principal.userId()))
                .thenReturn(ticket(SupportTicketStatus.CLOSED));

        mockMvc.perform(post("/api/v1/support-tickets/" + ticketId + "/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        verify(supportTicketService).close(principal.tenantId(), ticketId, principal.userId());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.api.controller.SupportTicketControllerTest"`
Expected: FAIL — compile error, `SupportTicketController` does not exist yet.

- [ ] **Step 4: Write the controller**

`gen-adm-starter/src/main/java/com/example/admsvc/api/controller/SupportTicketController.java`:
```java
package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.request.CreateSupportTicketRequest;
import com.example.admsvc.api.dto.response.SupportTicketResponse;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.application.service.SupportTicketService;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/support-tickets")
public class SupportTicketController {

    private static final String MANAGE_TICKETS = "adm:tickets:manage";

    private final SupportTicketService supportTicketService;
    private final PermissionChecker permissionChecker;

    public SupportTicketController(SupportTicketService supportTicketService, PermissionChecker permissionChecker) {
        this.supportTicketService = supportTicketService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public SupportTicketResponse create(@AuthenticationPrincipal GenAdmPrincipal principal,
                                         @Valid @RequestBody CreateSupportTicketRequest request) {
        return SupportTicketResponse.from(supportTicketService.create(
                principal.tenantId(), principal.userId(), request.type(),
                request.subject(), request.description(), request.priority()));
    }

    @GetMapping("/mine")
    public List<SupportTicketResponse> listMine(@AuthenticationPrincipal GenAdmPrincipal principal) {
        return supportTicketService.listMine(principal.tenantId(), principal.userId()).stream()
                .map(SupportTicketResponse::from)
                .toList();
    }

    @GetMapping
    public List<SupportTicketResponse> listAll(@AuthenticationPrincipal GenAdmPrincipal principal) {
        permissionChecker.require(principal, MANAGE_TICKETS);
        return supportTicketService.listAll(principal.tenantId()).stream()
                .map(SupportTicketResponse::from)
                .toList();
    }

    @PostMapping("/{id}/start")
    public SupportTicketResponse start(@AuthenticationPrincipal GenAdmPrincipal principal,
                                        @PathVariable UUID id) {
        permissionChecker.require(principal, MANAGE_TICKETS);
        return SupportTicketResponse.from(supportTicketService.start(principal.tenantId(), id));
    }

    @PostMapping("/{id}/resolve")
    public SupportTicketResponse resolve(@AuthenticationPrincipal GenAdmPrincipal principal,
                                          @PathVariable UUID id) {
        permissionChecker.require(principal, MANAGE_TICKETS);
        return SupportTicketResponse.from(
                supportTicketService.resolve(principal.tenantId(), id, principal.userId()));
    }

    @PostMapping("/{id}/close")
    public SupportTicketResponse close(@AuthenticationPrincipal GenAdmPrincipal principal,
                                        @PathVariable UUID id) {
        permissionChecker.require(principal, MANAGE_TICKETS);
        return SupportTicketResponse.from(
                supportTicketService.close(principal.tenantId(), id, principal.userId()));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.api.controller.SupportTicketControllerTest"`
Expected: PASS (6 tests) — MockMvc standalone setup, no Spring context, no database.

- [ ] **Step 6: Commit**

```bash
git add gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/CreateSupportTicketRequest.java \
        gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/SupportTicketResponse.java \
        gen-adm-starter/src/main/java/com/example/admsvc/api/controller/SupportTicketController.java \
        gen-adm-starter/src/test/java/com/example/admsvc/api/controller/SupportTicketControllerTest.java
git commit -m "feat: add support-tickets REST controller (create/listMine/listAll/start/resolve/close)"
```

---

## Task 4: Full-flow integration test

**Files:**
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/SupportTicketIntegrationTest.java`

**Interfaces:**
- Consumes: `SupportTicketServiceImpl` (Task 2) wired through the real Spring context (`GenAdmAutoConfiguration`, `TenantContextAspect`, Flyway migrations from Task 1), `TicketEventPublisher` (Task 2) overridden by a recording test bean.
- Produces: nothing consumed by later tasks — verification-only.

- [ ] **Step 1: Write the integration test**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/SupportTicketIntegrationTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketStatus;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.domain.port.TicketEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;
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
@SpringBootTest(classes = SupportTicketIntegrationTest.TestApp.class)
class SupportTicketIntegrationTest {

    @SpringBootApplication
    static class TestApp {

        @Bean
        RecordingTicketEventPublisher recordingTicketEventPublisher() {
            return new RecordingTicketEventPublisher();
        }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static class RecordingTicketEventPublisher implements TicketEventPublisher {
        final List<String> events = new ArrayList<>();

        @Override
        public void onCreated(UUID tenantId, UUID ticketId, SupportTicketType type, SupportTicketPriority priority) {
            events.add("CREATED:" + type);
        }
    }

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    @Autowired
    private SupportTicketServiceImpl supportTicketService;

    @Autowired
    private RecordingTicketEventPublisher eventPublisher;

    @BeforeEach
    void resetRecordedEvents() {
        eventPublisher.events.clear();
    }

    private void authenticateAs(UUID tenantId, UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new TestPrincipal(tenantId, userId), null));
    }

    @Test
    void createStartResolveCloseFlow() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        SupportTicketEntity created = supportTicketService.create(tenantId, requestedBy,
                SupportTicketType.TECHNICAL, "Dashboard down", "500 errors on load", SupportTicketPriority.HIGH);
        assertThat(created.getStatus()).isEqualTo(SupportTicketStatus.OPEN);
        assertThat(eventPublisher.events).containsExactly("CREATED:TECHNICAL");

        SupportTicketEntity started = supportTicketService.start(tenantId, created.getId());
        assertThat(started.getStatus()).isEqualTo(SupportTicketStatus.IN_PROGRESS);

        SupportTicketEntity resolved = supportTicketService.resolve(tenantId, created.getId(), admin);
        assertThat(resolved.getStatus()).isEqualTo(SupportTicketStatus.RESOLVED);
        assertThat(resolved.getResolvedByUserId()).isEqualTo(admin);

        SupportTicketEntity closed = supportTicketService.close(tenantId, created.getId(), admin);
        assertThat(closed.getStatus()).isEqualTo(SupportTicketStatus.CLOSED);
        assertThat(closed.getClosedByUserId()).isEqualTo(admin);
    }

    @Test
    void closeIsAllowedDirectlyFromOpenSkippingInProgressAndResolved() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        SupportTicketEntity created = supportTicketService.create(tenantId, requestedBy,
                SupportTicketType.GENERAL, "Duplicate ticket", "Filed by mistake", SupportTicketPriority.LOW);

        SupportTicketEntity closed = supportTicketService.close(tenantId, created.getId(), admin);

        assertThat(closed.getStatus()).isEqualTo(SupportTicketStatus.CLOSED);
    }

    @Test
    void listMineOnlyReturnsTheRequestersOwnTickets() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);
        SupportTicketEntity mine = supportTicketService.create(tenantId, requestedBy,
                SupportTicketType.ACCOUNT, "s1", "d1", SupportTicketPriority.MEDIUM);

        authenticateAs(tenantId, UUID.randomUUID());
        supportTicketService.create(tenantId, UUID.randomUUID(),
                SupportTicketType.ACCOUNT, "s2", "d2", SupportTicketPriority.MEDIUM);

        authenticateAs(tenantId, requestedBy);
        List<SupportTicketEntity> mineList = supportTicketService.listMine(tenantId, requestedBy);

        assertThat(mineList).extracting(SupportTicketEntity::getId).containsExactly(mine.getId());
    }

    @Test
    void crossTenantLookupIs404() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);
        SupportTicketEntity created = supportTicketService.create(tenantId, requestedBy,
                SupportTicketType.OTHER, "s", "d", SupportTicketPriority.URGENT);

        authenticateAs(UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> supportTicketService.start(UUID.randomUUID(), created.getId()))
                .isInstanceOf(GenAdmNotFoundException.class);
    }

    @Test
    void resolvingAnOpenTicketWithoutStartingItIsAConflict() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);
        SupportTicketEntity created = supportTicketService.create(tenantId, requestedBy,
                SupportTicketType.TECHNICAL, "s", "d", SupportTicketPriority.HIGH);

        assertThatThrownBy(() -> supportTicketService.resolve(tenantId, created.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.SupportTicketIntegrationTest"`
Expected: PASS (5 tests) — subject to the known Testcontainers/Docker sandbox limitation noted in Global Constraints; verify by code review if Docker isn't available here.

- [ ] **Step 3: Run the full module test suite**

Run: `./gradlew :gen-adm-starter:test`
Expected: PASS — all prior-phase and support-ticket tests green together, no cross-test interference.

- [ ] **Step 4: Commit**

```bash
git add gen-adm-starter/src/test/java/com/example/admsvc/application/impl/SupportTicketIntegrationTest.java
git commit -m "test: add full-flow support ticket integration test (lifecycle, listMine, cross-tenant 404)"
```

---

## Task 5: Demo wiring, smoke test, docs

**Files:**
- Modify: `gen-adm-demo/src/main/resources/application.yaml`
- Modify: `scripts/smoke-test.sh`
- Modify: `README.md`
- Modify: `docs/integration-guide.md`

**Interfaces:**
- Consumes: `POST/GET /api/v1/support-tickets`, `GET /api/v1/support-tickets/mine`, `POST /api/v1/support-tickets/{id}/{start,resolve,close}` (Task 3), `adm:tickets:manage` permission code (new in this task), the existing `PUT /api/v1/roles/{id}/permissions` route (Phase 1).
- Produces: nothing — terminal wiring/documentation task.

- [ ] **Step 1: Add the permission code to the demo's catalog**

In `gen-adm-demo/src/main/resources/application.yaml`, extend the `gen-adm.permissions` list (currently ending at `adm:invitations:manage`) by appending:
```yaml
    - code: adm:tickets:manage
      description: List all tenant support tickets and manage their lifecycle
```

- [ ] **Step 2: Extend the smoke test**

In `scripts/smoke-test.sh`, append at the end of the file, after the existing "List invitations" block and before "Smoke test complete.":
```bash
echo "== File a support ticket as the owner (no special permission needed) =="
TICKET_RESPONSE=$(curl -sf -X POST "$BASE_URL/api/v1/support-tickets" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d '{"type":"TECHNICAL","subject":"Cannot access dashboard","description":"Getting a 500 error.","priority":"HIGH"}')
echo "$TICKET_RESPONSE"
TICKET_ID=$(echo "$TICKET_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

echo "== List my own tickets =="
curl -sf "$BASE_URL/api/v1/support-tickets/mine" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID"
echo

echo "== Grant adm:tickets:manage to the viewer role =="
curl -sf -X PUT "$BASE_URL/api/v1/roles/$ROLE_ID/permissions" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d '{"permissionCodes":["adm:tickets:manage"]}'
echo

echo "== List all tenant tickets as the second user (now holding adm:tickets:manage) =="
curl -sf "$BASE_URL/api/v1/support-tickets" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo

echo "== Start, resolve, and close the ticket as the second user =="
curl -sf -X POST "$BASE_URL/api/v1/support-tickets/$TICKET_ID/start" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo
curl -sf -X POST "$BASE_URL/api/v1/support-tickets/$TICKET_ID/resolve" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo
curl -sf -X POST "$BASE_URL/api/v1/support-tickets/$TICKET_ID/close" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo
```
Keep the final `echo "Smoke test complete."` as the last line of the file.

- [ ] **Step 3: Update README.md**

In `README.md`, append after the existing "Invitations" section:
```markdown

## Support Tickets

Tenant-scoped support ticket requests with a real lifecycle. Any
authenticated tenant member can file a ticket and check on their own; an
admin can see every ticket in the tenant and move one through its
lifecycle. See `docs/integration-guide.md` for the full walkthrough.

- `POST /api/v1/support-tickets` — file a ticket (`{ "type": "...", "subject": "...", "description": "...", "priority": "..." }`), any authenticated principal, no permission required
- `GET /api/v1/support-tickets/mine` — list your own tickets, any authenticated principal, no permission required
- `GET /api/v1/support-tickets` — list every ticket in the tenant, requires `adm:tickets:manage`
- `POST /api/v1/support-tickets/{id}/start` / `/resolve` / `/close` — move a ticket through `OPEN → IN_PROGRESS → RESOLVED → CLOSED`; requires `adm:tickets:manage`. `close` works from any non-`CLOSED` state, not only from `RESOLVED`.

Gen_ADM only tracks the ticket record and its lifecycle — it does not
provide a real support desk (no comments, attachments, or agent
assignment). Register an `TicketEventPublisher` bean to forward new
tickets into whatever support tool you actually use; the default is a
no-op.
```

- [ ] **Step 4: Update docs/integration-guide.md**

In `docs/integration-guide.md`, append after the existing "## 8. Invitations" section:
```markdown

## 9. Support Tickets

No mandatory bean to register. Optionally register a `TicketEventPublisher`
bean to forward new tickets into a real support tool — the default is a
no-op:

```java
@Component
public class MyTicketEventPublisher implements TicketEventPublisher {
    @Override
    public void onCreated(UUID tenantId, UUID ticketId, SupportTicketType type, SupportTicketPriority priority) {
        // forward into Zendesk/Jira/whatever you actually use
    }
}
```

State machine: `OPEN → IN_PROGRESS` (`start`), `IN_PROGRESS → RESOLVED`
(`resolve`), and `close` is valid from **any** non-`CLOSED` state — not
only from `RESOLVED`, since closing a duplicate or invalid ticket
shouldn't require resolving it first.

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/v1/support-tickets` | `{type, subject, description, priority}` | Any authenticated principal — no permission required |
| GET | `/api/v1/support-tickets/mine` | — | Lists the caller's own tickets, any status. No permission required. |
| GET | `/api/v1/support-tickets` | — | Lists every ticket in the tenant. Requires `adm:tickets:manage`. |
| POST | `/api/v1/support-tickets/{id}/start` | — | 409 if not `OPEN`. Requires `adm:tickets:manage`. |
| POST | `/api/v1/support-tickets/{id}/resolve` | — | 409 if not `IN_PROGRESS`. Requires `adm:tickets:manage`. |
| POST | `/api/v1/support-tickets/{id}/close` | — | 409 if already `CLOSED`. Requires `adm:tickets:manage`. |

Unlike `invitations`, `support_tickets` has normal row-level security —
there is no token-only lookup path in this feature, so the standard
`tenant_id = current_setting('app.tenant_id', true)::uuid` policy applies
without conflict. See
`docs/superpowers/specs/2026-07-24-gen-adm-support-tickets-design.md` for
the full design rationale.
```

- [ ] **Step 5: Run the full build**

Run: `./gradlew build`
Expected: PASS — full build including all modules and tests.

- [ ] **Step 6: Commit**

```bash
git add gen-adm-demo/src/main/resources/application.yaml scripts/smoke-test.sh README.md docs/integration-guide.md
git commit -m "docs: wire support tickets into gen-adm-demo, smoke test, README, and integration guide"
```

---

## Final review

After all five tasks are complete and committed, do one whole-branch review pass (not per-task): re-read `SupportTicketServiceImpl` alongside `docs/superpowers/specs/2026-07-24-gen-adm-support-tickets-design.md` and confirm every guard (start-requires-OPEN, resolve-requires-IN_PROGRESS, close-from-any-non-CLOSED-state, cross-tenant 404, create/listMine's no-permission-check) is actually exercised by at least one test, and that `README.md`/`docs/integration-guide.md` describe the shipped behavior exactly, not an earlier draft of it.
