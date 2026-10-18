# Phase B — CPMS-Platform auth-svc Migration Scoping

Goal: replace CPMS-Platform's real, live `auth-svc` (`apps/auth-svc`) with `gen-auth-starter`.
Started once all 7 gap.md parity items were closed (see `gap.md`). CPMS-Platform is a real
sibling repo at `C:\Users\naksh\Desktop\Metaupspace\CPMS-Platform` — read-only reference until a
sub-project below is actually authorized to touch it.

## Scale (survey, 2026-09-01)

auth-svc: 209 Java files, ~13,560 LOC, Spring Boot 4.0.6, 10 controllers. Same shared Neon
Postgres instance as adm-svc/tnt-svc (direct cross-service JDBC reads, not HTTP). Shared Redis,
shared RabbitMQ. 10 real `@RabbitListener` consumers (all from adm-svc/tnt-svc/cpt-svc-via-adm).
5 real HTTP dependents: adm-svc (revoke-sessions), bsm-svc + crm-svc + sup-svc (service-token),
fin-svc (OTP), sup-svc (impersonation-token). No auth-svc-specific docker-compose/Helm values;
Dockerfile builds from repo root (monorepo Gradle multi-module).

## Sub-projects

| # | Sub-project | Status | Notes |
|---|---|---|---|
| A | Packaging/wiring decision | ✅ RESOLVED (2026-09-01) | No change — gen-auth-starter stays a real Spring Boot autoconfigured starter. See below. |
| E | Feature-parity check | ✅ RESOLVED (2026-09-01) | Clean — see below. |
| B | Data-model reconciliation | ✅ RESOLVED (2026-09-01) | `TenantSlugResolver.resolveName`/`UserDisplayNameResolver` SPI + 12-arg `JwtClaims` wired into all 3 real call sites. See below. |
| C | Messaging cutover | Not started | 10 consumers rewritten in CPMS's own layer (gap.md §9), outbox wired to real `cpms.events`/`cpms.audit`/`cpms.platform.audit` exchange names via `io.cpms.common.messaging`. Depends on A (done). |
| D | HTTP-surface compatibility | Not started | Verify/adapt request+response payloads (not just paths) for the 5 real dependent services. |
| F | Cutover/deployment strategy | Not started | How a live service with 5 dependents and shared DB/broker actually gets swapped. Depends on B, C, D. |

## A — Packaging/wiring decision

**Question**: does gen-auth-starter get consumed as a Spring Boot autoconfigured starter
(its current design), or restructured to CPMS's own `packages/gen-fin` precedent — plain
modules, hand-wired via a local `@Configuration` class?

**Finding**: `packages/gen-fin`'s manual-wiring style (`fin-svc`'s `GenFinConfig`, a hand-built
`ExtensionRegistry`) exists because gen-fin is a *deliberately framework-agnostic, plain-Java*
business-logic library — no Spring dependency at all, and fin-svc explicitly wanted fine-grained
control over which calculators to adopt while keeping its own persistence model (it even has an
unused Spring Boot starter module for gen-fin it chose not to use). That's a domain-specific
choice for gen-fin, not a CPMS-wide convention against Spring Boot starters — every other CPMS
service already consumes ordinary Spring Boot starters. gen-auth-starter is inherently
Spring-Security-coupled (JWT filters, JPA repositories, RabbitMQ listeners) and is meant to
replace auth-svc's *every* layer wholesale, unlike fin-svc's partial adoption of gen-fin.

Verified gen-auth-starter already ships real autoconfiguration
(`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`), and
`gen-auth-demo` already consumes it as a plain `implementation project(':gen-auth-starter')`
dependency with zero manual wiring beyond overriding SPI beans (e.g. `TenantSlugResolver`) —
which is normal Spring bean-override behavior, not a gen-fin-style bridge.

**Decision**: keep gen-auth-starter as a genuine Spring Boot starter dependency. No bridge/config
class needed for Phase B.

## E — Feature-parity check

Diffed all 10 real auth-svc controllers against gen-auth-starter's controllers (paths verified
byte-for-byte, not just class names):

| auth-svc controller | gen-auth-starter equivalent | Path match |
|---|---|---|
| AuthController | AuthController | — |
| ClientTokenController (`/v1/client-token`) | V1InternalTokenController | ✅ exact |
| ImpersonationTokenController (`/v1/impersonation-token`) | V1InternalTokenController | ✅ exact |
| InternalOtpController (`/internal/otp/*`) | OtpController | ✅ exact |
| InternalUserController (`/internal/auth/users/*`) | InternalUserController | ✅ exact |
| MagicLinkController (`/api/v1/auth/magic-link/*`) | MagicLinkController | ✅ exact |
| ServiceTokenController (`/internal/service-token`) | ServiceTokenController | ✅ exact |
| SignoffTokenController | *(none)* | Deliberate — resolved as CPMS-side integration code, gap.md item 2b |
| SuperAdminController (`/api/v1/auth/super-admin/*`) | SuperAdminController | ✅ exact, all 3 endpoints |
| JwksController (`/.well-known/jwks.json`) | JwksController | ✅ |

Also confirmed present in gen-auth-starter (not just controller-level): `AwsKmsJwtSigner` (KMS
JWT signing), Redis-backed stores for refresh tokens, permission cache, MFA challenges, OTP,
magic links — all real, not gaps. auth-svc's SAML2 dependency (`build.gradle`) is confirmed dead
— zero usage anywhere in its Java source, pure unused scaffolding for a "future SSO" that was
never built.

**Finding**: feature parity at the controller/capability level is clean. The only real gap
(signoff-token) was already decided in gap.md. Remaining risk for Phase B lives entirely in
payload-level compatibility (sub-project D) and data-model reconciliation (sub-project B), not
in missing features.

## B — Data-model reconciliation

Closed CPMS's need for `username`/`userEmail`/`tenantName` claims in the JWT (CPMS's real
`auth-svc` reads a display name and tenant name it has no equivalent SPI for today) across 3
tasks:

- **Task 1**: added `TenantSlugResolver.resolveName(UUID) -> String` and a new
  `UserDisplayNameResolver.resolve(UUID) -> String` SPI
  (`com.example.authsvc.infrastructure.security.jwt`) — both implemented CPMS-side, not by
  gen-auth-starter itself, same pattern as the existing `TenantSlugResolver.resolve`.
- **Task 2**: extended `JwtClaims` to a 12-arg record (`username`, `userEmail`, `tenantName`
  trailing fields) and wired `JwtUtils` to serialize/parse them, deliberately leaving the 3 real
  call sites non-compiling until Task 3.
- **Task 3**: wired the new resolvers into the 3 real `new JwtClaims(...)` construction points —
  `LoginExecutionServiceImpl`, `RefreshTokenServiceImpl` (1 construction point each, despite
  each having multiple `publishLogout`/`publishAuditLogout` event-call branches), and
  `ImpersonationTokenServiceImpl` (which needed a new `AuthUserJpaRepository` dependency added,
  since it never loaded an `AuthUserEntity` before — the acting super admin's own email now
  needs a lookup, not the impersonated tenant's).

Full `gen-auth-starter` test suite is green: 229 tests, 0 failures, 0 errors, 3 skipped,
`BUILD SUCCESSFUL`. No other manual construction of the 3 modified services exists outside
Spring's own DI (verified via repo-wide grep) — no config/wiring classes needed updating.
