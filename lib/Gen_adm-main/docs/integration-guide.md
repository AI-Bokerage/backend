# Gen_ADM Integration Guide

Tenant-scoped RBAC as a Spring Boot library. Add `gen-adm-starter` as a
dependency and `GenAdmAutoConfiguration` wires everything in automatically.

## 1. Embedding

Add the dependency (from GitHub Packages — see this repo's root
`build.gradle`/`settings.gradle` for the exact coordinates):

```groovy
implementation 'com.example:gen-adm-starter:0.1.0-SNAPSHOT'
```

Gen_ADM requires:
- A PostgreSQL datasource (`spring.datasource.*`) — it runs its own Flyway
  migrations from `classpath:db/migration/genadm`, independent of any
  migrations your own app runs.
- `gen-adm.permissions` in your config — Gen_ADM ships with **no built-in
  permissions**; you supply your own catalog:

```yaml
gen-adm:
  permissions:
    - code: adm:roles:manage
      description: Create/edit roles and manage permission grants
    - code: users:read
      description: Read user data
```

- Your own `Authentication#getPrincipal()` implementing
  `com.example.admsvc.domain.port.GenAdmPrincipal` (`tenantId()`,
  `userId()`) — Gen_ADM never issues or verifies tokens itself; it reads
  whatever principal your own auth layer (e.g. Gen_AUTH) already populated
  in `SecurityContextHolder`.

## 2. Bootstrapping a tenant's first role

There is no HTTP route for this — call it directly, in-process, from your
own tenant-provisioning flow:

```java
UserRoleAssignmentEntity assignment = userRoleAssignmentService.bootstrapTenant(
    tenantId, firstUserId, "owner", Set.of("adm:roles:manage"));
```

A second call for a tenant that already has roles throws
`GenAdmConflictException` — this path is for first-role creation only.

## 3. HTTP API reference

All routes require an already-resolved `GenAdmPrincipal` and (except
bootstrap, which has no route) a `PermissionChecker.require(...)` check —
by default every mutating endpoint requires `adm:roles:manage`.

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/v1/roles` | `{name, description}` | Creates a role |
| GET | `/api/v1/roles` | — | Lists roles for the caller's tenant |
| GET | `/api/v1/roles/{id}` | — | 404 if not found or belongs to another tenant |
| DELETE | `/api/v1/roles/{id}` | — | |
| PUT | `/api/v1/roles/{id}/permissions` | `{permissionCodes: [...]}` | Replaces the role's permission grants |
| GET | `/api/v1/permissions` | — | Lists the consumer-supplied global catalog |
| POST | `/api/v1/assignments` | `{userId, roleId}` | 409 if the user already holds that role |
| DELETE | `/api/v1/assignments?userId=&roleId=` | — | |
| GET | `/api/v1/assignments?userId=` | — | |

## 4. Error reference

| Code | HTTP status | Meaning |
|---|---|---|
| `NOT_FOUND` | 404 | Resource doesn't exist, or belongs to another tenant |
| `CONFLICT` | 409 | Duplicate role name, duplicate assignment, or bootstrap called on an already-provisioned tenant |
| `VALIDATION_ERROR` | 400 | Unknown permission code, missing required field |
| `FORBIDDEN` | 403 | Caller's principal lacks the required permission |
| `CONFIG_ERROR` | 500 | No resolvable tenant context (misconfigured caller, not the caller's fault) |

## 5. Local smoke test

```bash
cd gen-adm-demo
cp .env.example .env
# fill in ADM_DB_URL/ADM_DB_USERNAME/ADM_DB_PASSWORD
../gradlew bootRun &
sleep 5
../scripts/smoke-test.sh
```

See `docs/superpowers/specs/2026-07-23-gen-adm-rbac-design.md` for the full
design rationale.

## 6. Offboarding Saga

Register a `SessionRevocationGateway` bean — this is mandatory, startup fails
without one:

```java
@Component
public class MySessionRevocationGateway implements SessionRevocationGateway {
    @Override
    public void revoke(UUID tenantId, UUID userId) {
        // call your own auth service's session-revocation endpoint
    }
}
```

Optionally register zero or more `OffboardingStepHandler` beans for anything
else that should happen on offboarding (task reassignment, ticket
reassignment, etc. — Gen_ADM ships none built in):

```java
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
```

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
