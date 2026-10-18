# Gen_ADM Phase 1: RBAC Library — Design Spec

## Goal

Build Gen_ADM as a configurable Spring Boot library (starter + demo), providing
role-based access control (roles, permissions, user-role assignments) that any
multi-tenant SaaS platform can drop in — the same pattern already established
by Gen_AUTH, Gen_REG, Gen_TNT, and Gen_TBR in this Gen_MS family.

## Why

Source-audited from CPMS-Platform's `adm-svc` (see `docs/source-audit-notes.md`
in this repo). The decision matrix classified adm-svc as **PORT LOGIC ONLY**
(~45-55% effort vs. clean rebuild): its RBAC schema and permission-check shape
are genuinely portable, but its offboarding saga, client/user profile tables,
invitations, impersonation, support tickets, and export jobs are either
CPMS-specific or a separate concern. This spec covers RBAC only — everything
else is deferred to future sub-projects, decided when they're actually needed
(see Scope below).

## Scope

**In scope (this spec, Phase 1):**
- Role, Permission, UserRoleAssignment schema — tenant-scoped roles and
  assignments, a global permission-code catalog.
- Postgres Row-Level Security (RLS) enforcing tenant isolation at the DB layer
  — new ground for Gen_MS (no sibling library has this yet), chosen
  deliberately over the app-level-filtering-only pattern every sibling uses.
- A tenant-context interceptor that sets the `app.tenant_id` session GUC per
  transaction — built and tested as a first-class component specifically
  because `tbr-svc`'s equivalent RLS is silently dead (no code ever sets the
  GUC there); Gen_ADM's design makes that failure mode structurally hard to
  repeat.
- Programmatic permission checking (`PermissionChecker.require(...)`) — no
  AOP annotations, no `@PreAuthorize` SpEL integration.
- A `GenAdmPrincipal` port — Gen_ADM never imports a Gen_AUTH class directly;
  the consumer's `Authentication#getPrincipal()` implements this interface or
  is adapted to it.
- A programmatic-only tenant bootstrap path (`bootstrapTenant(...)`) with no
  HTTP route and no permission gate, solving the "first role in a fresh
  tenant" chicken-and-egg problem.
- REST controllers for role/permission/assignment CRUD.
- `gen-adm-demo` reference app + smoke test.

**Out of scope (explicitly deferred, not part of this spec):**
- Offboarding saga (retry-only in source; session-revocation gap noted).
- Owned user/client profile data (email, name, status) — Gen_ADM references
  external `(tenantId, userId)` pairs only.
- Invitations, impersonation requests, support tickets, data-export jobs,
  audit log, outbox/RabbitMQ event publishing.
- Annotation-based (`@RequiresPermission`) or Spring-Security-expression-based
  (`hasPermission(...)`) permission-check sugar — may be layered on top of the
  programmatic engine later without changing it.
- Built-in default role seeding — consumer defines all roles.

## Architecture

Gen_ADM Phase 1 ships as a Spring Boot autoconfiguration starter, matching
Gen_AUTH/Gen_TNT's exact convention:

- Gradle multi-module: `gen-adm-starter` (the library) + `gen-adm-demo` (thin
  reference Spring Boot app).
- `GenAdmAutoConfiguration` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,
  conditional beans, `@ConfigurationProperties` for toggles — same shape as
  `GenAuthAutoConfiguration`.
- Postgres + Flyway migrations, applied the same way `auth-svc`'s
  `FlywayConfig` does.

**Domain owned by Gen_ADM (thin references only — no user/client profile
data; that's Gen_AUTH's or the host app's job):**
- `Role` — tenant-scoped (`tenant_id`, `name`), unique per tenant.
- `Permission` — flat string `code` (e.g. `"users:read"`), global catalog,
  not tenant-scoped (the permission set is the same everywhere; only
  role→permission grants and role→user assignments are tenant-scoped).
- `UserRoleAssignment` — links an external `(tenantId, userId)` pair to a
  `Role`. No email/name/status columns.

**RLS:**
- Postgres RLS policies on `roles` and `user_role_assignments` (permissions
  stay global, no tenant column to police), filtering on
  `current_setting('app.tenant_id')::uuid`.
- A request/transaction-scoped interceptor sets the `app.tenant_id` session
  GUC from the authenticated principal before each transaction runs. This is
  the piece `tbr-svc` skipped — Gen_ADM treats it as a tested, first-class
  component, not an afterthought.

**Auth integration (programmatic permission checks, not annotations or
Spring Security expressions):**
- `GenAdmPrincipal` interface (`tenantId()`, `userId()`) — the consuming
  app's principal implements it, or the app registers a small
  `Function<Authentication, GenAdmPrincipal>` adapter bean. Gen_ADM never
  imports a Gen_AUTH class directly.
- `PermissionChecker.require(principal, "code")` — throws
  `GenAdmForbiddenException` (403) if the principal's roles don't grant that
  code; called explicitly by the consumer at the top of a controller/service
  method. `PermissionChecker.has(principal, "code"): boolean` for
  non-throwing checks.

## Components

**Persistence (`infrastructure/persistence`):**
- `RoleEntity` (`id`, `tenantId`, `name`, `description`, `systemRole` flag,
  `version`, timestamps) — `@Table(name="roles")`, unique `(tenant_id, name)`.
- `PermissionEntity` (`id`, `code` unique, `description`) — global, no
  tenant column.
- `role_permissions` join table (many-to-many, ported near-as-is from
  source's `RoleEntity`/`PermissionEntity`).
- `UserRoleAssignmentEntity` (`tenantId`, `userId`, `roleId`, `assignedAt`) —
  replaces source's `UserRoleEntity`; drops the `userType`/`clientUserId`/
  `InternalUserEntity` split entirely (CPMS-specific internal-vs-client-user
  concept, out of scope here).
- Flyway migrations: (1) create tables, (2) enable RLS + policies
  (`ALTER TABLE ... ENABLE ROW LEVEL SECURITY`,
  `CREATE POLICY ... USING (tenant_id = current_setting('app.tenant_id')::uuid)`).

**Domain/port (`domain/port`):**
- `GenAdmPrincipal` — see Architecture.
- `TenantContextSetter` — internal port wrapping the "set `app.tenant_id` for
  this transaction" mechanic. Implemented as a Spring AOP `@Around` aspect on
  Gen_ADM's own `@Transactional` service methods (`RoleService`,
  `UserRoleAssignmentService`, `PermissionChecker`) that runs
  `SET LOCAL app.tenant_id = ...` as the first statement inside the
  transaction — deliberately **not** a servlet `HandlerInterceptor`, because
  `bootstrapTenant` is called in-process (no HTTP request in flight) and a
  request-scoped interceptor would never fire for it. The aspect is the one
  mechanism that covers both the controller-triggered path and the
  programmatic bootstrap path uniformly.

**Application (`application/service` + `application/impl`):**
- `RoleService` — CRUD for roles per tenant; grant/revoke permission codes to
  a role.
- `UserRoleAssignmentService` — assign/revoke a role to a `(tenantId,
  userId)` pair; list a user's effective permission codes;
  `bootstrapTenant(tenantId, userId, roleName, permissionCodes)` — the
  programmatic-only, permission-check-free path for a fresh tenant's first
  role (see Data Flow).
- `PermissionChecker` — see Architecture.

**API (`api/controller`):**
- `RoleController` — `POST/GET/PATCH/DELETE /api/v1/roles`,
  `PUT /api/v1/roles/{id}/permissions` (replace grant set).
- `PermissionController` — `GET /api/v1/permissions` (list the global
  catalog — read-only over HTTP; permission codes are never created ad hoc by
  end users).

**Permission catalog population (no built-in permissions, per your
consumer-defines-everything decision):** the consuming app supplies its own
permission list via `GenAdmProperties.permissions` (codes + descriptions, set
in `application.yml` or a config bean); `GenAdmAutoConfiguration` idempotently
upserts them into the `permissions` table on startup. There is no migration
that hardcodes permission codes — the schema/migration only creates the empty
table and its constraints.
- `AssignmentController` — `POST/DELETE /api/v1/assignments`,
  `GET /api/v1/assignments?tenantId=&userId=` (effective roles+permissions
  for a user).
- Every mutating endpoint is gated by `PermissionChecker.require(...)` (e.g.
  managing roles itself requires `"adm:roles:manage"`) except the bootstrap
  path, which has no HTTP route at all.

## Data Flow & Error Handling

**Tenant bootstrap (the permission-check chicken-and-egg problem):**
A fresh tenant has zero roles, so the HTTP `RoleController` (gated by
`"adm:roles:manage"`) can't create the first one. Gen_ADM exposes a separate
**programmatic-only** method —
`UserRoleAssignmentService.bootstrapTenant(tenantId, userId, roleName,
permissionCodes)` — with no HTTP route and no permission check, meant to be
called in-process by the host app's own tenant-provisioning flow (the same
place a Gen_TNT provisioning hook or the app's own signup completion already
runs). This mirrors Gen_REG's `createGenReg()` in-process composition instead
of a service-to-service call. A tenant that already has at least one role
cannot use this path for a second "first" role — `bootstrapTenant` checks for
zero existing roles and throws `GenAdmConflictException` otherwise, so it
can't be reused as a privilege-escalation shortcut.

**RLS + missing tenant context — fail loud, not fail-silent-empty:**
The transaction interceptor requires `principal.tenantId()` to be non-null
before setting `SET LOCAL app.tenant_id`. If a principal with no tenant ID
reaches a Gen_ADM call, the interceptor throws `GenAdmConfigException`
immediately rather than letting RLS silently return zero rows (which would
look like "not found" instead of "misconfigured caller").

**Cross-tenant access:**
RLS makes a role/assignment from another tenant simply not exist in query
results for the current session — the service layer surfaces this as a plain
404 (`GenAdmNotFoundException`), never a 403, so callers can't distinguish
"wrong tenant" from "never existed" (standard anti-enumeration practice).

**Error taxonomy** (mirrors Gen_REG's `AppError`→`errorHandler` shape,
Java-flavored): a `GenAdmException` base + `@ControllerAdvice`, with:
- `GenAdmNotFoundException` (404)
- `GenAdmConflictException` (409 — e.g. duplicate role name in a tenant, or
  a `bootstrapTenant` call against a tenant that already has roles)
- `GenAdmValidationException` (400)
- `GenAdmForbiddenException` (403)
- `GenAdmConfigException` (500 — misconfigured caller/principal)

## Testing

- **Unit tests** (Mockito): `RoleService`, `UserRoleAssignmentService`,
  `PermissionChecker` — role CRUD, grant/revoke, permission resolution,
  forbidden-exception paths, all against mocked repositories.
- **RLS integration tests — the highest-value tests in this plan**, guarding
  directly against `tbr-svc`'s dead-RLS failure mode: real Postgres via
  Testcontainers, seed two tenants' worth of roles/assignments, assert that a
  session scoped to tenant A's `app.tenant_id` genuinely cannot read/write
  tenant B's rows — not just "the policy exists," but "the policy actually
  blocks a real query." Also test the interceptor itself: a missing/null
  tenant ID on the principal throws `GenAdmConfigException` before any query
  runs.
- **Controller/API tests** (`MockMvc` or `@SpringBootTest` +
  `TestRestTemplate`): each endpoint's happy path, 403 when the calling
  principal lacks the permission, 404 for cross-tenant lookups, 409 for
  duplicate role names.
- **Bootstrap-path test**: `bootstrapTenant(...)` succeeds with zero prior
  roles and has no permission-check gate; a second call for a tenant that
  already has roles throws `GenAdmConflictException` — no accidental
  privilege-escalation reuse of the bootstrap path.
- **Demo app** (`gen-adm-demo`): a minimal Spring Boot app wiring
  `GenAdmAutoConfiguration`, providing a trivial `GenAdmPrincipal` adapter
  (e.g. from a request header for local testing), with a smoke script hitting
  bootstrap → create role → assign → check-permission end to end.

## Delivery Approach

One spec (this document), one implementation plan broken into many small
tasks (each a fresh implementer subagent + task-scoped review, per
Subagent-Driven Development), and **one** final whole-branch review at the
end — not a review per task-group. This keeps individual diffs small without
paying for repeated brainstorming/spec/final-review overhead across multiple
sub-projects.
