# Phase B Sub-Project B: Data-Model Reconciliation — Design

**Status**: design, pending user review.

**Context**: Phase B (see `phase-b.md`) is the migration of CPMS-Platform's real `auth-svc` to
`gen-auth-starter`. Sub-projects A (packaging) and E (feature-parity) are resolved. This is
sub-project B — the highest-risk piece, since it touches how the migrated service persists and
enriches its core identity data, and gap.md §9 explicitly left it unresolved.

## Background

auth-svc does live, direct JDBC reads against tables owned by other CPMS services, all sharing
one physical Neon Postgres instance:

- `TenantSlugResolver.resolve`/`resolveName` — reads `tenants` (owned by tnt-svc) to embed
  `tenant_slug`/`tenant_name` claims into the JWT at login/refresh/impersonation time.
- `UserDisplayNameResolver.resolve` — reads `internal_users` falling back to `client_users`
  (both owned by adm-svc) to embed a `username` (display name) claim, same call sites.
- `AuthReconciliationService` — a dormant `ApplicationRunner` (`auth.reconciliation.enabled=true`)
  that joins `internal_users`+`user_roles` (adm-svc) against local `auth_users`/`auth_user_roles`
  to backfill gaps from missed events.

**Decision (user-confirmed, 2026-09-01)**: the migrated service keeps sharing CPMS's one physical
Postgres instance — no isolated database for this migration.

**Schema-identity finding**: gen-auth-starter's `V1__init.sql`/`V2__auth_user_roles.sql` are
already structural copies of CPMS's real `V1__init.sql`/`V2__auth_user_roles.sql` — same columns,
types, constraints, indexes for `auth_users`, `auth_sessions`, `auth_refresh_tokens`,
`auth_audit_logs`, `auth_login_attempts`, `platform_super_admin`, `auth_user_roles`. Combined with
the shared-instance decision above: pointing the migrated service's datasource at the same
physical database makes gen-auth-starter's `CREATE TABLE IF NOT EXISTS` migrations no-ops against
the existing tables. **No data migration is needed for these tables.**

This reduces "data-model reconciliation" to three narrower, independent problems:

1. Two SPI gaps: `TenantSlugResolver` is missing `resolveName`, and there is no
   `UserDisplayNameResolver`-equivalent SPI at all.
2. `JwtClaims` and the JWT issuance pipeline don't support `username`/`userEmail`/`tenantName`
   claims at all currently — this is new capability, not a currently-unused hook.
3. `AuthReconciliationService`'s fate — confirmed to need **no new library extension point**: it
   only calls gen-auth-starter's already-public `AuthUserJpaRepository`/`AuthUserRoleJpaRepository`/
   `PasswordHasher` beans, so it is rebuilt near-verbatim in CPMS-Platform's own layer at Phase B
   implementation time (same pattern as signoff-token, gap.md item 2b).

## Design

### 1. New SPI surface

- `TenantSlugResolver` (`infrastructure/security/jwt/TenantSlugResolver.java`) gains:
  ```java
  String resolveName(UUID tenantId);
  ```
  `PlatformOnlyTenantSlugResolver` implements it: returns `"Platform"` for
  `TenantConstants.PLATFORM_TENANT_ID`, `""` otherwise — mirrors `resolve()`'s existing pattern.

- New interface `UserDisplayNameResolver` (same package):
  ```java
  public interface UserDisplayNameResolver {
      String resolve(UUID userId);
  }
  ```
  New default implementation `NoOpUserDisplayNameResolver` — `@Component
  @ConditionalOnMissingBean(UserDisplayNameResolver.class)`, always returns `""`. A host app
  supplies its own JDBC-backed (or any other) implementation to override it — same override
  mechanism already established for `TenantSlugResolver` this session. CPMS's own real
  implementation (querying `internal_users` falling back to `client_users`) is written in
  CPMS-Platform's own layer at Phase B implementation time — it never becomes gen-auth-starter
  code, since it's coupled to adm-svc's schema.

### 2. `JwtClaims` + JWT issuance pipeline

`domain/model/JwtClaims.java` gains 3 nullable fields:

```java
public record JwtClaims(
        UUID        userId,
        UUID        tenantId,
        String      tenantSlug,
        List<UUID>  roleIds,
        UserType    userType,
        Instant     issuedAt,
        Instant     expiresAt,
        String      sessionId,
        String      jti,
        String      username,
        String      userEmail,
        String      tenantName
) { ... }
```

`infrastructure/security/jwt/util/JwtUtils.java`:
- `generateAccessToken`: after the existing `session_id` line, add (mirroring the existing
  `tenant_id`/`role_id`/`user_type` not-null-guard style, and CPMS's exact wire key names for
  compatibility with anything already decoding auth-svc JWTs):
  ```java
  if (claims.username()   != null && !claims.username().isBlank())   payload.put("username",    claims.username());
  if (claims.userEmail()  != null && !claims.userEmail().isBlank())  payload.put("user_email",  claims.userEmail());
  if (claims.tenantName() != null && !claims.tenantName().isBlank()) payload.put("tenant_name", claims.tenantName());
  ```
- `extractClaims`: read them back with `c.get("username", String.class)` /
  `c.get("user_email", String.class)` / `c.get("tenant_name", String.class)`.
- `generateTokenPair(JwtClaims, Instant)`: the intermediate `timedClaims` reconstruction (line
  ~98) currently drops any field not explicitly threaded through — **must** carry
  `baseClaims.username()`, `baseClaims.userEmail()`, `baseClaims.tenantName()` into the new
  `JwtClaims(...)` call, or these claims silently vanish from every token minted via this path
  (which is all of them — `generateAccessToken` is only ever called through `generateTokenPair`
  in this codebase's real call sites).

### 3. Wiring the 3 real call sites

Grepped CPMS-Platform's real usage: exactly 3 classes call these resolvers, always together
(`resolveName` + `resolve`), always at token-issuance time for a *human* session — never for
service-to-service or internal tokens:

- `LoginExecutionServiceImpl` — 1 construction point.
- `ImpersonationTokenServiceImpl` — 1 construction point.
- `RefreshTokenServiceImpl` — 4 construction points (its rotation-scenario branches).

Each adds two calls before constructing `JwtClaims`:
```java
String tenantName = tenantSlugResolver.resolveName(user.getTenantId());
String username    = userDisplayNameResolver.resolve(user.getId());
```
`userEmail` is read directly from the already-loaded `AuthUserEntity.getEmail()` — no resolver
call needed, since email lives in gen-auth-starter's own `auth_users` table already.

`ServiceTokenServiceImpl` and `ClientTokenServiceImpl` (service-to-service / internal tokens) are
**not** touched — CPMS's real code never populates these claims there either.

### 4. `AuthReconciliationService`

No gen-auth-starter code changes. Confirmed it depends only on already-public beans
(`AuthUserJpaRepository`, `AuthUserRoleJpaRepository`, `PasswordHasher`). CPMS-Platform rewrites
it in its own layer at Phase B implementation time, unchanged in shape from its real version
(idempotent `existsById` guard, `ON CONFLICT DO NOTHING` on `auth_user_roles`, dormant behind a
property flag, no emails sent).

## Testing

- `PlatformOnlyTenantSlugResolverTest`: extend with `resolveName` cases (platform sentinel →
  `"Platform"`, other tenant → `""`).
- New `NoOpUserDisplayNameResolverTest`: always returns `""`.
- `JwtUtilsTest` (or wherever JWT round-trip is tested today): a token built with all 3 new
  claims populated round-trips through `generateTokenPair` → `extractClaims` with all 3 values
  intact — this is the test that would have caught the `generateTokenPair` claim-dropping risk
  in section 2 if it existed only as a manual check.
- One test per wired call site (`LoginExecutionServiceImplTest`, `RefreshTokenServiceImplTest`,
  `ImpersonationTokenServiceImplTest`) asserting the two new resolvers are invoked and the
  resulting `JwtClaims` carries their return values.

## Out of scope (belongs to other sub-projects or Phase B implementation directly)

- Writing CPMS's actual `TenantSlugResolver`/`UserDisplayNameResolver` override beans (Phase B
  implementation, CPMS-Platform repo).
- Rebuilding `AuthReconciliationService` (Phase B implementation, CPMS-Platform repo).
- The 3 RabbitMQ consumers that populate `auth_user_roles` (`UserCreatedEvent`,
  `RoleAssignedEvent`, `RoleRemovedEvent`) — sub-project C.
- Verifying whether any other CPMS service actually decodes/relies on the `username`/`user_email`/
  `tenant_name` JWT claims today, beyond matching CPMS's own wire format — flagged for
  confirmation at Phase B implementation/cutover time (sub-project D or F), not blocking this
  design.
