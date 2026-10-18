# Gen_AUTH

Standalone, generalized auth service — a genericized fork of CPMS-Platform's Java/Spring Boot `auth-svc`, not tied to PMP or any single project. Not a Node rewrite (an earlier attempt at that exists on the `login-refresh-slice` git branch, abandoned in favor of keeping the service in its original language and genericizing in place).

## Module layout

- `gen-auth-starter/` — the reusable auth library. A Spring Boot starter: add it as a dependency, get a working core-auth module (register/login/refresh/logout/session/change-password + JWKS with runtime key rotation) auto-configured into your own app, no shared code between deployments, no dependency back on this repo after you've pulled the artifact. Email sending (SMTP/SES) and magic-link password reset are restored as of the email+magic-link slice — both optional, off by default (`app.email.enabled` / `app.magic-link.enabled`; see Environment variables below). Super-admin login/bootstrap/magic-link reset and impersonation-token issuance are also restored — optional, off by default (`app.super-admin.enabled`; see Environment variables below). RabbitMQ event publishing is restored too — optional, off by default (`app.messaging.enabled`; see Environment variables below), and outbox-backed rather than fire-and-forget as of the outbox+audit-routing slice.
- `gen-auth-demo/` — a thin reference app (package `com.example.gendemo`, deliberately not `com.example.authsvc`) that depends on the starter. Run this the same way the old standalone service ran; it exists to prove the starter's auto-configuration genuinely works from outside its own package, and to double as a working example of what a consuming project's `build.gradle`/`application.yaml` need to look like.

## What this is

Login, refresh (rotation + replay detection via family/generation), logout, change-password, session lookup, JWKS (multi-key, with runtime rotate/retire in `local` signing mode), Argon2id hashing, Redis-backed lockout. Plus, added during genericization: self-service registration, an internal admin-provisioning endpoint, self-service logout-everywhere, and configurable token delivery (cookie vs JSON body). Magic-link password reset and email sending (restored, optional — see Environment variables) existed in the original CPMS service and are back in this repo. Super-admin login, bootstrap-on-boot, magic-link password reset, and impersonation-token issuance (restored, optional — see Environment variables) are also back, gated on `app.super-admin.enabled`. TOTP-based MFA (enroll/confirm/disable, backup codes, per-tenant enforcement) and OAuth/OIDC social login (Google et al., account linking by verified email, password-setup gate for OAuth-only signups) are both new, optional, off by default (`app.mfa.enabled` / `app.oauth.enabled`; see Environment variables below).

## Genericization decisions

- **Tenant model**: `tenant_id` stays `NOT NULL` at the schema level (not migrated to nullable) — single-tenant/no-tenant callers use the sentinel `PLATFORM_TENANT_ID = 00000000-0000-0000-0000-000000000000` (`domain/TenantConstants.java`), the same UUID already used for super-admin accounts in the original code. Multi-tenant callers pass a real per-org UUID.
- **`TenantSlugResolver`**: no longer queries a `tenants` table owned by another service (that table doesn't exist here). Resolves the sentinel to `"platform"`, everything else to `""`. If you need real per-tenant slugs, extend this class — don't reach into another service's database the way the original CPMS version did.
- **`TenantSlugResolver.resolveName` / `UserDisplayNameResolver`**: `TenantSlugResolver` also has a `resolveName(UUID)` method (default no-op returns `""`, except the platform sentinel, which returns `"Platform"`), and there's a new `UserDisplayNameResolver` interface (single method `resolve(UUID userId) -> String`, default no-op always returns `""`). Both are pluggable via the same `@ConditionalOnMissingBean` override pattern as `TenantSlugResolver`, and both feed optional `tenant_name`/`username` JWT claims that are omitted entirely from the token when blank.
- **Every human-session access token now always carries `user_email`**: as of this change, every access token issued through a login, refresh, or impersonation session for a real user carries a `user_email` claim, read directly from the user's stored email. Unlike `username`/`tenant_name`, there's no resolver or opt-out for this one — it's unconditional. Worth knowing if your token ever reaches a browser (cookie-delivery mode) or gets forwarded to downstream services.
- **Token delivery**: `auth.token-delivery-mode` = `cookie` (default, preserves 100% of the original behavior) or `json` (tokens in the response body — for mobile/cross-origin/non-browser clients). Env override: `TOKEN_DELIVERY_MODE`.
- **Registration**: `auth.registration-mode` = `open` (default) or `disabled`. Env override: `REGISTRATION_MODE`. `POST /api/v1/auth/register` never accepts a `roleId` — only `POST /internal/auth/users` (gated by the existing `X-Internal-Secret` header, same filter that already protected `/revoke-sessions`) can set one.
- **Role assignment**: a user created with no `roleId` (the only thing the public register endpoint can do) logs in successfully with an empty `role_ids` claim — this service doesn't own a Role/Permission concept of its own (that lived in a sibling service in the original system), so "no role" is a valid, working state rather than a login-blocking error.
- **Removed entirely**: 8 RabbitMQ consumers reacting to sibling-service events, `RabbitMqConfig`'s cross-service queue declarations, the outbound `AuthEventPublisher`, the email-template renderer, `AuthBootstrapService` (triggered only by another service's provisioning saga), `AuthReconciliationService` (a dormant, disabled-by-default, ADM-SVC-specific migration-repair tool). RabbitMQ/email were deferred, not deleted from history (see `docs/superpowers/specs/2026-07-16-starter-library-design.md`).
- **JWKS rotation**: `jwt.signing-mode=local` deployments can rotate/retire signing keys at runtime via `/internal/auth/keys/**` — no restart needed. Rotated keys persist to Postgres (`jwt_signing_key`/`jwt_active_signing_key` tables), encrypted at rest with AES-256-GCM (`JWT_KEY_ENCRYPTION_SECRET`). `kms` mode still uses the existing restart-based YAML procedure (creating a new AWS KMS key is an AWS-side action, out of scope for this endpoint).

## Environment variables

| Variable | Required | Notes |
|---|---|---|
| `AUTH_DB_URL`, `AUTH_DB_USERNAME`, `AUTH_DB_PASSWORD` | yes | Postgres connection |
| `INTERNAL_SERVICE_SECRET` | yes | Gates `/internal/**` (admin user provisioning, JWKS rotation) |
| `JWT_ISSUER`, `JWT_AUDIENCE` | no (defaults exist) | JWT `iss`/`aud` claims |
| `REGISTRATION_MODE` | no (`open`) | `open` \| `disabled` |
| `TOKEN_DELIVERY_MODE` | no (`cookie`) | `cookie` \| `json` |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | no | Blank uses the SDK's default credential chain |
| `JWT_KEY_ENCRYPTION_SECRET` | yes | 32-byte value, base64-encoded (`openssl rand -base64 32`). Encrypts rotated JWT private keys at rest. |
| `APP_EMAIL_ENABLED` (`app.email.enabled`) | no (`false`) | Turns on email sending (`EmailService`/`EmailProviderConfig`). Off by default — no beans registered, no SMTP/SES connection attempted. |
| `APP_MAGIC_LINK_ENABLED` (`app.magic-link.enabled`) | no (`false`) | Turns on the magic-link password-reset endpoints (`/api/v1/auth/magic-link/issue`, `/verify`). **Requires `app.email.enabled=true`** — a magic-link with no way to deliver the reset URL is a broken feature; `MagicLinkActivationValidator` fails startup fast if this is set without email also enabled. |
| `mail.provider` | no (`smtp`) | Email transport backend: `smtp` (Spring `JavaMailSender`, configured via standard `spring.mail.*`) or `ses` (AWS SES SDK v2). Only read when `app.email.enabled=true`. When `mail.provider=ses`, `mail.ses.region` and `mail.ses.from-address` are also required. |
| `APP_SUPER_ADMIN_ENABLED` (`app.super-admin.enabled`) | no (`false`) | Turns on the platform super-admin bucket: bootstrap-on-boot account creation, `/api/v1/auth/super-admin/login`, `/api/v1/auth/super-admin/reset-password/{issue,verify}`, `/internal/auth/impersonation-token`, and `/internal/service-token`. Off by default — none of those beans are registered. **Requires both `app.email.enabled=true`** (bootstrap-credential and password-reset emails can't be delivered without it) **and `app.magic-link.enabled=true`** (the super-admin password-reset flow reuses the same Redis-backed `MagicLinkStore` as the tenant-user magic-link flow) — see the manual-verification note below. `SuperAdminActivationValidator` fails startup fast with a clear `IllegalStateException` when either flag or `auth.super-admin.email`/`.password` are missing. |
| `SUPER_ADMIN_EMAIL` (`auth.super-admin.email`) | required if `app.super-admin.enabled=true` | Email for the bootstrap super-admin account. No default — shipping one would be a security anti-pattern. |
| `SUPER_ADMIN_PASSWORD` (`auth.super-admin.password`) | required if `app.super-admin.enabled=true` | Initial plaintext password for the bootstrap super-admin account, Argon2id-hashed before storage. Change it after first login. |
| `APP_INTERNAL_HMAC_AUTH_ENABLED` (`app.internal-hmac-auth.enabled`) | no (`false`) | Turns on a second internal-caller auth mechanism — HMAC-SHA256-signed requests (`X-CPMS-Timestamp`/`X-CPMS-Signature` headers) — alongside the existing `X-Internal-Secret` header check. Off by default. Also requires `app.internal-hmac-auth.target-paths` (a YAML list, not an env var) to name every path this should guard — `InternalHmacAuthFilter` fails safe (never verifies) for any path not listed, and `V1InternalTokenController` fails fast at startup if `/v1/impersonation-token` (when `app.super-admin.enabled=true`) or `/v1/client-token` (when `app.client-token.enabled=true`) isn't in that list while enabled. |
| `APP_CLIENT_TOKEN_ENABLED` (`app.client-token.enabled`) | no (`false`) | Turns on `POST /v1/client-token`, a CLIENT-scoped JWT mint for an internal caller (e.g. CPT-SVC) that has already verified the human. Its own flag, not tied to `app.super-admin.enabled` — this isn't a super-admin-privileged token. Off by default — the handler 404s and `ClientTokenService` isn't registered. Still requires `app.internal-hmac-auth.enabled=true` (the whole `V1InternalTokenController` is HMAC-gated) with `/v1/client-token` in `app.internal-hmac-auth.target-paths`. |
| `APP_MESSAGING_ENABLED` (`app.messaging.enabled`) | no (`false`) | Turns on RabbitMQ event publishing (`AuthEventPublisher`, `MessagingConfig`'s exchange beans, `AuthOutboxRelayJob`). Off by default — no beans registered, no broker connection attempted, `authEventPublisher` stays `null` at every call site. **Behavior change as of the outbox+audit-routing slice**: publishes are no longer fire-and-forget. `AuthEventPublisher` writes a `PENDING` row to `auth_outbox_events` (migration `V7`) inside the caller's transaction, and a scheduled `AuthOutboxRelayJob` drains that table to the broker with retry — a broker outage no longer silently drops events, rows just accumulate as `PENDING`. Delivery is at-least-once; each message carries `messageId = event_id` (unique per row) so consumers can dedup. |
| `app.messaging.exchange` | no (`auth.events`) | Business-event topic exchange. Rows with no `target_exchange` (every non-audit event) are published here at relay time. |
| `app.messaging.outbox.relay-interval-ms` | no (`5000`) | Fixed delay between `AuthOutboxRelayJob` polls, in milliseconds. There is no backoff beyond this interval: a row that fails to send is retried on the next tick, up to 3 total attempts, then marked `FAILED` and never retried automatically. |
| `app.messaging.outbox.batch-size` | no (`50`) | Rows locked and drained per poll (`SELECT ... FOR UPDATE SKIP LOCKED`). Multiple app instances can run the job concurrently without double-publishing — each gets a disjoint batch. |
| `app.messaging.audit.tenant-exchange` | no (`auth.audit.tenant`) | Topic exchange for tenant-tier audit events (`auth.tenant.login.success`/`.failed`, `auth.logout`, `auth.password.changed`). Declared as a `TopicExchange` bean. A host app wanting exact CPMS-Platform parity sets this to `cpms.audit`. |
| `app.messaging.audit.platform-exchange` | no (`auth.audit.platform`) | Fanout exchange for platform-tier (super-admin) audit events (`auth.superadmin.login.success`/`.failed`). Declared as a `FanoutExchange` bean, so routing keys are ignored on this tier. CPMS-Platform parity value: `cpms.platform.audit`. |
| `APP_OTP_ENABLED` (`app.otp.enabled`) | no (`false`) | Turns on `POST /internal/otp/request` and `/verify` — caller-supplied-email one-time-code issue/verify, Redis-backed (`OtpStore`), single-use on both match and mismatch. Off by default — none of those beans are registered. **Requires `app.email.enabled=true`** — `OtpServiceImpl` depends on `EmailService` unconditionally, and (unlike `app.super-admin.enabled`/`app.magic-link.enabled`) there is currently no dedicated activation validator for this pair, so `app.otp.enabled=true` with email off fails Spring's bean graph at startup rather than surfacing a purpose-built `IllegalStateException`. |
| `APP_MFA_ENABLED` (`app.mfa.enabled`) | no (`false`) | Turns on TOTP-based MFA: `/api/v1/auth/mfa/enroll`, `/enroll/confirm`, `/disable`, `/verify-login`. Off by default — `MfaController`/`MfaServiceImpl`/`MfaLoginGateImpl` aren't registered and login behaves exactly as before. Per-tenant enforcement is set via the always-on internal endpoint `POST /internal/auth/tenants/{tenantId}/mfa-required` (not itself gated by this flag). |
| `mfa.secret-encryption-key` | required if `app.mfa.enabled=true` | 32-byte value, base64-encoded (`openssl rand -base64 32`). Encrypts stored TOTP secrets at rest (`MfaSecretEncryptionUtil`). |
| `APP_OAUTH_ENABLED` (`app.oauth.enabled`) | no (`false`) | Turns on OAuth/OIDC social login (`oauth2Login()`, `OAuthConfig`'s collaborator beans, `POST /api/v1/auth/oauth/complete-signup`). Off by default — no beans registered, no `oauth2Login()` attached to `SecurityConfig`. **Requires at least one `spring.security.oauth2.client.registration.<id>.*` entry** (issuer/client-id/client-secret/scopes — provider identity is standard Spring Boot config, not duplicated under `app.oauth.*`); enabling the flag with zero registrations fails the app fast at startup rather than booting with a no-op login. |

**Upgrading an existing deployment?** `JWT_KEY_ENCRYPTION_SECRET` is required at boot for *every* install on this version, not just ones that plan to use key rotation — `KeyEncryptionUtil` fails fast at startup if it's missing/wrong-length, and `RsaKeyConfig` now depends on it unconditionally (same fail-fast pattern as `INTERNAL_SERVICE_SECRET`). Set it *before* deploying this version, including on YAML-only and `kms`-mode installs that never call `/internal/auth/keys/rotate` — the service will not boot without it.

No `.env` file is loaded automatically — this is Spring Boot, not Node. Export these in your shell or pass them to `bootRun`/the packaged jar directly.

## Running it locally

```bash
docker compose up -d   # Postgres 15 on host port 5433, Redis on host port 6380, Mailhog on host ports 1026 (SMTP) / 8026 (web UI) — see note below on why non-default ports
mkdir -p gen-auth-demo/src/main/resources/keys
openssl genrsa -out gen-auth-demo/src/main/resources/keys/private.pem 2048
openssl rsa -in gen-auth-demo/src/main/resources/keys/private.pem -pubout -out gen-auth-demo/src/main/resources/keys/public.pem

JAVA_TOOL_OPTIONS=-Duser.timezone=UTC \
AUTH_DB_URL=jdbc:postgresql://localhost:5433/genauth \
AUTH_DB_USERNAME=postgres AUTH_DB_PASSWORD=postgres \
SPRING_DATA_REDIS_PORT=6380 \
INTERNAL_SERVICE_SECRET=dev-secret \
JWT_KEY_ENCRYPTION_SECRET=$(openssl rand -base64 32) \
JWT_ISSUER=genauth JWT_AUDIENCE=genauth \
./gradlew.bat :gen-auth-demo:bootRun
```

### Two environment gotchas hit during verification, both worth knowing about

1. **Non-default ports (5433/6380, not 5432/6379).** If your machine has a native Postgres/Redis install, `docker compose`'s published `5432`/`6379` can silently collide with it — a JVM launched directly on the Windows/host side (not inside Docker/WSL) may reach the *native* service instead of the container, with a completely different password, giving a confusing "password authentication failed" instead of a connection-refused. Using distinct host ports sidesteps this ambiguity entirely; there's no code-level fix for it, it's host-specific.
2. **`JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` is required, not optional, in some locales.** The JVM's default timezone name can resolve to a legacy IANA alias (e.g. `Asia/Calcutta` instead of `Asia/Kolkata`) that this Postgres image's tzdata doesn't recognize, causing every DB connection to fail with `invalid value for parameter "TimeZone"`. Passing `-D` on the `gradlew` command line does **not** propagate to `bootRun`'s forked JVM — it must be `JAVA_TOOL_OPTIONS`, which every JVM launch respects regardless of how it's forked.

## Internal key-rotation endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST   | `/internal/auth/keys/rotate`     | `X-Internal-Secret` header | Generates a new RSA key, persists it, promotes it active immediately (local mode only) |
| POST   | `/internal/auth/keys/{kid}/retire` | `X-Internal-Secret` header | Removes a rotated key; 404 if unknown/YAML-sourced, 409 if it's the active key |
| GET    | `/internal/auth/keys`            | `X-Internal-Secret` header | Lists all kids in the registry and which is active |
| POST   | `/internal/auth/impersonation-token` | `X-Internal-Secret` header | Only registered when `app.super-admin.enabled=true`. Issues a signed impersonation JWT for a caller-resolved (super admin, tenant, impersonation role) tuple — a pure token factory, no user JWT required. `401` if the header is missing/wrong. |
| POST   | `/v1/impersonation-token`        | `X-CPMS-Timestamp`/`X-CPMS-Signature` headers (HMAC-SHA256) | Only registered when both `app.super-admin.enabled=true` and `app.internal-hmac-auth.enabled=true`. Same `ImpersonationTokenService` and response shape as `/internal/auth/impersonation-token` above — this is an alternate path/auth-mechanism for callers that sign requests instead of sending a shared secret (matches a real external system's existing caller code exactly, so that caller needs zero changes). `401` if the signature is missing, stale (>60s clock skew), or doesn't verify. |
| GET    | `/internal/auth/users/exists`    | `X-Internal-Secret` header | Unconditionally registered, same as its sibling `/internal/auth/users` and `/revoke-sessions` handlers on `InternalUserController` — no feature flag. `?email=` query param; returns a bare `true`/`false` for whether any account (active or inactive) exists — used by invite flows to reject duplicate invites before sending them. |
| POST   | `/internal/service-token`        | `X-Internal-Secret` header | Only registered when `app.super-admin.enabled=true`. Mints a stateless, unpersisted `SUPER_ADMIN`-scoped JWT for service-to-service calls (`sub=TenantConstants.SERVICE_ACCOUNT_ID`), 5-minute default TTL (`jwt.service-token.expiration-minutes`). `X-CPMS-Service` header (optional, defaults to `unknown`) is recorded in the token's `session_id` claim and in logs only — never used as an authorization check. |
| POST   | `/internal/otp/request`         | `X-Internal-Secret` header | Only registered when `app.otp.enabled=true`. Generates a 6-digit code, stores its SHA-256 hash in Redis (`app.otp.expiration-minutes`, default 5), emails the plaintext code via `EmailService.sendOtpCode`, returns `{"otpId": "..."}`. |
| POST   | `/internal/otp/verify`          | `X-Internal-Secret` header | Only registered when `app.otp.enabled=true`. Atomically checks-and-consumes the code for a given `otpId` — always single-use, even on a wrong-code attempt. Returns `{"verified": true/false}`; no distinction leaked between wrong code, expired, already-consumed, or unknown `otpId`. |
| POST   | `/v1/client-token`               | `X-CPMS-Timestamp`/`X-CPMS-Signature` headers (HMAC-SHA256) | Only registered when `app.internal-hmac-auth.enabled=true`; the handler itself 404s unless `app.client-token.enabled=true` too (its own flag, independent of `app.super-admin.enabled`). Persists an `AuthSessionEntity` and mints a CLIENT-scoped JWT for a caller (e.g. CPT-SVC) that has already verified the human — 15-minute default TTL (`jwt.client-token.expiration-minutes`). |

## MFA and OAuth endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/auth/mfa/enroll` | JWT, or `challengeToken` query param when login itself demands enrollment | Returns a TOTP `otpauthUri` + base32 secret. Only registered when `app.mfa.enabled=true`. |
| POST | `/api/v1/auth/mfa/enroll/confirm` | JWT | Confirms the first TOTP code, returns one-time-use backup codes. |
| POST | `/api/v1/auth/mfa/disable` | JWT | Removes MFA enrollment for the calling user. |
| POST | `/api/v1/auth/mfa/verify-login` | `challengeToken` from a login response's MFA challenge | Verifies a TOTP/backup code and completes the login (cookies or JSON body, per `auth.token-delivery-mode`), 5 attempts max before the challenge is invalidated. |
| POST | `/internal/auth/tenants/{tenantId}/mfa-required` | `X-Internal-Secret` header | Sets per-tenant MFA enforcement. Always registered (not gated by `app.mfa.enabled`). |
| GET | `/oauth2/authorization/{registrationId}` | none (starts the flow) | Standard Spring Security OAuth2 login entry point — redirects to the provider. Only reachable when `app.oauth.enabled=true` and that `registrationId` is configured. |
| POST | `/api/v1/auth/oauth/complete-signup` | `setupToken` from an `OAuthPasswordSetupRequiredResponse` | Sets a password for an OAuth-originated account (new signup, or an existing account still missing a password) and completes login. Only registered when `app.oauth.enabled=true`. |

OAuth login (`OAuthLoginSuccessHandler`, run after Spring Security validates the provider's ID token) resolves to one of: matched existing identity → login; first-time email match with a verified provider email → auto-link + login; first-time email match with an unverified provider email → `409 oauth_email_not_verified`; no email match → new pending user created and blocked on `complete-signup`; any account (new or linked) with no password yet → blocked on `complete-signup` regardless of how many times OAuth login is repeated.

## Manual verification (2026-07-14, against real Postgres 15 + Redis 7)

All of the following were exercised for real, not just unit-tested:

- ✅ `POST /api/v1/auth/register` → `201`, then registering the same email again → `409`
- ✅ `POST /api/v1/auth/login` in cookie mode (default): `Set-Cookie` headers present, JSON body's `refreshToken` is `null`
- ✅ `POST /api/v1/auth/login` in `TOKEN_DELIVERY_MODE=json`: zero `Set-Cookie` headers, body has both `accessToken` and a populated `refreshToken`
- ✅ `POST /api/v1/auth/logout-all` — 401 unauthenticated, 200 with a valid session, clears cookies
- ✅ `POST /internal/auth/users` — 401 without `X-Internal-Secret`, 201 with it; the supplied `roleId` is confirmed present in the resulting JWT's `role_id` claim after login
- ✅ `REGISTRATION_MODE=disabled` → `POST /register` returns `403`
- ✅ A self-registered user (no role) logs in successfully with no `role_ids`/`role_id` claim in the JWT

**Three real bugs were found and fixed during this pass, none of which the unit-test suite caught** (all three needed a live database/HTTP round-trip to surface):
1. `SecurityConfig`'s permit-list never included `/api/v1/auth/register` — the new endpoint 401'd unconditionally until added.
2. Login threw `UnauthorizedException` for any `TENANT_USER` with zero `auth_user_roles` rows — meaning every self-registered account could register but never log in. Relaxed to allow empty-roles login (see Genericization decisions above).
3. `RegisterServiceImpl` stored a supplied `roleId` only on the legacy `auth_users.role_id` scalar column, never in the `auth_user_roles` table login actually reads from — so the internal endpoint's whole reason to exist (assigning a role at creation) had zero effect until fixed.

## Manual verification — JWKS key rotation (2026-07-15, against real Postgres 15 + Redis 7)

Full rotate → sign → retire → restart cycle exercised against a real, freshly-migrated Postgres instance:

- ✅ JWKS starts with exactly 1 key (`auth-key-v1`, from YAML)
- ✅ `GET /internal/auth/keys` → `{"activeKid":"auth-key-v1","kids":["auth-key-v1"]}`
- ✅ `POST /internal/auth/keys/rotate` → `201`, new `auth-key-<uuid>` kid + PEM public key returned
- ✅ JWKS now has 2 keys
- ✅ Logging in produces a token whose header `kid` is the newly rotated key (proving the registry's active-kid switch takes effect immediately, no restart)
- ✅ Retiring the now-active rotated key → `409`
- ✅ Retiring the original YAML key (`auth-key-v1`) → `404` (not DB-managed)
- ✅ **Restarted the process** (killed and re-ran `bootRun` against the same Postgres) — `GET /internal/auth/keys` afterward still showed both `auth-key-v1` and the rotated kid, with the rotated kid still active, proving the DB-backed registry survives a restart rather than reverting to the YAML-only key

**One real bug was found and fixed during this pass, not caught by the unit-test suite** (needed real Postgres schema validation to surface): `JwtSigningKeyEntity.privateKeyCiphertext` was annotated `@Lob`, which Hibernate 7 maps to `BLOB`/Postgres `oid` — but the `V3__jwt_signing_keys.sql` migration declares the column `BYTEA`. The app failed to boot with a schema-validation error the moment a real Postgres instance was used. Fixed by dropping `@Lob` (plain `byte[]` maps to `VARBINARY`/`BYTEA`, matching the migration).

## Manual verification — email + magic-link password reset (2026-07-16, against real Postgres 15 + Redis 7 + Mailhog)

Exercised end-to-end with `APP_EMAIL_ENABLED=true APP_MAGIC_LINK_ENABLED=true`, real SMTP delivery captured by Mailhog (`docker compose up -d mailhog`, web UI/API on host port 8026):

- ✅ Registered a user, then `POST /api/v1/auth/magic-link/issue` → `202`, generic message
- ✅ Reset email actually captured by Mailhog (checked via `curl http://localhost:8026/api/v2/messages`, not just the browser) — subject "Reset your password", body contains a `?token=...` reset URL
- ✅ `POST /api/v1/auth/magic-link/verify` with the real token parsed out of the captured email → `200`
- ✅ Old password → `401` on `/api/v1/auth/login`; new password → `200` with valid tokens
- ✅ Both flags off/absent: app boots, `/actuator/health` → `UP`, no SMTP connection attempted at startup, `MagicLinkController`/`MagicLinkActivationValidator` beans never registered (confirmed via `ConditionalOnProperty`)
- ✅ `APP_MAGIC_LINK_ENABLED=true` with `APP_EMAIL_ENABLED` unset → application fails to start (confirmed)

**Two things worth knowing about, found during this pass (neither is a magic-link regression from Tasks 1-3, both pre-existing/general):**
1. Hitting `POST /api/v1/auth/magic-link/issue` while the feature is off returns `500` ("An unexpected error occurred"), not `404`. The bean is genuinely never registered — Spring correctly throws `NoResourceFoundException` for the unmapped path — but `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)` intercepts it and maps it to a generic 500 instead of letting the 404 through. This affects *any* unmapped path that Spring Security's permit-list lets past the auth filter (not magic-link-specific).
2. The magic-link-without-email fail-fast does stop the app from starting, but not via `MagicLinkActivationValidator`'s intended `IllegalStateException` message — Spring's bean graph fails first with `UnsatisfiedDependencyException: ... No qualifying bean of type 'EmailService'` while constructing `MagicLinkServiceImpl` (which unconditionally depends on `EmailService` via constructor injection), before the validator's `@PostConstruct` check gets a chance to run. Net effect is the same (won't boot), but the friendlier, purpose-built error message doesn't actually surface in this bean-creation order.

## Manual verification — super-admin + impersonation (2026-07-16, against real Postgres 15 + Redis 7 + Mailhog)

Exercised end-to-end with `APP_EMAIL_ENABLED=true APP_MAGIC_LINK_ENABLED=true APP_SUPER_ADMIN_ENABLED=true`, real SMTP delivery captured by Mailhog:

- ✅ Boot log showed `superadmin.bootstrap.created email=admin@example.com`
- ✅ Bootstrap-credentials email actually captured by Mailhog (checked via `curl http://localhost:8026/api/v2/messages`) — subject "Your super admin account is ready", body contains "Temporary Password: TempPass123!"
- ✅ `POST /api/v1/auth/super-admin/login` → `200`, `Set-Cookie` headers present
- ✅ `POST /api/v1/auth/super-admin/reset-password/issue` → `202`; reset email captured by Mailhog, subject "Reset your password", real `?token=...` parsed out of the body
- ✅ `POST /api/v1/auth/super-admin/reset-password/verify` with that real token → `200`
- ✅ Old password → `401` on super-admin login; new password → `200` with valid cookies
- ✅ `POST /internal/auth/impersonation-token` with a valid `X-Internal-Secret` → `200`, real signed JWT, `expiresIn=3600`
- ✅ Same request without `X-Internal-Secret` → `401`
- ✅ Rebooted with `APP_SUPER_ADMIN_ENABLED` unset, `APP_EMAIL_ENABLED=true`: `/actuator/health` stayed `UP`; both `/api/v1/auth/super-admin/login` and `/internal/auth/impersonation-token` came back `500` (not `404`) — the same pre-existing `NoResourceFoundException`-swallowed-by-`GlobalExceptionHandler` behavior already documented above for magic-link, confirmed again here rather than assumed
- ⚠️ Rebooted with `APP_SUPER_ADMIN_ENABLED=true`, `APP_EMAIL_ENABLED` unset: app correctly refused to boot — but **not** via `SuperAdminActivationValidator`'s intended `IllegalStateException`; see finding below (now fixed)

**One more bean-graph race, found during this pass (same family as the magic-link/email one documented in the section above, not a regression from Tasks 1-5), since fixed:** `app.super-admin.enabled=true` with `app.email.enabled` unset never actually surfaced `SuperAdminActivationValidator`'s friendly `IllegalStateException` — a bean-wiring failure happened first, before the validator's `@PostConstruct` ever ran:
- `APP_SUPER_ADMIN_ENABLED=true` alone (magic-link off) failed with `Parameter 1 of constructor in SuperAdminMagicLinkServiceImpl required a bean of type 'MagicLinkStore' that could not be found` — because `SuperAdminMagicLinkServiceImpl` is gated only on `app.super-admin.enabled`, but unconditionally depends on `MagicLinkStore`, which is only registered when `app.magic-link.enabled=true`. This was an undocumented cross-dependency of super-admin on magic-link, separate from its documented dependency on email.
- `APP_SUPER_ADMIN_ENABLED=true` + `APP_MAGIC_LINK_ENABLED=true` (email off) instead failed with `Parameter 5 of constructor in MagicLinkServiceImpl required a bean of type 'EmailService' that could not be found` — the exact same `EmailService` bean-graph race already documented in the email+magic-link slice's own verification section above.

Net effect was the same either way — the app correctly refused to boot when a prerequisite was off — but the purpose-built validator message never surfaced for the super-admin bucket. **Fix:** `SuperAdminActivationValidator` now also checks `app.magic-link.enabled` directly (alongside its existing `app.email.enabled` check), so both cross-dependencies fail fast with the intended `IllegalStateException` instead of a bean-wiring error.

## Not done yet

Nothing tracked as outstanding — MFA and OAuth/social login (below) closed out the last two items on this list.
