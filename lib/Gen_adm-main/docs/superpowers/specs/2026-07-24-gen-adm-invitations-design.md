# Gen_ADM Invitations — Design

## Why

`invitations` was explicitly deferred in the RBAC design spec
([[2026-07-23-gen-adm-rbac-design]]) as CPMS-specific: in CPMS-Platform's
`adm-svc`, an accepted invitation creates a full `internal_users` account
row directly (`pre-context/adm-svc/src/main/java/com/example/admsvc/application/impl/InvitationServiceImpl.java`,
`acceptInvitation`) and sends the invitation email itself via
`JavaMailSender`/SMTP. Neither fits Gen_ADM: Gen_ADM owns no user/profile
table (RBAC spec: "Gen_ADM references external `(tenantId, userId)` pairs
only") and has no mail dependency anywhere in the codebase today. This spec
adapts the invitation flow to those boundaries, following the same move
already made for Offboarding and Impersonation: Gen_ADM owns the invitation
record's full lifecycle, but the pieces that were CPMS-specific
infrastructure (user creation, email delivery) become either the caller's
responsibility (userId) or an optional pluggable port (delivery).

Accept-invitation is also Gen_ADM's first **public, no-principal** endpoint
— every other endpoint in the codebase assumes an authenticated
`GenAdmPrincipal`. There is already a precedent for a principal-free service
call: `UserRoleAssignmentService.bootstrapTenant(@TenantIdParam UUID
tenantId, ...)` takes `tenantId` explicitly because no principal is in scope
for tenant-bootstrap; `TenantContextAspect` reads that annotated parameter
directly instead of looking for a principal. `accept` reuses this exact
mechanism.

## Scope

**In scope:**
- `InvitationEntity` — own table. **Deliberately no RLS** (see Architecture
  below) — the only Gen_ADM table without it, for a specific, justified
  reason.
- One small, additive change to Phase 1's `UserRoleAssignmentService`: a new
  `assignRoleFromInvitation(@TenantIdParam UUID tenantId, UUID userId, UUID
  roleId)` method, alongside the existing `assignRole(...)` (unchanged). See
  Architecture below for why this is required, not optional.
- Full lifecycle: create → `PENDING`, `accept` (token-authenticated) →
  `ACCEPTED`, `cancel` (admin-initiated) → `CANCELLED`, lazy expiry →
  `EXPIRED`.
- One-time plaintext token, SHA-256 hashed at rest, returned only in the
  `create` response.
- Configurable expiry (`gen-adm.invitation-ttl-days`, default 7).
- Duplicate-invite guard: a second `create` for an email with an existing
  active `PENDING` invitation in the same tenant is rejected (409).
- Role validation at creation: every `roleId` must exist in the caller's
  tenant.
- Optional `InvitationEventPublisher.onCreated(...)` port, no-op default.
- One new permission code (`adm:invitations:manage`), wired into
  `gen-adm-demo` + smoke test + docs.

**Out of scope (explicitly deferred):**
- Sending the invitation email — Gen_ADM returns the plaintext token in the
  `create` response; a consumer's own `InvitationEventPublisher.onCreated`
  implementation is responsible for actually notifying the invitee (email,
  Slack, whatever). Gen_ADM adds no mail/SMTP dependency.
- Creating the actual user account/identity — `accept` takes a
  caller-supplied `userId`. The consumer's own signup/auth flow (e.g.
  Gen_AUTH) creates the real account for the invited email *before* calling
  Gen_ADM's `accept`, exactly the same boundary Phase 1 already draws for
  every other `(tenantId, userId)` pair Gen_ADM operates on.
- CPMS's role-eligibility checks for "deprecated" and "IMPERSONATION" roles
  — neither concept exists on Gen_ADM's `RoleEntity` (no `deprecated`
  field; Gen_ADM's impersonation feature is session-based, not a role
  flag). Only "role exists in this tenant" is validated.
- Pagination on list — nothing in Gen_ADM paginates today (no
  `PagedResponse`/`Pageable` usage anywhere in `gen-adm-starter`);
  `listActionable` returns a plain list, matching Offboarding/Impersonation.
- A separate audit-log table — the invitation row itself
  (`invitedBy`/`acceptedAt`/`cancelledAt`/`cancelledByUserId`) is the audit
  trail, matching the precedent set by offboarding and impersonation.

## Architecture

### Entity & state machine

New table `invitations`:

```sql
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

`role_ids` stores a JSON-serialized `List<UUID>` in a plain `TEXT` column
(matching CPMS's `MemberInvitationEntity.roleIdsJson` approach, via a
getter/setter pair that (de)serializes with Jackson) rather than a
relational join table — this field is written once at creation and read
once at accept, never queried by role, so a join table would add schema
and code for no real benefit.

**No RLS on this table — deliberate, not an oversight.** Every other
Gen_ADM table gets `ENABLE`/`FORCE ROW LEVEL SECURITY` with a
`tenant_id = current_setting('app.tenant_id', true)::uuid` policy. Applying
that same policy here would break `accept`: the lookup is *by token only*
(`findByTokenHash`), and tenant isn't known until after that lookup
returns — with RLS forced, `current_setting('app.tenant_id', true)` would
be `NULL` at that point (no principal, no prior context), so the policy
would filter out every row and a valid token would always 404. Postgres
has no cheap way to exempt one query from a forced policy — that requires
a second DB role with `BYPASSRLS`, which Gen_ADM's single-role setup
doesn't have — so RLS and a token-only lookup are fundamentally
incompatible here (CPMS never had to solve this: "No RLS anywhere in
adm-svc" per `docs/source-audit-notes.md`). Tenant isolation for this table
instead comes entirely from explicit `tenant_id` predicates in every
tenant-scoped query (`create`'s duplicate check, `listActionable`,
`cancel`) — the same defense-in-depth layer every other table already has
*in addition to* RLS; here it's the only layer, which is acceptable because
the sole cross-tenant access path (`accept`) is authorized by a
cryptographically unguessable 256-bit token, not by row visibility — the
identical trust model any invite-link or password-reset-token system
already relies on.

States:

```
PENDING --accept (token-authenticated)--> ACCEPTED
PENDING --cancel (adm:invitations:manage)--> CANCELLED
PENDING --expiresAt passed--> EXPIRED   (lazy, read-time check)
```

`expiresAt` is set once at creation as `createdAt + gen-adm.invitation-ttl-days`
(default 7, configurable — unlike CPMS's hardcoded value). Lazy expiry
follows the exact pattern established by
[[2026-07-24-gen-adm-impersonation-design]]: every read/mutate path
(`listActionable`, `accept`, `cancel`) checks whether a `PENDING` row's
`expiresAt` has passed and flips it to `EXPIRED` before applying the
requested action — no scheduler anywhere in this feature.

### Permissions & service API

One permission code: `adm:invitations:manage` — gates create, list, and
cancel. Unlike impersonation, there's no requester/reviewer split here:
inviting and cancelling are both ordinary tenant-admin actions performed by
the same actor. `accept` requires **no permission** — the one-time token
itself is the authentication mechanism, exactly as in CPMS.

`InvitationService`:

```java
InvitationEntity create(UUID tenantId, UUID invitedByUserId, String email, List<UUID> roleIds);
// 400 if any roleId doesn't exist in tenantId (GenAdmValidationException)
// 409 if an active PENDING invitation for this email already exists in this tenant
//     (after first lazily expiring any overdue PENDING row for that email)
// Generates a UUID plaintext token; only its SHA-256 hex hash is persisted.
// The plaintext token is carried on the returned entity as a transient
// (non-@Column) field, read once by the controller's create-response
// mapping — never persisted, never retrievable again after this call
// returns. Ordinary @Transactional method: tenantId resolved from the
// caller's principal, exactly like every other Gen_ADM write.

List<InvitationEntity> listActionable(UUID tenantId);
// PENDING only, tenant-scoped, lazily expires stale rows first. Ordinary
// @Transactional method, tenantId from principal.

InvitationEntity accept(String token, UUID userId);
// NOT @Transactional — see "Why accept() can't be @Transactional" below.
// 1. Looks up by SHA-256(token) via a plain repository call (no RLS on
//    this table, so no tenant context is needed for this step at all).
// 2. 404 (GenAdmNotFoundException) if no row matches the hash.
// 3. If PENDING and overdue, flips to EXPIRED (a plain write — again, no
//    RLS on this table, no tenant context needed) and throws
//    GenAdmConflictException. If not PENDING at all, same conflict.
// 4. Otherwise delegates to InvitationAcceptanceExecutor.completeAcceptance
//    (below) with the now-known tenantId.

InvitationEntity cancel(UUID tenantId, UUID invitationId, UUID cancelledByUserId);
// 409 if not PENDING (after lazy-expiry check). Ordinary @Transactional
// method, tenantId from principal.
```

### Why `accept()` can't be `@Transactional`, and the executor split

`TenantContextAspect` wraps every `@Transactional` method in
`com.example.admsvc.application.impl` and, *before* the method body runs,
resolves a tenant ID from either a `@TenantIdParam`-annotated argument or
the current `GenAdmPrincipal` — throwing `GenAdmConfigException` if neither
is present. `accept(String token, UUID userId)` has neither: no principal
(public endpoint) and no tenant ID in its parameters (that's the entire
point — the caller doesn't know it either). If `accept` itself were
`@Transactional`, the aspect would throw immediately, before the token
lookup that would have discovered the tenant ever ran.

The fix is the same one `OffboardingServiceImpl.initiate()` already uses
for a different reason (deferring step execution past commit): delegate to
a **separate `@Service` bean**, `InvitationAcceptanceExecutor`, whose method
carries `@TenantIdParam` — exactly `OffboardingStepExecutor.execute(@TenantIdParam
UUID tenantId, UUID jobId)`'s shape:

```java
@Transactional
InvitationEntity completeAcceptance(@TenantIdParam UUID tenantId, UUID invitationId,
                                     UUID userId, List<UUID> roleIds) {
    // assigns every roleId via userRoleAssignmentService.assignRoleFromInvitation(...),
    // then flips the invitation to ACCEPTED — one atomic transaction; a
    // mid-loop assignment conflict rolls back the whole thing.
}
```

`InvitationServiceImpl.accept()` calls this executor bean (a genuine
different-bean call, correctly intercepted by Spring's AOP proxy — this
is not a same-class self-invocation, which the proxy would silently skip)
only after the token lookup has resolved a real `tenantId`, satisfying the
aspect's precondition on that call.

**Why `UserRoleAssignmentService` needs one new method.** The existing
`assignRole(UUID tenantId, UUID userId, UUID roleId)` has no
`@TenantIdParam` — if `completeAcceptance` called it directly, that call is
*also* a separate-bean invocation the aspect intercepts independently, and
the aspect would try (and fail) to resolve tenant context for *that* call
using its own parameters/principal, ignoring that a GUC happens to already
be set by the outer `completeAcceptance` wrapping. So a second, minimal,
`@TenantIdParam`-carrying entry point is required:

```java
UserRoleAssignmentEntity assignRoleFromInvitation(@TenantIdParam UUID tenantId, UUID userId, UUID roleId);
```

This is additive only — `assignRole`'s existing signature and behavior are
untouched, and this mirrors `bootstrapTenant`'s already-established shape
(and CPMS's own naming for this exact scenario,
`assignRoleFromInvitation`). It is the one place this feature touches
Phase 1 code.

### REST controller + DTOs

`InvitationController` at `/api/v1/invitations` (no `/adm/` path segment —
matches every other Gen_ADM controller's convention, not CPMS's
multi-service gateway prefix):

| Method | Path | Permission |
|---|---|---|
| POST | `/` | `adm:invitations:manage` |
| GET | `/` | `adm:invitations:manage` |
| POST | `/{id}/cancel` | `adm:invitations:manage` |
| POST | `/{token}/accept` | none — public |

Request DTOs:
- `CreateInvitationRequest`: `email` (`@NotBlank @Email`), `roleIds`
  (`@NotEmpty List<UUID>`).
- `AcceptInvitationRequest`: `userId` (`@NotNull UUID`).

Response DTO `InvitationResponse`: `id`, `tenantId`, `email`, `status`,
`roleIds`, `invitedByUserId`, `expiresAt`, `acceptedAt`, `cancelledAt`,
`cancelledByUserId`, `createdAt`, and a nullable `token`. Two static
factories on the one record: `.forCreate(entity, plaintextToken)` (used
only by the `create` response, populates `token`) and `.from(entity)` (used
by list/cancel/accept responses, `token` always null) — avoids a second,
near-duplicate response type for the one field that differs.

### Event publisher port

Mirrors `OffboardingEventPublisher`/`ImpersonationEventPublisher`:

```java
public interface InvitationEventPublisher {
    void onCreated(UUID tenantId, UUID invitationId, String email, String plaintextToken);
}
```

Default `NoOpInvitationEventPublisher`. Synchronous hook only — no outbox,
no retry. A consumer registers its own bean to actually send the
invitation (email, Slack, etc.); Gen_ADM's `create` call itself does not
block on delivery succeeding or failing.

### Configuration

`GenAdmProperties.invitationTtlDays` (`int`, default `7`) — the only new
configuration surface this feature adds. Unlike `gen-adm.permissions`
(which has no default and must be supplied), this has a sane built-in
default and is overridable via `application.yaml` only if a consumer wants
something other than 7 days.

## Testing

- `InvitationServiceImplTest` — unit test per transition: create (role
  validation, duplicate-invite guard), accept's own pre-delegation logic
  (404 on unknown token, lazy-expiry-then-409, not-PENDING-then-409, and
  that a valid PENDING/unexpired token delegates to
  `InvitationAcceptanceExecutor.completeAcceptance` with the invitation's
  own `tenantId`), cancel + 409.
- `InvitationAcceptanceExecutorTest` — unit test for `completeAcceptance`:
  every `roleId` gets `assignRoleFromInvitation` called with the right
  `(tenantId, userId, roleId)`, the invitation flips to `ACCEPTED`, and a
  mid-loop assignment failure prevents the `ACCEPTED` flip (verifying the
  atomicity claim at the mock level — a real rollback needs the
  integration test below to confirm at the database level).
- `UserRoleAssignmentServiceImplTest` (existing file, extended) — one new
  test for `assignRoleFromInvitation`, confirming it performs the same
  assignment as `assignRole` (same conflict/not-found behavior) — the only
  difference is the `@TenantIdParam` annotation enabling the no-principal
  call path, which itself needs no unit-level behavior change to verify,
  only the aspect integration below.
- `InvitationControllerTest` — permission gating on create/list/cancel;
  confirms `accept` requires no `@AuthenticationPrincipal`/permission check
  at all.
- `InvitationIntegrationTest` — mirrors the shape of
  `ImpersonationIntegrationTest`: real persistence, full create→accept flow
  through the *actual* `InvitationServiceImpl`/`InvitationAcceptanceExecutor`
  pair (proving the cross-bean `@TenantIdParam` handoff really works against
  Postgres, not just mocks — asserting the resulting
  `UserRoleAssignmentEntity` rows actually exist), create→cancel flow,
  duplicate-invite 409, and an already-expired-invite 409 (built via a
  non-positive TTL passed directly to the service, same
  deterministic-without-sleeping trick used in the impersonation plan).

## Wiring (gen-adm-demo, docs)

Matches the shape used for offboarding and impersonation:
- One migration, `V7__create_invitations_table.sql` (next free version
  after impersonation's `V5`/`V6`) — no RLS migration, per the deliberate
  no-RLS decision above.
- Add `adm:invitations:manage` to `gen-adm-demo`'s permission catalog and
  `scripts/smoke-test.sh` (a full create → accept flow, using a fresh
  third user as the invitee's `userId`).
- `README.md` + `docs/integration-guide.md` sections describing the new
  endpoints, same structure as the existing offboarding/impersonation
  sections.
