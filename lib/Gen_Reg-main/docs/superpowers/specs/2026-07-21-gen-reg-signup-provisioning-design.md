# Gen_REG Phase 1 — Signup & Provisioning Handoff Design

## Goal

Genericize `reg-svc` (CPMS-Platform's registration service) into a standalone, reusable Node/Express/Prisma service: `Gen_REG`. Phase 1 covers the load-bearing path only — signup form, email verification, and handing the tenant off to Gen_TNT for provisioning. Payment, plan selection, captcha, and the wizard read-model are explicitly deferred to later phases.

## Source reference

`CPMS-Platform/apps/reg-svc` — see its `docs/adr/ADR-001` through `ADR-008` for the original's full design rationale. This spec only ports what Phase 1 needs; later phases will revisit the deferred ADRs (payment adapter, webhook dedup, wizard read-model, gRPC callback) when their features land.

## Scope

**In scope (Phase 1):**
- Signup form submission with validation (disposable-domain block, reserved-subdomain block, duplicate-email/duplicate-subdomain checks).
- Real-time subdomain availability check.
- Email verification via single-use token.
- Resume-signup via a separate long-lived resume token.
- Handoff to Gen_TNT: create the tenant, then poll until provisioning finishes (success or failure).
- A companion small addition to **Gen_TNT**: `GET /api/v1/tenants/by-slug/{slug}`, needed for the authoritative + real-time slug check (Gen_TNT currently has no such lookup).
- A minimal **Gen_Auth** registration call at signup time — `POST /api/v1/auth/register` (email + password), no tenant scope yet — so Gen_TNT's required `primaryOwnerUserId` is a real user, not a placeholder. Nothing beyond registration; login/session flows stay out of scope.

**Explicitly out of scope (later phases):**
- Plan selection, checkout, Stripe/Razorpay payment (ADR-004).
- Turnstile captcha (module exists in the original; not ported yet).
- Abandoned-signup recovery emails (the original's `RECOVERABLE_STATES` / recovery worker — real feature, deferred, not forgotten).
- Wizard read-model / `wizard.events` (ADR-006).
- Idempotency-key middleware (ADR-008) — Phase 1 relies on the signup form's own duplicate-email/duplicate-subdomain checks instead; revisit if retried-request duplication becomes a real problem.
- RabbitMQ event bus (ADR-002) — Phase 1 has no other service that needs to consume signup events yet. If/when one exists, add it then.

## Architecture

Two entrypoints, same split as the original and as Gen_Auth/Gen_TNT's demo pattern:
- `src/server.ts` — HTTP API.
- `src/worker.ts` — background poller that drives sessions from `PROVISIONING` to `ACTIVE`/`PROVISION_FAILED`.

Module layout (trimmed to Phase 1's modules only):
```
src/
  modules/
    signup/v1/        — controller, service, repo, schema, routes
    verify-email/v1/  — controller, service, routes
  domain/
    enums/            — SignupState
    ports/             — ISignupSessionRepo
  infra/
    persistence/       — Prisma client
    tnt-client/         — Gen_TNT HTTP client (create tenant, get by id, get by slug)
    auth-client/        — Gen_Auth HTTP client (register)
  common/               — token gen/hash, slug normalization, error classes (reimplemented locally — no @cpms/node-common dependency)
```

No `infra/grpc`, `infra/mongo`, `infra/payment`, `infra/messaging`, `infra/cache` (Valkey) — all dropped per the scope/persistence decisions below.

## State machine (Phase 1)

Reduced from the original's 7-state machine — no plan/payment states, since Phase 1 goes straight from email verification to provisioning:

```
STARTED ──(email verified)──> EMAIL_VERIFIED ──(POST to Gen_TNT succeeds)──> PROVISIONING ──(Gen_TNT tenant ACTIVE)──> ACTIVE
   │                                │                                            │
   └──(TTL expired)──> ABANDONED <──┘                                            └──(Gen_TNT job DEAD)──> PROVISION_FAILED
```

- `ABANDONED` and `ACTIVE`/`PROVISION_FAILED` are terminal — no further transitions, matching the original's `TERMINAL_STATES` guard pattern (ADR-001).
- Every service method checks the session's current state before acting, so duplicate/out-of-order requests (e.g. verifying an already-verified token) are rejected explicitly rather than silently double-processed — same principle as the original.

## Data model

`SignupSession` (Postgres via Prisma), trimmed to Phase 1's actual fields — no `selectedPlanCode`, `stripeCheckoutSessionId`, `captchaToken`, `abandonedRecoverySent`/`recoveryAttempts` (those return with their owning phase):

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `email` | varchar(255) | |
| `companyName`, `fullName`, `phone` | varchar | |
| `source`, `referralCode`, `utmSource`, `utmMedium`, `utmCampaign` | varchar | optional attribution fields, straight port |
| `desiredSubdomain` | varchar(63) | normalized slug |
| `state` | enum (`SignupState`) | replaces original's string `currentStep` — an actual enum column, not a free string |
| `emailVerificationTokenHash` | varchar | null once consumed |
| `emailVerifiedAt` | timestamptz | null until verified |
| `resumeTokenHash` | varchar | new: original kept resume tokens in Valkey only; Phase 1 needs this in Postgres since Valkey is dropped |
| `authUserId` | UUID | new: Gen_Auth's user id, set at signup time (§ Gen_TNT & Gen_Auth integration) — becomes Gen_TNT's `primaryOwnerUserId` |
| `provisioningJobId` | UUID | Gen_TNT's job id, set once POST /api/v1/tenants succeeds |
| `provisionedTenantId` | UUID | Gen_TNT's tenant id |
| `lastProvisioningError` | text | surfaced to the frontend on `PROVISION_FAILED` |
| `expiresAt` | timestamptz | drives the abandon-sweep |
| `createdAt`, `updatedAt` | timestamptz | |

`ipAddress`, `userAgent`, `country` are dropped from Phase 1 — they were captured for abuse/recovery-analytics purposes tied to features not yet built (captcha, recovery emails). Re-add when those phases land.

## API

```
POST /api/v1/signup
  body adds `password` (new field vs the original — needed for the Gen_Auth registration call)
  → 201 { sessionId, message }
  Validations (in order): disposable-domain block → reserved-subdomain block →
  duplicate-active-email → duplicate-subdomain (local sessions) → duplicate-subdomain
  (authoritative, via Gen_TNT by-slug lookup) → referral-code validity (if provided) →
  Gen_Auth register() call (email, password) — failure here (incl. Gen_Auth's own
  "email already registered") aborts the whole request; no SignupSession row is created

GET /api/v1/signup/check-subdomain?value=...
  → { available, normalized, valid }
  Same two-layer check as POST /signup's validation (local sessions + Gen_TNT by-slug),
  without creating anything — powers live-typing UX.

GET /api/v1/signup/verify-email?token=...
  → 200 { sessionId, email, nextStep: "PROVISIONING" }
  409 if already verified; 400/404 if token invalid or expired.

GET /api/v1/signup/resume?token=...
  → 200 { sessionId, email, state, companyName, desiredSubdomain }

GET /api/v1/signup/{sessionId}
  → 200 { sessionId, state, provisionedTenantId?, lastProvisioningError? }
  Polling target for the frontend while PROVISIONING is in flight.
```

## Gen_TNT & Gen_Auth integration

Three touchpoints — one small addition needed on the Gen_TNT side first, one new call to Gen_Auth:

1. **New Gen_TNT endpoint**: `GET /api/v1/tenants/by-slug/{slug}` → `200` (tenant exists) or `404` (free). Gated by the same `X-Internal-Secret` filter as every other Gen_TNT endpoint. This becomes Gen_REG's authoritative slug source, replacing the original's gRPC `isTenantSlugTaken` call.
2. **Gen_Auth registration**: during `POST /api/v1/signup` (state still `STARTED`), Gen_REG calls Gen_Auth's `POST /api/v1/auth/register` with `{ email, password }` (no `tenantId` — registers against Gen_Auth's single-tenant sentinel scope, since no tenant exists yet). The returned `userId` is stored as `SignupSession.authUserId`. This resolves Gen_TNT's required `primaryOwnerUserId` with a real user instead of a placeholder — decided over deferring/reconciling later, since the call is cheap and keeps Phase 1 free of reconciliation debt.
3. **Provisioning handoff**: on `EMAIL_VERIFIED` → `POST /api/v1/tenants` on Gen_TNT with `{ name: companyName, slug: desiredSubdomain, primaryOwnerUserId: authUserId, idempotencyKey: sessionId }`, store the returned `provisioningJobId`, transition to `PROVISIONING`.
4. **Completion polling**: `worker.ts` runs a scheduled sweep over sessions in `PROVISIONING`, calling `GET /api/v1/tenants/{provisionedTenantId}` on Gen_TNT — `ACTIVE` status moves the session to `ACTIVE`; if Gen_TNT's own job reaches `DEAD` (checked via `GET /api/v1/provisioning/jobs/{jobId}`), the session moves to `PROVISION_FAILED` with `lastProvisioningError` set from the job's `lastError`.

Gen_Auth's own `RegisterRequest` requires a `@ValidPassword`-annotated password — Gen_REG's signup schema's `password` field must satisfy whatever Gen_Auth's password policy is (delegated entirely to Gen_Auth's validation; Gen_REG doesn't duplicate the rule).

## Error handling

Reuses the original's error taxonomy (domain-specific error classes: `SignupEmailAlreadyRegisteredError`, `SignupTenantSlugTakenError`, `SignupDomainBlockedError`, `EmailVerificationTokenInvalidError`, `EmailVerificationTokenExpiredError`, `EmailAlreadyVerifiedError`, `ResumeTokenNotFoundError`, `SignupSessionNotFoundError`), reimplemented locally instead of imported from `@cpms/node-common` (same move Gen_Auth/Gen_TNT made for their own dependencies). Each maps to a 4xx status via a central error-handler middleware, matching the controller→service→repo layering ADR-001 describes.

Gen_Auth's registration conflict (an email already registered directly against Gen_Auth, outside any Gen_REG session) maps to the same `SignupEmailAlreadyRegisteredError` as the local duplicate-session check — one error class, two possible sources. A Gen_Auth call failing for any other reason (network, 5xx) aborts signup with a 502, since a `SignupSession` with no corresponding `authUserId` would be unrecoverable dead state.

## Testing

- Unit tests per service method (state-guard rejections, validation-order, token hash/expiry logic) — no real Postgres needed, repo mocked.
- Integration tests against a real Postgres (Testcontainers, matching Gen_TNT's own precedent) covering the full `STARTED → EMAIL_VERIFIED → PROVISIONING → ACTIVE` path with a mocked Gen_TNT HTTP client.
- One real end-to-end smoke script (matching Gen_TNT's `smoke-provisioning.sh` pattern): signup → verify → poll until `ACTIVE`, run against a live Gen_REG + Gen_TNT + mock-step-server stack.

## Explicitly out of scope (repeated for emphasis, not a copy-paste error)

Payment, captcha, wizard read-model, abandoned-recovery emails, RabbitMQ, MongoDB, Valkey. All real features from the original — deferred, not dismissed. Each gets its own phase when picked up.
