# Design: Restoring Email, Magic-Link, Super-Admin/Impersonation, and RabbitMQ Events

Restores three subsystems that were cut from `gen-auth-starter` during its v1 scope trim (commit `6722879`, "trim gen-auth-starter to core-auth-only v1 scope"): email sending, magic-link password reset, super-admin login/impersonation, and RabbitMQ event publishing. All four are deferred features named explicitly as "restorable from git history" in `docs/superpowers/specs/2026-07-16-starter-library-design.md`'s scope table — this spec is that restoration.

Pre-trim reference commit: `8f2c5b7` (already in the post-module-split, auto-configuration-based layout — not the old standalone-service layout). Preserved permanently via tag `archive/starter-library-slice-pretrim` (on `8b4ac78`, an ancestor chain that includes `8f2c5b7`), since the branch it lived on was force-deleted after the starter-library squash-merge.

## Explicitly out of scope

**`ServiceTokenController`/`ServiceTokenService`** — issues short-lived `SUPER_ADMIN` JWTs keyed to an `X-CPMS-Service` header, hardcoded for CPMS sibling-service auth ("Used by SUP-SVC tenant-sync to authenticate against TNT-SVC's governance APIs"). This is CPMS-specific coupling, the same category as the 8 RabbitMQ consumers and `AuthBootstrapService`/`AuthReconciliationService` already permanently removed during genericization — not a generic starter feature. Stays out, remains available in git history (`8f2c5b7`) if ever needed as a reference.

## Activation model

Every restored subsystem is **optional and conditional** — off by default, zero cost when off (no required env vars, no connections attempted, no beans registered). This is a deliberate departure from how core auth (register/login/JWKS) works today, where required config is enforced fail-fast unconditionally. A client embedding `gen-auth-starter` who only wants core auth should never be asked for SMTP credentials, a RabbitMQ connection, or a super-admin password.

| Property | Default | Gates |
|---|---|---|
| `app.email.enabled` | `false` | Email sending (SMTP or SES) |
| `app.email.provider` | `smtp` | `smtp` \| `ses`, only read when email is enabled |
| `app.magic-link.enabled` | `false` | Magic-link password reset. **Requires `app.email.enabled=true`** — fails fast at boot otherwise, since a magic-link with no way to deliver it is a broken feature, not a partial one. |
| `app.super-admin.enabled` | `false` | Super-admin login, bootstrap account creation, impersonation tokens |
| `app.messaging.enabled` | `false` | RabbitMQ event publishing |
| `app.messaging.exchange` | `auth.events` | Configurable, only read when messaging is enabled |

Cross-dependency: super-admin's magic-link login variant (`SuperAdminMagicLinkServiceImpl`) additionally requires `app.email.enabled=true`, same rule as regular magic-link. Super-admin's impersonation start/end optionally publishes events if `app.messaging.enabled=true` — does not require messaging.

## Subsystem 1: Email + magic-link password reset

Restores: `EmailService`, `EmailProviderConfig`, `EmailProvider` (interface), `SmtpEmailProvider`, `SesEmailProvider`, `EmailHtmlTemplate`, `MailProperties`, `MagicLinkController`, `MagicLinkService`/`MagicLinkServiceImpl`, `MagicLinkStore`/`RedisMagicLinkStore`, `MagicLinkProperties`, `MagicLinkIssueRequest`/`Response`, `MagicLinkVerifyRequest`/`Response`, `MagicLinkInvalidException`.

- Both providers restored (SMTP and SES), selected via `app.email.provider`. `EmailProviderConfig` registers only the selected provider's bean.
- Magic-link is Redis-backed (`RedisMagicLinkStore`) — no new Flyway migration needed.
- `GlobalExceptionHandler` gets back its `MagicLinkInvalidException` handler.
- `spring-boot-starter-mail` and the AWS SES SDK module re-added to `gen-auth-starter/build.gradle` as `implementation` (not `runtimeOnly`, since `SesEmailProvider` needs the SDK types at compile time — check what the pre-trim `build.gradle` actually declared and match it).

## Subsystem 2: Super-admin login / impersonation

Restores: `SuperAdminController`, `SuperAdminLoginService`/`SuperAdminLoginServiceImpl`, `SuperAdminMagicLinkService`/`SuperAdminMagicLinkServiceImpl`, `ImpersonationTokenController`, `ImpersonationTokenServiceImpl`, `ImpersonationTokenRequest`/`Response`, `SuperAdminProperties`, `PlatformSuperAdminEntity`, `PlatformSuperAdminJpaRepository`, `BootstrapSuperAdminInitializer`.

- New Flyway migration in `gen-auth-starter/src/main/resources/db/migration/genauth/` for the `platform_super_admin` table (next version number after `V3__jwt_signing_keys.sql`, i.e. `V4__platform_super_admin.sql`) — runs through the starter's existing independent Flyway instance, only applied when the migration file is on the classpath. Since Flyway migrations aren't conditionally loadable by a Spring property (Flyway just runs whatever `.sql` files are on its configured location), the migration always runs — an empty, unused table when `app.super-admin.enabled=false` is an acceptable cost (matches how `jwt_signing_key` tables exist even in `kms` signing mode).
- `BootstrapSuperAdminInitializer` (currently an unconditional `ApplicationRunner`) becomes conditional — only registered as a bean when `app.super-admin.enabled=true`. `SUPER_ADMIN_EMAIL`/`SUPER_ADMIN_PASSWORD` become required env vars only in that case (fail fast if enabled but missing, same pattern as `JWT_KEY_ENCRYPTION_SECRET`).
- `DevDataSeeder` — check at implementation time whether this is super-admin-specific or a broader dev-only seeder; restore only the parts relevant to super-admin if it's mixed-purpose.
- Impersonation start/end events (`ImpersonationStartedEvent`/`EndedEvent`) publish through `AuthEventPublisher` only if `app.messaging.enabled=true` (see Subsystem 3) — impersonation itself works standalone without messaging.

## Subsystem 3: RabbitMQ event publishing

Restores: `AuthEventPublisher`, `AuthExchangeConstants`, `LoginSuccessEvent`, `LoginFailedEvent`, `LogoutEvent`, `PasswordChangedEvent`, `ImpersonationStartedEvent`, `ImpersonationEndedEvent`. Does **not** restore `AuthConsumerTopologyConstants` or any consumer — those existed only to react to sibling-service events and were permanently removed during genericization, independent of this restoration.

- `AuthExchangeConstants.EVENTS_EXCHANGE` was hardcoded `"cpms.events"` — replaced with `app.messaging.exchange` (default `auth.events`). Routing keys (`auth.login.success`, etc.) are already generic, kept as-is.
- `RabbitMqConfig` (or equivalent, restored/written fresh) declares only the exchange, no queues/bindings — those are the consuming application's concern, not the starter's, matching the "no shared central service" philosophy from the starter-library design.
- `spring-boot-starter-amqp` re-added to `gen-auth-starter/build.gradle` as `implementation`.
- `AuthEventPublisher` and its call sites (login/logout/password-change/impersonation flows) become conditional beans/no-ops when `app.messaging.enabled=false` — check the pre-trim call-site pattern (was publishing fire-and-forget via the existing `authAsync` executor) and preserve that async behavior.

## Testing & manual verification

- Unit tests for each restored class move back into `gen-auth-starter`'s test source set, adapted for the conditional-activation model (test both the enabled and disabled states — e.g. confirm no `AuthEventPublisher` bean exists when `app.messaging.enabled=false`).
- `docker-compose.yml` gains two new services for manual verification: **Mailhog** (SMTP capture, for email/magic-link) and **RabbitMQ** (for event publishing) — both on non-default host ports, following the existing Postgres-5433/Redis-6380 pattern to avoid native-service port collisions.
- `gen-auth-demo/src/main/resources/application.yaml` gets example config blocks for all three subsystems, commented out / `enabled: false` by default, so the demo continues booting with zero required new env vars unless a developer explicitly turns a subsystem on to test it.
- Manual verification convention: real Postgres/Redis/Mailhog/RabbitMQ (not mocks), same as every prior slice in this project — magic-link email actually captured in Mailhog's UI, super-admin bootstrap+login+impersonation exercised via real HTTP calls, RabbitMQ events actually consumed by a throwaway test consumer to confirm delivery.

## Delivery plan

One shared spec (this document), three independent implementation plans/branches, built in this order:

1. **Email + magic-link** (foundation — nothing else depends on it, but super-admin's magic-link variant depends on it existing)
2. **Super-admin / impersonation**
3. **RabbitMQ event publishing** (fully independent of 1 and 2, could be reordered first if preferred — placed last here only because it was named last)

Each gets its own worktree, its own `writing-plans` pass, and its own subagent-driven-development execution — same workflow as the JWKS rotation and starter-library slices.
