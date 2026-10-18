# Gen_Auth vs CPMS auth-svc — Gap Analysis

Goal: swap Gen_Auth in for auth-svc inside CPMS-Platform with nothing breaking.
Scope: only what auth-svc has that Gen_Auth lacks. Gen_Auth's extra features (MFA,
KMS signer, magic-link, etc.) are out of scope — already covered.

Legend: 🔴 blocker (CPMS breaks without it) · 🟡 needed but lower risk · ⚪ note/decision needed, not a code gap.

---

## 1. Missing endpoints / controllers (🔴 blockers) — ✅ RESOLVED (2026-08-27)

| auth-svc endpoint | Gen_Auth equivalent | Gap | Status |
|---|---|---|---|
| POST /v1/client-token (ClientTokenController) | none | No CLIENT-type token minting at all. Gen_Auth's UserType enum still carries CLIENT/PROSPECT but zero code path issues them. | ✅ Built — `POST /v1/client-token` via `V1InternalTokenController`, HMAC-guarded, `app.client-token.enabled`. |
| POST /v1/impersonation-token (HMAC X-CPMS-Signature, called by SUP-SVC) | POST /internal/auth/impersonation-token (shared-secret X-Internal-Secret) | Same capability, different trust model. Gen_Auth's version won't accept SUP-SVC's HMAC-signed calls. | ✅ Built (sub-project 1, PR #1) — see §2. |
| POST /internal/service-token (ServiceTokenController) | **explicitly removed** during genericization (per docs/superpowers/specs/2026-07-16-restore-deferred-features-design.md) | Whole feature absent on purpose. Must be re-added (generalized) or CPMS's callers of this endpoint need another path. | ✅ Built — `POST /internal/service-token`, stateless mint, `app.super-admin.enabled`. |
| POST /internal/otp/request, POST /internal/otp/verify (InternalOtpController) | none | Gen_Auth only has MFA-TOTP for login. No generic caller-supplied-email OTP issue/verify (used today by FIN-SVC). | ✅ Built — `app.otp.enabled`, `OtpActivationValidator` fail-fasts without `app.email.enabled`. |
| POST /api/v1/auth/signoff-token (SignoffTokenController) | none | Entire signoff-token flow (internal employee → short-lived CLIENT-context token, gated on ADM+CPT lookups) is missing. | ✅ **Resolved as no-library-work-needed.** ~90% of this endpoint is CPMS-specific authorization logic (ADM-SVC "Signoff Client" lookup, CPT-SVC role-tag check) — classified alongside §9's AdmClient/CptClient/RabbitMQ consumers. The generic tail (persist session, mint a re-scoped JWT for the same identity) is already exposed as public beans (`JwtUtils`, `AuthSessionJpaRepository`, `TenantSlugResolver`). CPMS-Platform writes this endpoint itself at Phase B (auth-svc migration) time, calling those beans directly — no new gen-auth-starter endpoint or extension point. Note: signoff-token's real flow has no request body to pass a tenant slug through (unlike impersonation/client-token), so Phase B will need §7's tenant/user-store SPI done first, or its own `TenantSlugResolver` bean override. |
| GET /internal/auth/users/exists | none (InternalUserController only has create + revoke-sessions) | No email-existence check for ADM's invite flow. | ✅ Built — `GET /internal/auth/users/exists`. |

## 2. Trust/auth mechanism gap (🔴 blocker, cross-cutting) — ✅ RESOLVED (PR #1)

Gen_Auth's only internal-caller auth is a single shared-secret header (X-Internal-Secret,
InternalTokenAuthFilter). auth-svc uses **two different mechanisms** depending on caller:
shared-secret header (ADM/CPT/FIN internal calls)
**HMAC request signing** (X-CPMS-Signature) for SUP-SVC → impersonation-token and CPT-SVC → client-token

Gen_Auth has no HMAC-signature verification filter at all. This is a generic, reusable
security primitive (not CPMS-specific business logic) — needs to be added as a second
pluggable internal-auth strategy, not hardcoded to CPMS service names.

## 3. Event delivery reliability gap (🔴 blocker) — ✅ RESOLVED (2026-09-01)

**auth-svc**: transactional outbox pattern — auth_outbox_events table, AuthOutboxRelayJob
  polling every 5s with SELECT ... FOR UPDATE SKIP LOCKED, max 3 retries then FAILED. Two
  audit tiers (cpms.audit / cpms.platform.audit) routed via AuthAuditEventRouter
  classification logic, plus a general business exchange (cpms.events).
**Gen_Auth**: AuthEventPublisher is fire-and-forget only (no outbox, no retry, no dedup,
  no audit-tier routing). If a publish fails or RabbitMQ is briefly down, the event is just lost.

Gap: outbox + relay job + retry/dead-letter handling + the two-tier audit routing/classification
logic. This is a generic reliability pattern, worth porting into the library itself (not
CPMS-specific), since any consuming SaaS app will want at-least-once event delivery.

✅ Built — `auth_outbox_events` table (V7 migration, verbatim CPMS schema), `AuthOutboxRelayJob`
(`@Scheduled`, `FOR UPDATE SKIP LOCKED`, `MAX_RETRIES=3`, configurable interval/batch-size),
`AuthEventPublisher` rewritten to enqueue to the outbox instead of firing directly at RabbitMQ.
`RefreshTokenServiceImpl`'s 3 rollback-prone logout sites wrapped in `REQUIRES_NEW` so outbox rows
survive the caller's later throw. Merged to local `main` at `9f597ca`.

## 4. Credential provisioning gap (🔴 blocker) — ✅ RESOLVED (2026-09-01)

auth-svc has three distinct "create a user + send credentials" flows:
tenant bootstrap (AuthBootstrapService)
ADM employee creation (UserCreatedConsumer, invitation-vs-magic-link branching)
CLIENT provisioning (ClientCredentialProvisioningService, shared by two consumers)

Gen_Auth's InternalUserController only exposes a bare POST /internal/auth/users create —
no invitation/welcome-email branching logic, no CLIENT-specific provisioning path. Gen_Auth
deliberately removed all 8 RabbitMQ consumers and AuthBootstrapService/
AuthReconciliationService as CPMS-specific — but the underlying **provisioning logic itself**
(create user, decide invite vs. direct-credential email, send the right template) is a generic
capability CPMS still needs from something. Decision needed: keep provisioning logic in
CPMS-Platform's own service layer (calling Gen_Auth's plain create endpoint) vs. add it back to
Gen_Auth as a configurable strategy. Recommendation: keep it in CPMS/host-app layer — it's
genuinely business-specific (which template, which trigger) — but Gen_Auth must expose enough
hooks (e.g. a "create with role + send email template X" parameter) to support it without a
second bespoke endpoint per case.

✅ Decision confirmed: provisioning logic (invite vs. direct-credential, which template) stays in
CPMS-Platform's own service layer, calling Gen_Auth's plain create endpoint + item 5's email
methods directly — no library-side provisioning strategy added. Spike against CPMS-Platform's
real `ClientCredentialProvisioningService`/`UserCreatedConsumer` found the create endpoint's
hook set was NOT quite sufficient as-is: both real flows pre-assign the user's id (matching an
ADM/CPT-owned identity) and set a non-default `userType`, neither of which the endpoint
supported. Fixed: `register()`/`POST /internal/auth/users` now accept optional `id` (idempotent —
an existing id short-circuits to a no-op returning that id, safe under event redelivery) and
`userType` (default `TENANT_USER`). Public self-registration unaffected (passes null/null).

## 5. Email templates (🟡 needed) — ✅ RESOLVED (2026-09-01)

auth-svc sends 5 email types: password reset, tenant-admin welcome, invitation welcome,
super-admin bootstrap creds, OTP code. Gen_Auth's EmailService/ProviderBackedEmailService
only covers password reset (confirmed) — welcome/invitation/bootstrap-creds/OTP templates are
missing. Needed once items 1 and 4 are ported (OTP and provisioning flows need their own emails).

✅ All 5 now present. `sendOtpCode`/`sendSuperAdminBootstrapCredentials` were already added
during item 2's work this session; `sendTenantAdminWelcome`/`sendInvitationWelcome` added here,
same pattern (SMTP+SES providers, `ProviderBackedEmailService` delegate). No call sites in this
library — tenant bootstrap and ADM-invitation provisioning stay CPMS-specific (§9); these are
public beans for CPMS-Platform's Phase B provisioning layer to call directly.

## 6. Two-tier audit logging (🟡 needed) — ✅ RESOLVED (2026-09-01)

auth-svc classifies every audit event into tenant-tier (cpms.audit) vs platform-tier
(cpms.platform.audit) via AuthAuditEventRouter. Gen_Auth's auth_audit_logs is a single
undifferentiated table with no exchange publishing/classification. If CPMS's downstream audit
consumers depend on this routing, it needs to be added (can build on item 3's outbox/exchange
work — same mechanism, add a classifier).

✅ Built — `AuditRoutingProperties` (generic `app.messaging.audit.tenant-exchange` /
`platform-exchange`, not CPMS's own names), tenant `TopicExchange` + platform `FanoutExchange`
beans, 6 typed `publishAudit*` methods on `AuthEventPublisher` wired into all 4 real call sites
(login success/failed, logout, password-changed). Classification uses `UserType.SUPER_ADMIN`
where a resolved user is available; login-failure path documents its known single-tenant/
unknown-email imprecision inline (no better signal exists there). Same outbox/relay mechanism
as §3 — no separate delivery path.

## 7. Multi-tenant/user-store SPI is a stub, not a real extension point (🟡 needed, design gap) — 🟡 PARTIALLY RESOLVED (2026-09-01)

TenantSlugResolver in Gen_Auth only resolves the platform sentinel; every other tenant
  resolves to "". It's a concrete @Component, not an interface a host app can implement.
No pluggable UserDetailsService/user-store SPI — AuthUserEntity/AuthUserJpaRepository
  are hardwired.
auth-svc reads tenants, internal_users, client_users directly via JdbcTemplate
  against tables owned by other CPMS services in the same physical database — this specific
  approach is CPMS-only coupling and should **not** be ported as-is.

Gap: turn TenantSlugResolver (and ideally the user-lookup path) into a real Java interface
with a default no-op/platform-only implementation, so CPMS-Platform can supply its own bean
implementing tenant-slug and cross-service lookups the way it needs, without forking the library.

✅ TenantSlugResolver piece done — now a `TenantSlugResolver` interface +
`PlatformOnlyTenantSlugResolver` default (`@ConditionalOnMissingBean`), 4 call sites unchanged.
❌ User-lookup SPI (AuthUserEntity/AuthUserJpaRepository) deliberately NOT done — it's the
library's own identity store, wired directly into 10+ classes (login, sessions, MFA, OAuth,
password change, registration, internal-user CRUD). Making it swappable would mean abstracting
user lookup+mutation behind a port and decoupling every caller from the JPA entity, without
knowing yet what shape CPMS's real Phase B integration wants (own table vs. synced data vs.
something else) — deferred to Phase B rather than guessed at now.

## 8. Orphaned/unenforced config (⚪ verify, not necessarily a gap) — ✅ CONFIRMED NO GAP (2026-09-01)

Gen_Auth has auth.session.concurrent-session-limit in properties but **no code enforces it**.
Confirm whether CPMS/auth-svc relies on a concurrent-session cap today. If yes, enforcement logic
needs to be added to Gen_Auth before swap; if auth-svc doesn't enforce it either, ignore.

✅ Confirmed: `concurrent-session-limit: 5` exists in both Gen_Auth's and CPMS-Platform's real
`application.yaml`, but zero Java code references it in either repo (grepped both source trees).
CPMS-Platform's actual production auth-svc doesn't enforce it either — dead config on both sides.
No enforcement logic needed.

## 9. Explicitly-not-portable (⚪ CPMS coupling — solve at the app/integration layer, not in the library)

Don't try to add these to Gen_Auth itself — they're CPMS-specific by nature and belong in
CPMS-Platform's integration code around the library:
AdmClient/CptClient HTTP clients with hardcoded ADM-SVC/CPT-SVC/SUP-SVC/FIN-SVC/TNT-SVC
  names and Resilience4j config.
AuthReconciliationService — direct SQL joins against other services' tables in the same DB
  for event-miss backfill. Only safe if CPMS keeps the shared-DB assumption; otherwise redesign.
Fixed platform UUIDs (00000000-...-0001/0002, all-zero PLATFORM_TENANT_ID).
io.cpms.common.messaging event class/exchange/queue constants — external shared-lib
  dependency, not something Gen_Auth should absorb.
The 8 RabbitMQ consumers (UserCreatedEvent, RoleAssignedEvent,
  RolePermissionUpdatedEvent, ClientCreatedEvent, CPT-SVC's client-user created/deactivated,
  AuthBootstrapRequestedEvent, user.deactivated/reactivated) — these belong in
  CPMS-Platform's own service layer, subscribing to Gen_Auth's published events (item 3) or to
  other CPMS services directly, not inside the generic library.
DevDataSeeder — dev-only convenience, recreate per-project if wanted, not a library concern.

## 10. Secrets hygiene (⚠ found while reading, unrelated to the migration itself) — ⚠ FLAGGED, DEFERRED TO CUTOVER (2026-09-01)

auth-svc/src/main/resources/application.yaml has literal fallback secrets baked into
${VAR:default} expressions — AWS keys (lines ~283-284), internal-service-secret (line 336,
reused for both adm.internal-secret and cpt.internal-secret), SUPER_ADMIN_PASSWORD
default Naman@1234, and a live-looking dev test-user password. If env vars aren't set in some
environment, the app boots with these real-looking values active. Independent of the Gen_Auth
migration, but worth rotating/removing before or during the cutover. This lines up with earlier
findings of tracked credentials in this repo (memory: AUTH_API_KEY history).

Not fixed here — this is CPMS-Platform's own `application.yaml`, a file gen-auth-starter doesn't
own. Confirmed still present as of 2026-09-01. Stays flagged for rotation before/during Phase B
cutover, not gen-auth-starter code work.

---

## Priority order to reach parity

1. ✅ HMAC internal-auth strategy (§2) — cross-cutting, needed before client-token/impersonation-token can work correctly. **Done, PR #1, merged.**
2. ✅ Client-token, service-token, signoff-token, OTP endpoints (§1) — the actual missing surface area. **Done** — client-token/service-token/OTP/users-exists built and merged to local `main`; signoff-token resolved as no-library-work-needed (see §1 table).
3. ✅ Outbox + retry + two-tier audit routing (§3, §6) — reliability parity, avoids silent event loss in production. **Done**, merged to local `main` at `9f597ca`.
4. 🟡 Tenant/user-store SPI (§7) — do this early, it's a design decision that affects how CPMS wires itself to the library. TenantSlugResolver piece **done**; user-lookup SPI deferred to Phase B.
5. ✅ Email templates (§5) — mechanical, follows from §1's new flows. **Done.**
6. ✅ Credential-provisioning decision (§4) — decide library vs. app-layer before CPMS integration work starts. **Done.**
7. ✅ Confirm §8, handle §10 secrets separately, leave §9 alone (host-app responsibility). **Done** — §8 confirmed no gap (dead config on both sides); §10 flagged, deferred to Phase B cutover (CPMS-Platform's own file).

---

**gap.md priority order: all 7 items closed as of 2026-09-01.** Remaining work is Phase B itself
(the actual CPMS-Platform `auth-svc` → gen-auth-starter migration), deliberately not started —
see repo root note at top of this document.