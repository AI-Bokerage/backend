# Gen_ADM Impersonation Requests — Design

## Why

`impersonation requests` was explicitly deferred in the RBAC design spec
([[2026-07-23-gen-adm-rbac-design]]) as CPMS-specific: in CPMS-Platform's
`adm-svc`, impersonation is a **stateless proxy** — ADM-SVC has no
`impersonation_sessions` table of its own; it forwards reads and consent
decisions to a separate SUP-SVC, which owns the actual session state machine
(see `docs/source-audit-notes.md` and
`pre-context/adm-svc/src/main/java/com/example/admsvc/application/impl/ImpersonationRequestServiceImpl.java`).

Gen_ADM has no SUP-SVC equivalent, and no cross-tenant "superadmin" identity —
`GenAdmPrincipal` (`gen-adm-starter/.../domain/port/GenAdmPrincipal.java`) is
always scoped to exactly one tenant. Offboarding already established the
precedent for handling this gap: instead of proxying to CPMS's external
PMT-SVC/SDS-SVC, Gen_ADM owns the offboarding job/step tables itself
(`OffboardingJobEntity`, `OffboardingStepEntity`, RLS via
`V3__create_offboarding_tables.sql` / `V4__enable_offboarding_rls.sql`). This
spec applies the same move to impersonation: Gen_ADM owns the full session
lifecycle, and impersonation is **intra-tenant** — a privileged user requests
to act as another user in the *same* tenant, and a second privileged user
(not the requester) grants or denies consent.

## Scope

**In scope:**
- `ImpersonationSessionEntity` — own table, tenant-scoped RLS (same pattern
  as offboarding).
- Full lifecycle: request → consent → active session → end, all within one
  tenant.
- Lazy, read-time expiry (no scheduler).
- Self-approval guard (reviewer ≠ requester).
- Self-end (the impersonator can end their own session) or admin force-end.
- Optional `ImpersonationEventPublisher` port (mirrors
  `OffboardingEventPublisher`), no-op default.
- Two new permission codes, wired into `gen-adm-demo` + smoke test + docs.

**Out of scope (explicitly deferred):**
- Cross-tenant / platform-level superadmin identity — no such concept exists
  in Gen_ADM today and nothing else needs it yet; if a future consumer needs
  a superadmin impersonating across tenants, that's a separate spec that
  would extend `GenAdmPrincipal`.
- Actually minting/scoping a session token for "acting as" the target user
  (JWT issuance, permission substitution while impersonating). Gen_ADM only
  tracks *whether* an impersonation session is currently authorized and
  active — executing the impersonation (e.g. issuing a scoped JWT) is the
  consuming application's job, same way offboarding's actual step logic
  (task reassignment, etc.) is pluggable via `OffboardingStepHandler` rather
  than owned by Gen_ADM.
- A separate audit-log table — the session row itself (reviewed_by,
  reviewed_at, ended_by, ended_at) is the audit trail, matching how
  offboarding never introduced a standalone audit service either.

## Architecture

### Entity & state machine

New table `impersonation_sessions`:

```sql
CREATE TABLE impersonation_sessions (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    requested_by_user_id UUID NOT NULL,
    target_user_id       UUID NOT NULL,
    reason               VARCHAR(500) NOT NULL,
    status               VARCHAR(30) NOT NULL,
    reviewed_by_user_id  UUID,
    reviewed_at          TIMESTAMPTZ,
    ended_by_user_id     UUID,
    ended_at             TIMESTAMPTZ,
    expires_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_impersonation_sessions_tenant_status
    ON impersonation_sessions(tenant_id, status);
```

RLS enabled/forced with the same `tenant_id = current_setting('app.tenant_id', true)::uuid`
policy used for `offboarding_jobs`/`offboarding_steps`.

States:

```
PENDING_CONSENT --approve--> ACTIVE --end (self or admin)--> ENDED
PENDING_CONSENT --reject---> DENIED
PENDING_CONSENT --expiresAt passed--> EXPIRED   (lazy check)
ACTIVE          --expiresAt passed--> EXPIRED   (lazy check)
```

A single `expires_at`, set at creation as `createdAt + ttlMinutes`, covers
both "must be approved by" (while PENDING_CONSENT) and "session must end by"
(while ACTIVE) — no separate consent-deadline field.

Creation guard: `requestedByUserId != targetUserId` (can't request to
impersonate yourself) — `IllegalArgumentException` → 400.

Lazy expiry: every read/mutate path (`listActionable`, `approve`, `reject`,
`end`) first checks, per row touched, whether `status IN (PENDING_CONSENT,
ACTIVE) AND now() > expires_at`; if so it flips the row to `EXPIRED` and
persists that before applying the requested action. An `approve`/`reject`/
`end` call against an already-expired row therefore fails with 409 (wrong
state), not a stale success.

### Permissions & service API

Two permission codes (same naming convention as `adm:offboarding:manage`):

- `adm:impersonation:request` — create a session.
- `adm:impersonation:manage` — list, approve, reject, force-end.

`ImpersonationSessionService`:

```java
ImpersonationSession create(UUID tenantId, UUID requestedByUserId,
        UUID targetUserId, String reason, int ttlMinutes);

List<ImpersonationSession> listActionable(UUID tenantId);
// PENDING_CONSENT + ACTIVE only, tenant-scoped, lazily expires stale rows first

ImpersonationSession approve(UUID tenantId, UUID sessionId, UUID reviewerId);
// 409 if not PENDING_CONSENT (after lazy-expiry check)
// 403 if reviewerId == requestedByUserId (self-approval guard)

ImpersonationSession reject(UUID tenantId, UUID sessionId, UUID reviewerId);
// same guards as approve

ImpersonationSession end(UUID tenantId, UUID sessionId, UUID actorId, boolean isPrivileged);
// 409 if not ACTIVE (after lazy-expiry check)
// 403 if !isPrivileged && actorId != requestedByUserId
```

`isPrivileged` is computed by the controller (`permissionChecker.has(principal,
MANAGE_IMPERSONATION)`) and passed through — the service doesn't call
`PermissionChecker` itself, matching `OffboardingServiceImpl`'s existing
separation (controller owns permission checks, service owns state
transitions).

### REST controller + DTOs

`ImpersonationSessionController` at `/api/v1/impersonation-requests` (URL
kept from the CPMS controller name; the entity itself is a "session"):

| Method | Path | Permission |
|---|---|---|
| POST | `/` | `adm:impersonation:request` |
| GET | `/` | `adm:impersonation:manage` |
| POST | `/{id}/approve` | `adm:impersonation:manage` |
| POST | `/{id}/reject` | `adm:impersonation:manage` |
| POST | `/{id}/end` | none if caller is `requestedByUserId`, else `adm:impersonation:manage` |

Request DTO `CreateImpersonationRequest`: `targetUserId`, `reason`,
`ttlMinutes` (validated: `1 <= ttlMinutes <= 480`, i.e. up to 8 hours).

Response DTO `ImpersonationSessionResponse`: `id`, `tenantId`,
`requestedByUserId`, `targetUserId`, `reason`, `status`,
`reviewedByUserId`, `reviewedAt`, `endedByUserId`, `endedAt`, `expiresAt`,
`createdAt`.

### Event publisher port

Mirrors `OffboardingEventPublisher`:

```java
public interface ImpersonationEventPublisher {
    void onGranted(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId);
    void onDenied(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId);
    void onEnded(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId);
}
```

Default `NoOpImpersonationEventPublisher`, same shape as
`NoOpOffboardingEventPublisher`. Synchronous hook only — no outbox, no retry.

## Testing

- `ImpersonationSessionServiceImplTest` — unit test per transition (create,
  approve, reject, end, self-approval rejection, self-impersonation
  rejection, lazy-expiry-then-409).
- `ImpersonationSessionControllerTest` — permission gating per endpoint,
  including the self-end-without-permission path.
- `ImpersonationIntegrationTest` — mirrors `OffboardingIntegrationTest`'s
  shape: real persistence, full request→approve→active→end flow and
  request→reject→denied flow, no mocked repository layer.

## Wiring (gen-adm-demo, docs)

Matches the two-commit shape used for offboarding
(`905f96e` feat, `d8a2a24` docs):
- Migrations `V5__create_impersonation_sessions_table.sql`,
  `V6__enable_impersonation_sessions_rls.sql` (next free versions after
  offboarding's `V3`/`V4`).
- Add `adm:impersonation:request` / `adm:impersonation:manage` to
  `gen-adm-demo`'s permission catalog and `scripts/smoke-test.sh`.
- `README.md` + `docs/integration-guide.md` sections describing the new
  endpoints, same structure as the existing offboarding sections.
