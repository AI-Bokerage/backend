# Gen_ADM Data Export Jobs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a tenant-scoped data-export feature to Gen_ADM: any caller holding `adm:exports:manage` can synchronously generate a JSON snapshot of everything Gen_ADM owns for their tenant (roles, permissions, assignments, offboarding jobs, impersonation sessions, invitations, support tickets), list/check/download it, and revoke it early.

**Architecture:** Additive to the existing `gen-adm-starter`/`gen-adm-demo` split — no changes to any prior phase's public surface beyond four new one-line repository query methods. `DataExportEntity` is a single self-contained table with normal tenant-scoped RLS; `DataExportServiceImpl` builds the snapshot synchronously inline (no job queue, no scheduler, no external storage) and stores it as a TEXT column on the same row. No approval workflow, no event-publisher port — this feature has no external system to decouple from (everything it reads and writes is already inside Gen_ADM's own database).

**Tech Stack:** Same as prior phases — Java 21, Spring Boot 4.0.6, Gradle 9.4.1, PostgreSQL, Flyway, Spring Data JPA, Spring AOP, Lombok, Jackson (`ObjectMapper`, Spring Boot's autoconfigured bean — not a new instance), JUnit 5, Mockito, AssertJ, Testcontainers. No new dependencies.

## Global Constraints

- Package root stays `com.example.admsvc` — new code lives alongside existing code, not in a separate module.
- Export status values are exactly `COMPLETED`, `EXPIRED`, `REVOKED` — no `PENDING_APPROVAL`/`APPROVED`/`REJECTED`/`FAILED` (those CPMS states modeled a cross-tenant approval workflow Gen_ADM does not have).
- `request` is synchronous: it builds the snapshot and persists a `COMPLETED` row within the same call, no queued/background work.
- `data_exports` gets normal `ENABLE`/`FORCE ROW LEVEL SECURITY` (unlike `invitations`, which deliberately has none) — there is no token-only lookup path here, so the standard tenant policy applies without conflict.
- One permission code, `adm:exports:manage`, gates every operation (`request`/`getStatus`/`listExports`/`download`/`revoke`) — no self-service, no-permission-required tier (unlike support tickets), since the snapshot contains the tenant's entire RBAC configuration and every other feature's lifecycle records, not just the caller's own data.
- Lazy, read-time expiry: every read/mutate path (`getStatus`, `listExports`, `download`, `revoke`) calls a shared `expireIfOverdue` helper that flips an overdue `COMPLETED` row to `EXPIRED` (and clears `snapshotJson`) before acting. No scheduler anywhere.
- `revoke` and `download` both require `status == COMPLETED`; anything else is a 409 (`GenAdmConflictException`).
- Cross-tenant lookups surface as 404 (`GenAdmNotFoundException`), never 403 — same anti-enumeration convention as every prior feature.
- No new exception subclasses — reuse `GenAdmConflictException`/`GenAdmNotFoundException`.
- No event-publisher port for this feature (unlike `OffboardingEventPublisher`/`ImpersonationEventPublisher`/`InvitationEventPublisher`/`TicketEventPublisher`) — those existed to let a consumer forward work to a real external system Gen_ADM deliberately doesn't reimplement. Export generation has no external counterpart; it only reads Gen_ADM's own repositories and writes to Gen_ADM's own table.
- `DataExportController` lives at `/api/v1/exports` (no `/adm/` path segment).
- Snapshot assembly reuses each feature's *existing* REST response record (`RoleResponse`, `AssignmentResponse`, `OffboardingJobResponse`, `ImpersonationSessionResponse`, `InvitationResponse`, `SupportTicketResponse`) — no new per-section DTOs invented. `InvitationResponse.from(...)` (not `forCreate`) is used, so the plaintext invitation token is never included (it is always `null` via that factory method).
- `DataExportServiceImpl` receives Spring Boot's autoconfigured `ObjectMapper` bean via constructor injection — do not construct a new `ObjectMapper` instance in production code.
- Reuse `PermissionChecker`, `GenAdmPrincipal`, `GlobalExceptionHandler`, `TenantContextAspect`, `GenAdmProperties` completely unmodified except for one additive field (`exportTtlDays`) on `GenAdmProperties`.
- One final whole-branch review at the end of all tasks, not one per task.
- Known sandbox limitation carried forward from prior phases: Testcontainers-based tests (Tasks 1 and 4) can only be verified by code review in this sandbox (confirmed Docker-detection limitation, not a code defect). Testcontainers' default DB user in this test setup is a superuser, so RLS itself is never actually enforced in this test suite (a pre-existing, accepted limitation affecting every prior feature's persistence tests too) — only repository-level `tenant_id = ?` predicate filtering is exercised.

---

## Task 1: Data export persistence — enum, entity, repository, migrations

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/DataExportStatus.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/DataExportEntity.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/DataExportRepository.java`
- Create: `gen-adm-starter/src/main/resources/db/migration/genadm/V10__create_data_exports_table.sql`
- Create: `gen-adm-starter/src/main/resources/db/migration/genadm/V11__enable_data_exports_rls.sql`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/DataExportPersistenceIntegrationTest.java`

**Interfaces:**
- Consumes: the existing `classpath:db/migration/genadm` Flyway location. `V10`/`V11` are the next free migration versions — `V1`-`V9` already exist (`V7` — invitations — has no RLS companion; `V8`/`V9` are support tickets).
- Produces: `DataExportEntity` (`UUID id`, `UUID tenantId`, `UUID requestedByUserId`, `DataExportStatus status`, `String snapshotJson` (nullable), `long recordCount`, `Instant expiresAt`, `Instant revokedAt`, `UUID revokedByUserId`, `Long version`, `Instant createdAt`, `Instant updatedAt`) — later tasks depend on these exact field names. `DataExportRepository.findByIdAndTenantId(UUID, UUID): Optional<DataExportEntity>`, `.findAllByTenantId(UUID): List<DataExportEntity>` — Task 2 calls these exact signatures.

- [ ] **Step 1: Create the status enum**

`gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/DataExportStatus.java`:
```java
package com.example.admsvc.domain.enums;

public enum DataExportStatus {
    COMPLETED,
    EXPIRED,
    REVOKED
}
```

- [ ] **Step 2: Write the failing persistence integration test first**

`gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/DataExportPersistenceIntegrationTest.java`:
```java
package com.example.admsvc.infrastructure.persistence;

import com.example.admsvc.domain.enums.DataExportStatus;
import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;
import com.example.admsvc.infrastructure.persistence.repository.DataExportRepository;
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
@SpringBootTest(classes = DataExportPersistenceIntegrationTest.TestApp.class)
class DataExportPersistenceIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DataExportRepository repository;

    private DataExportEntity newExport(UUID tenantId, UUID requestedBy) {
        return DataExportEntity.builder()
                .tenantId(tenantId)
                .requestedByUserId(requestedBy)
                .status(DataExportStatus.COMPLETED)
                .snapshotJson("{\"tenantId\":\"" + tenantId + "\"}")
                .recordCount(3L)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
    }

    @Test
    void savesAndFindsByIdAndTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        DataExportEntity saved = repository.saveAndFlush(newExport(tenantId, requestedBy));

        Optional<DataExportEntity> found = repository.findByIdAndTenantId(saved.getId(), tenantId);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(DataExportStatus.COMPLETED);
        assertThat(found.get().getSnapshotJson()).contains(tenantId.toString());
        assertThat(found.get().getRecordCount()).isEqualTo(3L);
        assertThat(found.get().getRevokedByUserId()).isNull();
    }

    @Test
    void anExportIsInvisibleWhenLookedUpUnderTheWrongTenant() {
        UUID tenantId = UUID.randomUUID();
        DataExportEntity saved = repository.saveAndFlush(newExport(tenantId, UUID.randomUUID()));

        assertThat(repository.findByIdAndTenantId(saved.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void findsAllExportsForATenantRegardlessOfRequester() {
        UUID tenantId = UUID.randomUUID();
        DataExportEntity a = repository.saveAndFlush(newExport(tenantId, UUID.randomUUID()));
        DataExportEntity b = repository.saveAndFlush(newExport(tenantId, UUID.randomUUID()));
        repository.saveAndFlush(newExport(UUID.randomUUID(), UUID.randomUUID())); // different tenant

        List<DataExportEntity> all = repository.findAllByTenantId(tenantId);
        assertThat(all).extracting(DataExportEntity::getId).containsExactlyInAnyOrder(a.getId(), b.getId());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.persistence.DataExportPersistenceIntegrationTest"`
Expected: FAIL — compile error, `DataExportEntity`/`DataExportRepository` do not exist yet.

- [ ] **Step 4: Create the entity**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/DataExportEntity.java`:
```java
package com.example.admsvc.infrastructure.persistence.entity;

import com.example.admsvc.domain.enums.DataExportStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_exports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataExportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DataExportStatus status;

    @Column(name = "snapshot_json", columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "record_count", nullable = false)
    private long recordCount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by_user_id")
    private UUID revokedByUserId;

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
            status = DataExportStatus.COMPLETED;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
```

- [ ] **Step 5: Create the repository**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/DataExportRepository.java`:
```java
package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataExportRepository extends JpaRepository<DataExportEntity, UUID> {

    Optional<DataExportEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<DataExportEntity> findAllByTenantId(UUID tenantId);
}
```

- [ ] **Step 6: Create the migrations**

`gen-adm-starter/src/main/resources/db/migration/genadm/V10__create_data_exports_table.sql`:
```sql
CREATE TABLE data_exports (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    requested_by_user_id  UUID NOT NULL,
    status                VARCHAR(20) NOT NULL,
    snapshot_json         TEXT,
    record_count          BIGINT NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    revoked_at            TIMESTAMPTZ,
    revoked_by_user_id    UUID,
    version               BIGINT,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_data_exports_tenant_status
    ON data_exports(tenant_id, status);
```

`gen-adm-starter/src/main/resources/db/migration/genadm/V11__enable_data_exports_rls.sql`:
```sql
ALTER TABLE data_exports ENABLE ROW LEVEL SECURITY;
ALTER TABLE data_exports FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_data_exports ON data_exports
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.persistence.DataExportPersistenceIntegrationTest"`
Expected: PASS (3 tests) — subject to the known Testcontainers/Docker sandbox limitation noted in Global Constraints; verify by code review if Docker isn't available here.

- [ ] **Step 8: Commit**

```bash
git add gen-adm-starter/src/main/java/com/example/admsvc/domain/enums/DataExportStatus.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/DataExportEntity.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/DataExportRepository.java \
        gen-adm-starter/src/main/resources/db/migration/genadm/V10__create_data_exports_table.sql \
        gen-adm-starter/src/main/resources/db/migration/genadm/V11__enable_data_exports_rls.sql \
        gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/DataExportPersistenceIntegrationTest.java
git commit -m "feat: add data export persistence (entity, repository, RLS migrations)"
```

---

## Task 2: Snapshot assembly + DataExportService (request/getStatus/listExports/download/revoke)

**Files:**
- Modify: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/UserRoleAssignmentRepository.java`
- Modify: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/OffboardingJobRepository.java`
- Modify: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/ImpersonationSessionRepository.java`
- Modify: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/InvitationRepository.java`
- Modify: `gen-adm-starter/src/main/java/com/example/admsvc/config/properties/GenAdmProperties.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/TenantExportSnapshot.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/service/DataExportService.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/DataExportServiceImpl.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/DataExportServiceImplTest.java`

**Interfaces:**
- Consumes: `DataExportRepository`/`DataExportEntity`/`DataExportStatus` from Task 1 (exact signatures above). `RoleRepository.findAllByTenantId` and `SupportTicketRepository.findAllByTenantId` (both pre-existing, unmodified). `RoleResponse`, `AssignmentResponse`, `OffboardingJobResponse`, `ImpersonationSessionResponse`, `InvitationResponse`, `SupportTicketResponse` (all pre-existing, unmodified — each has a static `.from(entity)` factory). `GenAdmConflictException`, `GenAdmNotFoundException` from `com.example.admsvc.common.exception` (pre-existing).
- Produces: `DataExportService` with
  ```java
  DataExportEntity request(UUID tenantId, UUID requestedByUserId);
  DataExportEntity getStatus(UUID tenantId, UUID exportId);
  List<DataExportEntity> listExports(UUID tenantId);
  String download(UUID tenantId, UUID exportId);
  DataExportEntity revoke(UUID tenantId, UUID exportId, UUID revokedByUserId);
  ```
  Task 3's controller calls these exact signatures. `GenAdmProperties.getExportTtlDays(): int` (new field, default 7).

- [ ] **Step 1: Add one additive tenant-wide query method to each of four existing repositories**

In `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/UserRoleAssignmentRepository.java`, add this method inside the existing interface (do not remove or change any existing method):
```java
    List<UserRoleAssignmentEntity> findAllByTenantId(UUID tenantId);
```

In `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/OffboardingJobRepository.java`, add this method inside the existing interface:
```java
    List<OffboardingJobEntity> findAllByTenantId(UUID tenantId);
```
This repository currently has no `List` import — add `import java.util.List;` alongside the existing `Optional`/`UUID` imports.

In `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/ImpersonationSessionRepository.java`, add this method inside the existing interface:
```java
    List<ImpersonationSessionEntity> findAllByTenantId(UUID tenantId);
```

In `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/InvitationRepository.java`, add this method inside the existing interface:
```java
    List<InvitationEntity> findAllByTenantId(UUID tenantId);
```

- [ ] **Step 2: Add the export TTL property**

In `gen-adm-starter/src/main/java/com/example/admsvc/config/properties/GenAdmProperties.java`, add this field alongside the existing `invitationTtlDays` field (do not change `invitationTtlDays` or the class's other members):
```java
    /** Data export retention window in days, applied once at creation. */
    private int exportTtlDays = 7;
```

- [ ] **Step 3: Create the snapshot record**

`gen-adm-starter/src/main/java/com/example/admsvc/application/impl/TenantExportSnapshot.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.api.dto.response.AssignmentResponse;
import com.example.admsvc.api.dto.response.ImpersonationSessionResponse;
import com.example.admsvc.api.dto.response.InvitationResponse;
import com.example.admsvc.api.dto.response.OffboardingJobResponse;
import com.example.admsvc.api.dto.response.RoleResponse;
import com.example.admsvc.api.dto.response.SupportTicketResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The full JSON document persisted into {@code DataExportEntity.snapshotJson}.
 * Reuses each feature's existing REST response shape rather than inventing
 * a new one — this is the same data those features' own GET endpoints
 * already return, just assembled into a single tenant-wide document.
 */
public record TenantExportSnapshot(
        UUID tenantId,
        Instant generatedAt,
        List<RoleResponse> roles,
        List<AssignmentResponse> userRoleAssignments,
        List<OffboardingJobResponse> offboardingJobs,
        List<ImpersonationSessionResponse> impersonationSessions,
        List<InvitationResponse> invitations,
        List<SupportTicketResponse> supportTickets) {
}
```

- [ ] **Step 4: Create the service interface**

`gen-adm-starter/src/main/java/com/example/admsvc/application/service/DataExportService.java`:
```java
package com.example.admsvc.application.service;

import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;

import java.util.List;
import java.util.UUID;

public interface DataExportService {

    DataExportEntity request(UUID tenantId, UUID requestedByUserId);

    DataExportEntity getStatus(UUID tenantId, UUID exportId);

    List<DataExportEntity> listExports(UUID tenantId);

    String download(UUID tenantId, UUID exportId);

    DataExportEntity revoke(UUID tenantId, UUID exportId, UUID revokedByUserId);
}
```

- [ ] **Step 5: Write the failing unit test first**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/DataExportServiceImplTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.config.properties.GenAdmProperties;
import com.example.admsvc.domain.enums.DataExportStatus;
import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.domain.enums.OffboardingJobStatus;
import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketStatus;
import com.example.admsvc.domain.enums.SupportTicketType;
import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;
import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.entity.SupportTicketEntity;
import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.example.admsvc.infrastructure.persistence.repository.DataExportRepository;
import com.example.admsvc.infrastructure.persistence.repository.ImpersonationSessionRepository;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import com.example.admsvc.infrastructure.persistence.repository.SupportTicketRepository;
import com.example.admsvc.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataExportServiceImplTest {

    @Mock private DataExportRepository dataExportRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Mock private OffboardingJobRepository offboardingJobRepository;
    @Mock private ImpersonationSessionRepository impersonationSessionRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private SupportTicketRepository supportTicketRepository;

    private GenAdmProperties properties;
    private ObjectMapper objectMapper;
    private DataExportServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new GenAdmProperties();
        properties.setExportTtlDays(7);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new DataExportServiceImpl(dataExportRepository, roleRepository, userRoleAssignmentRepository,
                offboardingJobRepository, impersonationSessionRepository, invitationRepository,
                supportTicketRepository, properties, objectMapper);
        lenient().when(dataExportRepository.saveAndFlush(any())).thenAnswer(inv -> {
            DataExportEntity export = inv.getArgument(0);
            if (export.getId() == null) {
                export.setId(UUID.randomUUID());
            }
            return export;
        });
    }

    private DataExportEntity export(UUID tenantId, DataExportStatus status, Instant expiresAt) {
        return DataExportEntity.builder()
                .id(UUID.randomUUID()).tenantId(tenantId)
                .requestedByUserId(UUID.randomUUID())
                .status(status)
                .snapshotJson("{}")
                .recordCount(0L)
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    void requestAssemblesAllSixSectionsAndSumsRecordCount() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();

        when(roleRepository.findAllByTenantId(tenantId)).thenReturn(List.of(
                RoleEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("owner").build()));
        when(userRoleAssignmentRepository.findAllByTenantId(tenantId)).thenReturn(List.of(
                UserRoleAssignmentEntity.builder().id(UUID.randomUUID()).tenantId(tenantId)
                        .userId(UUID.randomUUID()).roleId(UUID.randomUUID()).build()));
        when(offboardingJobRepository.findAllByTenantId(tenantId)).thenReturn(List.of(
                OffboardingJobEntity.builder().id(UUID.randomUUID()).tenantId(tenantId)
                        .status(OffboardingJobStatus.COMPLETED).build()));
        when(impersonationSessionRepository.findAllByTenantId(tenantId)).thenReturn(List.of(
                ImpersonationSessionEntity.builder().id(UUID.randomUUID()).tenantId(tenantId)
                        .status(ImpersonationSessionStatus.ENDED).build()));
        when(invitationRepository.findAllByTenantId(tenantId)).thenReturn(List.of(
                InvitationEntity.builder().id(UUID.randomUUID()).tenantId(tenantId)
                        .status(InvitationStatus.PENDING).build()));
        when(supportTicketRepository.findAllByTenantId(tenantId)).thenReturn(List.of(
                SupportTicketEntity.builder().id(UUID.randomUUID()).tenantId(tenantId)
                        .type(SupportTicketType.GENERAL).priority(SupportTicketPriority.LOW)
                        .status(SupportTicketStatus.OPEN).build()));

        DataExportEntity result = service.request(tenantId, requestedBy);

        assertThat(result.getStatus()).isEqualTo(DataExportStatus.COMPLETED);
        assertThat(result.getRecordCount()).isEqualTo(6L);
        assertThat(result.getExpiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));

        TenantExportSnapshot snapshot = objectMapper.readValue(result.getSnapshotJson(), TenantExportSnapshot.class);
        assertThat(snapshot.tenantId()).isEqualTo(tenantId);
        assertThat(snapshot.roles()).hasSize(1);
        assertThat(snapshot.userRoleAssignments()).hasSize(1);
        assertThat(snapshot.offboardingJobs()).hasSize(1);
        assertThat(snapshot.impersonationSessions()).hasSize(1);
        assertThat(snapshot.invitations()).hasSize(1);
        assertThat(snapshot.supportTickets()).hasSize(1);
    }

    @Test
    void listExportsReturnsEveryExportForTheTenant() {
        UUID tenantId = UUID.randomUUID();
        DataExportEntity a = export(tenantId, DataExportStatus.COMPLETED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(dataExportRepository.findAllByTenantId(tenantId)).thenReturn(List.of(a));

        assertThat(service.listExports(tenantId)).containsExactly(a);
    }

    @Test
    void downloadReturnsSnapshotWhileCompleted() {
        UUID tenantId = UUID.randomUUID();
        DataExportEntity exp = export(tenantId, DataExportStatus.COMPLETED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(dataExportRepository.findByIdAndTenantId(exp.getId(), tenantId)).thenReturn(Optional.of(exp));

        assertThat(service.download(tenantId, exp.getId())).isEqualTo("{}");
    }

    @Test
    void downloadThrowsConflictWhenRevoked() {
        UUID tenantId = UUID.randomUUID();
        DataExportEntity exp = export(tenantId, DataExportStatus.REVOKED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(dataExportRepository.findByIdAndTenantId(exp.getId(), tenantId)).thenReturn(Optional.of(exp));

        assertThatThrownBy(() -> service.download(tenantId, exp.getId()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void downloadFlipsToExpiredAndClearsSnapshotOncePastExpiry() {
        UUID tenantId = UUID.randomUUID();
        DataExportEntity exp = export(tenantId, DataExportStatus.COMPLETED, Instant.now().minus(1, ChronoUnit.DAYS));
        when(dataExportRepository.findByIdAndTenantId(exp.getId(), tenantId)).thenReturn(Optional.of(exp));

        assertThatThrownBy(() -> service.download(tenantId, exp.getId()))
                .isInstanceOf(GenAdmConflictException.class);
        assertThat(exp.getStatus()).isEqualTo(DataExportStatus.EXPIRED);
        assertThat(exp.getSnapshotJson()).isNull();
    }

    @Test
    void revokeSucceedsFromCompleted() {
        UUID tenantId = UUID.randomUUID();
        UUID revokedBy = UUID.randomUUID();
        DataExportEntity exp = export(tenantId, DataExportStatus.COMPLETED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(dataExportRepository.findByIdAndTenantId(exp.getId(), tenantId)).thenReturn(Optional.of(exp));

        DataExportEntity revoked = service.revoke(tenantId, exp.getId(), revokedBy);

        assertThat(revoked.getStatus()).isEqualTo(DataExportStatus.REVOKED);
        assertThat(revoked.getRevokedByUserId()).isEqualTo(revokedBy);
        assertThat(revoked.getSnapshotJson()).isNull();
    }

    @Test
    void revokeThrowsConflictWhenAlreadyRevoked() {
        UUID tenantId = UUID.randomUUID();
        DataExportEntity exp = export(tenantId, DataExportStatus.REVOKED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(dataExportRepository.findByIdAndTenantId(exp.getId(), tenantId)).thenReturn(Optional.of(exp));

        assertThatThrownBy(() -> service.revoke(tenantId, exp.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void throwsNotFoundForACrossTenantLookup() {
        UUID exportId = UUID.randomUUID();
        when(dataExportRepository.findByIdAndTenantId(eq(exportId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(UUID.randomUUID(), exportId))
                .isInstanceOf(GenAdmNotFoundException.class);
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.DataExportServiceImplTest"`
Expected: FAIL — compile error, `DataExportServiceImpl` does not exist yet.

- [ ] **Step 7: Write the implementation**

`gen-adm-starter/src/main/java/com/example/admsvc/application/impl/DataExportServiceImpl.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.api.dto.response.AssignmentResponse;
import com.example.admsvc.api.dto.response.ImpersonationSessionResponse;
import com.example.admsvc.api.dto.response.InvitationResponse;
import com.example.admsvc.api.dto.response.OffboardingJobResponse;
import com.example.admsvc.api.dto.response.RoleResponse;
import com.example.admsvc.api.dto.response.SupportTicketResponse;
import com.example.admsvc.application.service.DataExportService;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.config.properties.GenAdmProperties;
import com.example.admsvc.domain.enums.DataExportStatus;
import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;
import com.example.admsvc.infrastructure.persistence.repository.DataExportRepository;
import com.example.admsvc.infrastructure.persistence.repository.ImpersonationSessionRepository;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import com.example.admsvc.infrastructure.persistence.repository.SupportTicketRepository;
import com.example.admsvc.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class DataExportServiceImpl implements DataExportService {

    private final DataExportRepository dataExportRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final OffboardingJobRepository offboardingJobRepository;
    private final ImpersonationSessionRepository impersonationSessionRepository;
    private final InvitationRepository invitationRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final GenAdmProperties properties;
    private final ObjectMapper objectMapper;

    public DataExportServiceImpl(DataExportRepository dataExportRepository,
                                  RoleRepository roleRepository,
                                  UserRoleAssignmentRepository userRoleAssignmentRepository,
                                  OffboardingJobRepository offboardingJobRepository,
                                  ImpersonationSessionRepository impersonationSessionRepository,
                                  InvitationRepository invitationRepository,
                                  SupportTicketRepository supportTicketRepository,
                                  GenAdmProperties properties,
                                  ObjectMapper objectMapper) {
        this.dataExportRepository = dataExportRepository;
        this.roleRepository = roleRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.offboardingJobRepository = offboardingJobRepository;
        this.impersonationSessionRepository = impersonationSessionRepository;
        this.invitationRepository = invitationRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public DataExportEntity request(UUID tenantId, UUID requestedByUserId) {
        List<RoleResponse> roles = roleRepository.findAllByTenantId(tenantId).stream()
                .map(RoleResponse::from).toList();
        List<AssignmentResponse> assignments = userRoleAssignmentRepository.findAllByTenantId(tenantId).stream()
                .map(AssignmentResponse::from).toList();
        List<OffboardingJobResponse> offboardingJobs = offboardingJobRepository.findAllByTenantId(tenantId).stream()
                .map(OffboardingJobResponse::from).toList();
        List<ImpersonationSessionResponse> impersonationSessions =
                impersonationSessionRepository.findAllByTenantId(tenantId).stream()
                        .map(ImpersonationSessionResponse::from).toList();
        List<InvitationResponse> invitations = invitationRepository.findAllByTenantId(tenantId).stream()
                .map(InvitationResponse::from).toList();
        List<SupportTicketResponse> supportTickets = supportTicketRepository.findAllByTenantId(tenantId).stream()
                .map(SupportTicketResponse::from).toList();

        TenantExportSnapshot snapshot = new TenantExportSnapshot(
                tenantId, Instant.now(), roles, assignments, offboardingJobs,
                impersonationSessions, invitations, supportTickets);

        long recordCount = roles.size() + assignments.size() + offboardingJobs.size()
                + impersonationSessions.size() + invitations.size() + supportTickets.size();

        DataExportEntity export = DataExportEntity.builder()
                .tenantId(tenantId)
                .requestedByUserId(requestedByUserId)
                .status(DataExportStatus.COMPLETED)
                .snapshotJson(writeSnapshot(snapshot))
                .recordCount(recordCount)
                .expiresAt(Instant.now().plus(properties.getExportTtlDays(), ChronoUnit.DAYS))
                .build();
        return dataExportRepository.saveAndFlush(export);
    }

    @Override
    @Transactional
    public DataExportEntity getStatus(UUID tenantId, UUID exportId) {
        return expireIfOverdue(findOrThrow(tenantId, exportId));
    }

    @Override
    @Transactional
    public List<DataExportEntity> listExports(UUID tenantId) {
        return dataExportRepository.findAllByTenantId(tenantId).stream()
                .map(this::expireIfOverdue)
                .toList();
    }

    @Override
    @Transactional
    public String download(UUID tenantId, UUID exportId) {
        DataExportEntity export = expireIfOverdue(findOrThrow(tenantId, exportId));
        if (export.getStatus() != DataExportStatus.COMPLETED) {
            throw new GenAdmConflictException("Export is not downloadable: " + exportId);
        }
        return export.getSnapshotJson();
    }

    @Override
    @Transactional
    public DataExportEntity revoke(UUID tenantId, UUID exportId, UUID revokedByUserId) {
        DataExportEntity export = expireIfOverdue(findOrThrow(tenantId, exportId));
        if (export.getStatus() != DataExportStatus.COMPLETED) {
            throw new GenAdmConflictException("Export cannot be revoked: " + exportId);
        }
        export.setStatus(DataExportStatus.REVOKED);
        export.setRevokedAt(Instant.now());
        export.setRevokedByUserId(revokedByUserId);
        export.setSnapshotJson(null);
        return dataExportRepository.saveAndFlush(export);
    }

    private DataExportEntity findOrThrow(UUID tenantId, UUID exportId) {
        return dataExportRepository.findByIdAndTenantId(exportId, tenantId)
                .orElseThrow(() -> new GenAdmNotFoundException("Export not found: " + exportId));
    }

    private DataExportEntity expireIfOverdue(DataExportEntity export) {
        if (export.getStatus() == DataExportStatus.COMPLETED && Instant.now().isAfter(export.getExpiresAt())) {
            export.setStatus(DataExportStatus.EXPIRED);
            export.setSnapshotJson(null);
            return dataExportRepository.saveAndFlush(export);
        }
        return export;
    }

    private String writeSnapshot(TenantExportSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize tenant export snapshot", e);
        }
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.DataExportServiceImplTest"`
Expected: PASS (7 tests) — plain Mockito, no Spring context, no database; expected to actually run here.

- [ ] **Step 9: Commit**

```bash
git add gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/UserRoleAssignmentRepository.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/OffboardingJobRepository.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/ImpersonationSessionRepository.java \
        gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/InvitationRepository.java \
        gen-adm-starter/src/main/java/com/example/admsvc/config/properties/GenAdmProperties.java \
        gen-adm-starter/src/main/java/com/example/admsvc/application/impl/TenantExportSnapshot.java \
        gen-adm-starter/src/main/java/com/example/admsvc/application/service/DataExportService.java \
        gen-adm-starter/src/main/java/com/example/admsvc/application/impl/DataExportServiceImpl.java \
        gen-adm-starter/src/test/java/com/example/admsvc/application/impl/DataExportServiceImplTest.java
git commit -m "feat: add DataExportService with synchronous snapshot generation and lazy expiry"
```

---

## Task 3: REST layer — DataExportController

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/DataExportResponse.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/controller/DataExportController.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/api/controller/DataExportControllerTest.java`

**Interfaces:**
- Consumes: `DataExportService` (Task 2, exact signatures above), `PermissionChecker.require(GenAdmPrincipal, String): void` (pre-existing), `GenAdmPrincipal.tenantId()`/`.userId()` (pre-existing), `GlobalExceptionHandler` (pre-existing, unmodified).
- Produces: REST routes under `/api/v1/exports` — `POST /`, `GET /{id}`, `GET /`, `GET /{id}/download`, `POST /{id}/revoke`. No other task depends on `DataExportResponse` — it is the terminal DTO.

- [ ] **Step 1: Create the response DTO**

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/DataExportResponse.java`:
```java
package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;

import java.time.Instant;
import java.util.UUID;

public record DataExportResponse(
        UUID id,
        UUID tenantId,
        UUID requestedByUserId,
        String status,
        long recordCount,
        Instant expiresAt,
        Instant revokedAt,
        UUID revokedByUserId,
        Instant createdAt) {

    public static DataExportResponse from(DataExportEntity export) {
        return new DataExportResponse(
                export.getId(),
                export.getTenantId(),
                export.getRequestedByUserId(),
                export.getStatus().name(),
                export.getRecordCount(),
                export.getExpiresAt(),
                export.getRevokedAt(),
                export.getRevokedByUserId(),
                export.getCreatedAt());
    }
}
```

- [ ] **Step 2: Write the failing controller test first**

`gen-adm-starter/src/test/java/com/example/admsvc/api/controller/DataExportControllerTest.java`:
```java
package com.example.admsvc.api.controller;

import com.example.admsvc.api.advice.GlobalExceptionHandler;
import com.example.admsvc.application.service.DataExportService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.DataExportStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DataExportControllerTest {

    private static final String MANAGE_PERM = "adm:exports:manage";

    private DataExportService dataExportService;
    private PermissionChecker permissionChecker;
    private MockMvc mockMvc;

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private final TestPrincipal principal = new TestPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        dataExportService = mock(DataExportService.class);
        permissionChecker = mock(PermissionChecker.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DataExportController(dataExportService, permissionChecker))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null));
    }

    private DataExportEntity export(DataExportStatus status) {
        return DataExportEntity.builder()
                .id(UUID.randomUUID()).tenantId(principal.tenantId())
                .requestedByUserId(principal.userId())
                .status(status)
                .snapshotJson("{\"tenantId\":\"" + principal.tenantId() + "\"}")
                .recordCount(2L)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
    }

    @Test
    void requestRequiresManagePermission() throws Exception {
        doThrow(new GenAdmForbiddenException("Missing required permission: " + MANAGE_PERM))
                .when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));

        mockMvc.perform(post("/api/v1/exports"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void requestReturnsTheCompletedExport() throws Exception {
        DataExportEntity created = export(DataExportStatus.COMPLETED);
        when(dataExportService.request(principal.tenantId(), principal.userId())).thenReturn(created);

        mockMvc.perform(post("/api/v1/exports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.recordCount").value(2));
        verify(permissionChecker).require(principal, MANAGE_PERM);
    }

    @Test
    void listExportsRequiresManagePermission() throws Exception {
        doThrow(new GenAdmForbiddenException("Missing required permission: " + MANAGE_PERM))
                .when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));

        mockMvc.perform(get("/api/v1/exports"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void listExportsReturnsEveryExportForTheTenant() throws Exception {
        when(dataExportService.listExports(principal.tenantId()))
                .thenReturn(List.of(export(DataExportStatus.COMPLETED)));

        mockMvc.perform(get("/api/v1/exports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void getStatusReturns404ForACrossTenantExport() throws Exception {
        UUID exportId = UUID.randomUUID();
        when(dataExportService.getStatus(principal.tenantId(), exportId))
                .thenThrow(new GenAdmNotFoundException("Export not found: " + exportId));

        mockMvc.perform(get("/api/v1/exports/" + exportId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void downloadReturnsTheRawSnapshotJson() throws Exception {
        UUID exportId = UUID.randomUUID();
        when(dataExportService.download(principal.tenantId(), exportId))
                .thenReturn("{\"tenantId\":\"" + principal.tenantId() + "\"}");

        mockMvc.perform(get("/api/v1/exports/" + exportId + "/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.tenantId").value(principal.tenantId().toString()));
    }

    @Test
    void revokeRequiresManagePermissionAndPassesRevokedBy() throws Exception {
        UUID exportId = UUID.randomUUID();
        when(dataExportService.revoke(principal.tenantId(), exportId, principal.userId()))
                .thenReturn(export(DataExportStatus.REVOKED));

        mockMvc.perform(post("/api/v1/exports/" + exportId + "/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
        verify(dataExportService).revoke(principal.tenantId(), exportId, principal.userId());
        verify(permissionChecker).require(principal, MANAGE_PERM);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.api.controller.DataExportControllerTest"`
Expected: FAIL — compile error, `DataExportController` does not exist yet.

- [ ] **Step 4: Write the controller**

`gen-adm-starter/src/main/java/com/example/admsvc/api/controller/DataExportController.java`:
```java
package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.response.DataExportResponse;
import com.example.admsvc.application.service.DataExportService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exports")
public class DataExportController {

    private static final String MANAGE_EXPORTS = "adm:exports:manage";

    private final DataExportService dataExportService;
    private final PermissionChecker permissionChecker;

    public DataExportController(DataExportService dataExportService, PermissionChecker permissionChecker) {
        this.dataExportService = dataExportService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public DataExportResponse request(@AuthenticationPrincipal GenAdmPrincipal principal) {
        permissionChecker.require(principal, MANAGE_EXPORTS);
        return DataExportResponse.from(dataExportService.request(principal.tenantId(), principal.userId()));
    }

    @GetMapping("/{id}")
    public DataExportResponse getStatus(@AuthenticationPrincipal GenAdmPrincipal principal,
                                         @PathVariable UUID id) {
        permissionChecker.require(principal, MANAGE_EXPORTS);
        return DataExportResponse.from(dataExportService.getStatus(principal.tenantId(), id));
    }

    @GetMapping
    public List<DataExportResponse> listExports(@AuthenticationPrincipal GenAdmPrincipal principal) {
        permissionChecker.require(principal, MANAGE_EXPORTS);
        return dataExportService.listExports(principal.tenantId()).stream()
                .map(DataExportResponse::from)
                .toList();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<String> download(@AuthenticationPrincipal GenAdmPrincipal principal,
                                            @PathVariable UUID id) {
        permissionChecker.require(principal, MANAGE_EXPORTS);
        String snapshotJson = dataExportService.download(principal.tenantId(), id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(snapshotJson);
    }

    @PostMapping("/{id}/revoke")
    public DataExportResponse revoke(@AuthenticationPrincipal GenAdmPrincipal principal,
                                      @PathVariable UUID id) {
        permissionChecker.require(principal, MANAGE_EXPORTS);
        return DataExportResponse.from(
                dataExportService.revoke(principal.tenantId(), id, principal.userId()));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.api.controller.DataExportControllerTest"`
Expected: PASS (7 tests) — MockMvc standalone setup, no Spring context, no database.

- [ ] **Step 6: Commit**

```bash
git add gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/DataExportResponse.java \
        gen-adm-starter/src/main/java/com/example/admsvc/api/controller/DataExportController.java \
        gen-adm-starter/src/test/java/com/example/admsvc/api/controller/DataExportControllerTest.java
git commit -m "feat: add data-export REST controller (request/getStatus/listExports/download/revoke)"
```

---

## Task 4: Full-flow integration test

**Files:**
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/DataExportIntegrationTest.java`

**Interfaces:**
- Consumes: `DataExportServiceImpl` (Task 2) wired through the real Spring context (`GenAdmAutoConfiguration`, `TenantContextAspect`, Flyway migrations from Task 1, and the real Spring Boot-provided `ObjectMapper` bean), plus `RoleRepository`/`RoleEntity` (pre-existing) to seed one role so the snapshot has non-empty content.
- Produces: nothing consumed by later tasks — verification-only.

- [ ] **Step 1: Write the integration test**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/DataExportIntegrationTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.DataExportStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.repository.DataExportRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = DataExportIntegrationTest.TestApp.class)
class DataExportIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    @Autowired
    private DataExportServiceImpl dataExportService;

    @Autowired
    private DataExportRepository dataExportRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private void authenticateAs(UUID tenantId, UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new TestPrincipal(tenantId, userId), null));
    }

    @Test
    void requestDownloadRevokeFlow() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID revokedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);
        roleRepository.saveAndFlush(RoleEntity.builder().tenantId(tenantId).name("owner").build());

        DataExportEntity created = dataExportService.request(tenantId, requestedBy);
        assertThat(created.getStatus()).isEqualTo(DataExportStatus.COMPLETED);
        assertThat(created.getRecordCount()).isGreaterThanOrEqualTo(1L);

        TenantExportSnapshot snapshot = objectMapper.readValue(created.getSnapshotJson(), TenantExportSnapshot.class);
        assertThat(snapshot.roles()).hasSizeGreaterThanOrEqualTo(1);

        String downloaded = dataExportService.download(tenantId, created.getId());
        assertThat(downloaded).isEqualTo(created.getSnapshotJson());

        DataExportEntity revoked = dataExportService.revoke(tenantId, created.getId(), revokedBy);
        assertThat(revoked.getStatus()).isEqualTo(DataExportStatus.REVOKED);
        assertThat(revoked.getRevokedByUserId()).isEqualTo(revokedBy);

        assertThatThrownBy(() -> dataExportService.download(tenantId, created.getId()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void listExportsReturnsAllExportsForTheTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        dataExportService.request(tenantId, requestedBy);
        dataExportService.request(tenantId, requestedBy);

        List<DataExportEntity> all = dataExportService.listExports(tenantId);
        assertThat(all).hasSize(2);
    }

    @Test
    void crossTenantLookupIs404() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);
        DataExportEntity created = dataExportService.request(tenantId, requestedBy);

        authenticateAs(UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> dataExportService.getStatus(UUID.randomUUID(), created.getId()))
                .isInstanceOf(GenAdmNotFoundException.class);
    }

    @Test
    void downloadingAfterTtlElapsedLazilyExpiresAndReturnsConflict() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);
        DataExportEntity created = dataExportService.request(tenantId, requestedBy);

        DataExportEntity stored = dataExportRepository.findById(created.getId()).orElseThrow();
        stored.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        dataExportRepository.saveAndFlush(stored);

        assertThatThrownBy(() -> dataExportService.download(tenantId, created.getId()))
                .isInstanceOf(GenAdmConflictException.class);

        DataExportEntity afterExpiry = dataExportRepository.findById(created.getId()).orElseThrow();
        assertThat(afterExpiry.getStatus()).isEqualTo(DataExportStatus.EXPIRED);
        assertThat(afterExpiry.getSnapshotJson()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.DataExportIntegrationTest"`
Expected: PASS (4 tests) — subject to the known Testcontainers/Docker sandbox limitation noted in Global Constraints; verify by code review if Docker isn't available here.

- [ ] **Step 3: Run the full module test suite**

Run: `./gradlew :gen-adm-starter:test`
Expected: PASS — all prior-phase and data-export tests green together, no cross-test interference.

- [ ] **Step 4: Commit**

```bash
git add gen-adm-starter/src/test/java/com/example/admsvc/application/impl/DataExportIntegrationTest.java
git commit -m "test: add full-flow data export integration test (request/download/revoke, TTL lazy-expiry, cross-tenant 404)"
```

---

## Task 5: Demo wiring, smoke test, docs

**Files:**
- Modify: `gen-adm-demo/src/main/resources/application.yaml`
- Modify: `scripts/smoke-test.sh`
- Modify: `README.md`
- Modify: `docs/integration-guide.md`

**Interfaces:**
- Consumes: `POST/GET /api/v1/exports`, `GET /api/v1/exports/{id}`, `GET /api/v1/exports/{id}/download`, `POST /api/v1/exports/{id}/revoke` (Task 3), `adm:exports:manage` permission code (new in this task), the existing `PUT /api/v1/roles/{id}/permissions` route (Phase 1).
- Produces: nothing — terminal wiring/documentation task.

- [ ] **Step 1: Add the permission code to the demo's catalog**

In `gen-adm-demo/src/main/resources/application.yaml`, extend the `gen-adm.permissions` list (currently ending at `adm:tickets:manage`) by appending:
```yaml
    - code: adm:exports:manage
      description: Request, list, download, and revoke tenant data exports
```

- [ ] **Step 2: Extend the smoke test**

In `scripts/smoke-test.sh`, append at the end of the file, after the existing support-tickets block and before `echo "Smoke test complete."`:
```bash
echo "== Grant adm:exports:manage to the viewer role =="
curl -sf -X PUT "$BASE_URL/api/v1/roles/$ROLE_ID/permissions" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d '{"permissionCodes":["adm:exports:manage"]}'
echo

echo "== Request a data export as the second user (now holding adm:exports:manage) =="
EXPORT_RESPONSE=$(curl -sf -X POST "$BASE_URL/api/v1/exports" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID")
echo "$EXPORT_RESPONSE"
EXPORT_ID=$(echo "$EXPORT_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

echo "== List exports =="
curl -sf "$BASE_URL/api/v1/exports" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo

echo "== Get export status =="
curl -sf "$BASE_URL/api/v1/exports/$EXPORT_ID" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo

echo "== Download the export snapshot =="
curl -sf "$BASE_URL/api/v1/exports/$EXPORT_ID/download" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo

echo "== Revoke the export =="
curl -sf -X POST "$BASE_URL/api/v1/exports/$EXPORT_ID/revoke" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo
```
Keep the final `echo "Smoke test complete."` as the last line of the file.

- [ ] **Step 3: Update README.md**

In `README.md`, append after the existing "Support Tickets" section:
```markdown

## Data Export Jobs

Tenant-scoped, synchronous export of everything Gen_ADM owns for a tenant —
roles (with their permission codes), user↔role assignments, offboarding
jobs, impersonation sessions, invitations, and support tickets — as one
JSON snapshot. See `docs/integration-guide.md` for the full walkthrough.

- `POST /api/v1/exports` — generate a new export immediately (no request body), requires `adm:exports:manage`
- `GET /api/v1/exports` — list every export for the tenant, requires `adm:exports:manage`
- `GET /api/v1/exports/{id}` — get one export's status/metadata, requires `adm:exports:manage`
- `GET /api/v1/exports/{id}/download` — download the raw JSON snapshot, requires `adm:exports:manage`. 409 if not `COMPLETED` (already `EXPIRED` or `REVOKED`).
- `POST /api/v1/exports/{id}/revoke` — delete the stored snapshot early, requires `adm:exports:manage`. 409 if not `COMPLETED`.

Exports generate synchronously — there is no approval step and no queued
generation, unlike CPMS's Super-Admin-gated workflow. Each export expires
automatically after `gen-adm.export-ttl-days` (default 7) the next time it
is read, at which point its snapshot content is cleared.
```

- [ ] **Step 4: Update docs/integration-guide.md**

In `docs/integration-guide.md`, append after the existing "## 9. Support Tickets" section:
```markdown

## 10. Data Export Jobs

No bean to register — this feature has no external system to decouple
from (it only reads Gen_ADM's own repositories and writes to Gen_ADM's own
`data_exports` table).

State machine: `COMPLETED → EXPIRED` (lazy, read-time, once
`gen-adm.export-ttl-days` — default 7 — has elapsed) or `COMPLETED →
REVOKED` (explicit action). Both are terminal. Unlike CPMS's TNT-SVC-backed
export, there is no `PENDING_APPROVAL`/`APPROVED`/`REJECTED`/`FAILED` —
generation is synchronous and returns `COMPLETED` immediately.

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/v1/exports` | — | Builds and stores the snapshot inline. Requires `adm:exports:manage`. |
| GET | `/api/v1/exports` | — | Lists every export in the tenant, any status. Requires `adm:exports:manage`. |
| GET | `/api/v1/exports/{id}` | — | Status/metadata only — no snapshot content. Requires `adm:exports:manage`. |
| GET | `/api/v1/exports/{id}/download` | — | Raw JSON snapshot. 409 if not `COMPLETED`. Requires `adm:exports:manage`. |
| POST | `/api/v1/exports/{id}/revoke` | — | Clears the snapshot early. 409 if not `COMPLETED`. Requires `adm:exports:manage`. |

The snapshot itself reuses each feature's existing REST response shape
(`RoleResponse`, `AssignmentResponse`, `OffboardingJobResponse`,
`ImpersonationSessionResponse`, `InvitationResponse`, `SupportTicketResponse`)
assembled under one JSON document — see
`docs/superpowers/specs/2026-07-25-gen-adm-data-export-design.md` for the
full design rationale, including why there is no approval workflow, no
storage abstraction, and no event-publisher port for this feature.
```

- [ ] **Step 5: Run the full build**

Run: `./gradlew build`
Expected: PASS — full build including all modules and tests.

- [ ] **Step 6: Commit**

```bash
git add gen-adm-demo/src/main/resources/application.yaml scripts/smoke-test.sh README.md docs/integration-guide.md
git commit -m "docs: wire data export jobs into gen-adm-demo, smoke test, README, and integration guide"
```

---

## Final review

After all five tasks are complete and committed, do one whole-branch review pass (not per-task): re-read `DataExportServiceImpl` alongside `docs/superpowers/specs/2026-07-25-gen-adm-data-export-design.md` and confirm every guard (download/revoke require `COMPLETED`, lazy expiry clears `snapshotJson` and flips status, cross-tenant 404, single-permission gating on all five endpoints, no event-publisher port, no approval workflow) is actually exercised by at least one test, and that `README.md`/`docs/integration-guide.md` describe the shipped behavior exactly, not an earlier draft of it.
