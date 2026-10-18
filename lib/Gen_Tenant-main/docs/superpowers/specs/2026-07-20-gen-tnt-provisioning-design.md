# Gen_TNT: Tenant Provisioning Microservice — Design

## Goal

Generalize CPMS-Platform's `tnt-svc` (Java/Spring Boot tenant provisioning service) into a standalone, reusable, configurable service — `Gen_TNT` — following the same pattern already established for Gen_Auth: a genericized fork of prior CPMS logic, packaged as an embeddable library for any Java project in the organisation, plus a thin deployable reference app for non-Java consumers (like PMP) to call over HTTP.

This is a deliberate exception to `docs/HLD.md`'s modular-monolith mandate (see `docs/ADRs/ADR-01-modular-monolith.md`'s "reopen this decision if... a different runtime that can't live in the same Node process"), mirroring the same exception already made for Gen_Auth.

## Module layout

Mirrors Gen_Auth's exact split (`gen-auth-starter` / `gen-auth-demo`):

- **`gen-tnt-starter`** — the reusable library. `java-library` + `maven-publish` Gradle plugins, published to GitHub Packages under the org (same mechanism as `gen-auth-starter`'s `com.example:gen-auth-starter` coordinate). Any Java/Spring Boot project adds it as a dependency and gets the Tenant aggregate, state machine, and provisioning saga auto-configured as Spring beans — in-process, no network hop, no separate deployment.
- **`gen-tnt-demo`** — thin reference app (`implementation project(':gen-tnt-starter')`, plus `spring-boot-starter` directly on its own compile classpath for the same `java-library`-doesn't-expose-transitive-Spring-Boot-deps reason `gen-auth-demo` needs it). This is what gets deployed standalone and is what the rest of this spec's HTTP API (`/api/v1/tenants`, `/api/v1/provisioning`, webhook step callbacks) describes — the form PMP's `apps/api` actually talks to, same as it talks to `gen-auth-demo` today.

Everything below (Architecture, Domain model, Step pipeline, Data flow, Error handling) describes the **shared logic** living in `gen-tnt-starter` — `gen-tnt-demo` just exposes it over HTTP.

## Scope

Two independent services are being extracted from the old `tnt-svc` + `tbr-svc` split, each generalized separately:

- **Gen_TNT** (this spec) — tenant CRUD + the provisioning saga. Java/Spring Boot, at `Gen_MS/Gen_TNT`.
- **Gen_TBR** (separate, later spec) — branding/custom-domain. Node/Express/Prisma, at `Gen_MS/Gen_TBR`. Not covered here.

### Gen_TNT v1 scope — explicitly IN

- Tenant aggregate: create, read, suspend, reactivate, cancel, purge.
- Tenant status lifecycle (state machine).
- The provisioning saga: an ordered, **configurable** pipeline of steps run against a newly-created tenant, with retry-on-failure and timeout-triggered best-effort compensation.
- Provisioning job/step introspection (list failed, get job, get steps, manual retry).

### Explicitly OUT of v1 scope

Ported from `tnt-svc` but dropped for v1 (all were bundled into the old service alongside provisioning, per the CPMS decision matrix — none of this is core to "provisioning"):

- Quotas, feature flags, region residency enforcement, billing-state snapshots, export/compliance with audit hash-chain, support-access requests.
- `tnt-svc`'s own `CustomDomain` model — that's `Gen_TBR`'s job, not duplicated here.
- gRPC server (was used for a `reg-svc` region-lookup integration that doesn't apply here).
- RabbitMQ / message broker of any kind — replaced by HTTP webhooks (see Architecture).
- `io.cpms.common.security.RequirePermission` — replaced by a simple internal shared-secret header, matching Gen_Auth's `INTERNAL_SERVICE_SECRET` pattern.

## Source reference

- `CPMS-Platform/apps/tnt-svc` (~200 Java files) — the code being genericized.
- `CPMS-Platform/PMP_Service_Reuse_Decision_Matrix.md` and `PMP_Service_Genericization_Tradeoffs.md` — the audited port-value assessment this design follows.

### What's actually worth porting (per the audit)

The saga machinery — `ProvisioningSagaOrchestrator`, `ProvisioningAggregatorService`'s fan-in logic, the transactional-outbox-relay _pattern_ (not its RabbitMQ-specific implementation), and the retry/timeout scheduler thresholds — is the ~35-45% of `tnt-svc` actually worth keeping. Everything else (governance entities, gRPC, cross-service message routing) is CPMS-specific plumbing to strip.

### Audited fact this design is built around

`tnt-svc`'s saga is **retry-primary, not a true compensating saga**. `compensate()` only ever fires on timeout (via `ProvisioningTimeoutScheduler` → `handleTimeout`), never on an explicit step failure (those go through `RetryRecoveryScheduler` instead). Of the four old step executors, three `compensate()` implementations are log-only no-ops ("left intact for audit"); only the one _local_ step (`SchemaBootstrapStepExecutor`, writing directly to this service's own tables) has real delete-based compensation. Gen_TNT keeps this same honest split — retry is the primary recovery path; compensation is optional-per-step and skipped entirely if a step has no compensate URL configured, rather than pretending to roll back what it can't.

## Architecture

Java 21, Postgres 15, Redis — same stack as Gen_Auth. No message broker (RabbitMQ dropped; async step completion uses HTTP callbacks instead).

Two API groups, both gated by an `X-Internal-Secret` header (mirrors Gen_Auth's `INTERNAL_SERVICE_SECRET` — this is a system-to-system service, no end-user-facing auth of its own):

- `/api/v1/tenants` — Tenant CRUD.
- `/api/v1/provisioning` — job/step introspection and manual retry.

### Domain model

- **Tenant**: `id, slug, name, status, region (plain string, not a fixed enum), primaryOwnerUserId, provisioningJobId, timestamps`. Drops all billing/plan/quota fields from the old model — out of scope.
- **TenantStateMachine**: portable as-is from `tnt-svc`, trimmed to `PROVISIONING → ACTIVE → SUSPENDED/CANCELLED → PURGED` (drops `TRIAL`/`PAST_DUE` — billing-specific; add back if a later phase needs them).
- **ProvisioningJob**: `id, tenantId, status (PENDING/IN_PROGRESS/COMPLETED/FAILED/DEAD), retryCount, lastError, startedAt/completedAt/expiresAt/createdAt, context (JSON)`. The old `adminRoleId` field (which existed only to shuttle one value between two hardcoded steps) generalizes into `context` — any step can read/write it via its request/callback payload. `expiresAt` is set at job creation as `startedAt + PROVISIONING_TIMEOUT_MINUTES` (configurable, e.g. env-driven, default 10 minutes — matching the old orchestrator's per-tenant lock TTL order of magnitude).
- **ProvisioningStep**: `id, jobId, stepName, status (PENDING/IN_PROGRESS/COMPLETED/FAILED), retryCount, errorMessage, startedAt/completedAt`. Same shape as before.
- **SlugValidator**: ported as-is (`^[a-z][a-z0-9-]*[a-z0-9]$`, 3-63 chars) — confirmed zero CPMS coupling in the audit, no changes needed.

### Step pipeline — configuration, not code

The old fixed 4-step Java enum (`SCHEMA_BOOTSTRAP`/`ADM_BOOTSTRAP`/`TBR_BOOTSTRAP`/`AUTH_BOOTSTRAP`) with a hardcoded `switch`-based orchestrator becomes an ordered config list (`application.yaml`/env-driven): each entry has `name`, `url`, `mode` (`sync`|`async`), `retryable`, optional `compensateUrl`.

v1 runs the pipeline **strictly sequentially** — no parallel-fan-out group. The old system's only parallelism was two steps (ADM + TBR bootstrap); a generic consumer can't be assumed to need concurrent steps, and sequential is simpler to reason about and debug. A `parallel: true` grouping flag is a clean later addition if a real use case needs it — not built now.

Per step, the orchestrator:

1. Acquires the per-tenant Redis lock (TTL-bound, same shape as the old `DistributedLockService`).
2. POSTs `{tenantId, jobId, stepName, context}` to the step's configured URL.
3. **Sync**: response status decides success/fail immediately; lock released, advance to next step.
4. **Async**: marks the step `IN_PROGRESS`, releases the lock, and waits. The target service does its work, then calls back `POST /internal/provisioning/jobs/{jobId}/steps/{stepName}/callback` with `{status, context, error?}`, carrying a **per-job callback token** (generated at job creation, required on every callback — this is a new security requirement the old design didn't need, since its RabbitMQ reply queue was infra-trusted and these HTTP callbacks are externally reachable). Gen_TNT re-acquires the lock, merges `context`, advances.

## Data flow

1. Consumer (e.g. PMP's `apps/api`) calls `POST /api/v1/tenants` with `{name, slug, ownerEmail, region?, idempotencyKey?}` + `X-Internal-Secret`.
2. Gen_TNT creates `Tenant` (`PROVISIONING`) + `ProvisioningJob` (`PENDING`) + one `ProvisioningStep` per configured pipeline entry (`PENDING`). Returns `202` + `jobId`. A repeated call with the same `idempotencyKey` returns the existing job/tenant instead of creating a second one.
3. Orchestrator immediately starts driving the step list in order (see step mechanics above).
4. All steps `COMPLETED` → `Tenant` → `ACTIVE`, job → `COMPLETED`.
5. Two distinct failure paths:
   - **Explicit failure** (sync non-2xx, sync connection error, or async callback with `status: failed`) → step/job `FAILED` → `RetryRecoveryScheduler` resets `FAILED` steps to `PENDING` and re-drives from there, capped at `maxRetries` (default 3, configurable), then `DEAD`.
   - **Silence** (async step never calls back before `job.expiresAt`) → `ProvisioningTimeoutScheduler` walks completed steps in reverse order, POSTing to each step's _optional_ `compensateUrl` — skipped entirely if none configured. Job ends `DEAD` after the compensate walk; no retry follows a timeout.

## Error handling

- **Idempotent create**: duplicate `POST /tenants` with the same `idempotencyKey` returns the existing job/tenant, no second saga run.
- **Duplicate slug**: `409`, checked before job creation — no wasted saga run.
- **Invalid state transition**: `TenantStateMachine` rejects e.g. suspending an already-`PURGED` tenant — `409` with attempted + current state in the body.
- **Step callback auth**: callback token is per-job; a callback with a stale/reused token, or for a step that's already `COMPLETED`, or for a step name not in this job's pipeline, is rejected with `409` and logged — not silently reapplied. Guards against a slow/duplicate retry from a flaky step target, or a misconfigured/buggy step target, corrupting state.
- **Lock contention**: if the per-tenant lock is already held when a scheduler tick wants to touch the same job, that tick is skipped rather than blocking — avoids scheduler pile-up under slow step targets.
- **Unreachable step URL** (sync call can't connect at all): treated as explicit failure → retry path, not the silence/timeout path, since a connection error is a clear signal, not an absence of one.
- **Max retries exhausted**: job → `DEAD`, tenant stays in whatever state it was (usually still `PROVISIONING`). A `DEAD` job needs a human, not auto-cleanup — `GET /api/v1/provisioning/failed` surfaces these (ported as-is from the old `ProvisioningController`).

## Testing

- **Unit** (no Spring context): `TenantStateMachine` transition table (every valid/invalid pair), `SlugValidator` regex edge cases, `ProvisioningJob`/`ProvisioningStep` status-transition guards, callback-token validation logic.
- **Integration** (real Postgres + Redis, no mocked DB — matching Gen_Auth's own testing philosophy): a lightweight local HTTP stub standing in for step-target URLs, driving the orchestrator through:
  - all-sync-steps-succeed → `ACTIVE`
  - sync-step-fails → retry scheduler recovers → eventually `ACTIVE`
  - async-step-callback-succeeds → advances correctly, `context` merges
  - async-step-callback with wrong/stale token → rejected, job state unchanged
  - async-step never calls back → timeout scheduler fires compensate walk on completed steps, job `DEAD`
  - retries exhausted → job `DEAD`, no further scheduler action
  - duplicate create with same `idempotencyKey` → same job/tenant returned
  - concurrent retry-scheduler + timeout-scheduler tick on the same job → lock contention handled, no double-processing
- **Manual smoke script** (mirrors `apps/api/scripts/smoke-auth.mjs`): create a tenant against a real running Gen_TNT + two dummy step-target servers → poll job status → confirm `ACTIVE`; a second run confirming duplicate-slug rejection and a forced-failure step recovering via retry.

## Explicitly out of scope (carried forward)

- Gen_TBR (branding/custom-domain) — separate service, separate spec.
- Everything listed under "Explicitly OUT of v1 scope" above: quotas, feature flags, region residency, billing snapshots, export/audit, support access, gRPC, message broker.
- Any consuming project actually embedding `gen-tnt-starter` in-process for v1 — the module split exists from day one (see Module layout), but no Java consumer is wired up to embed it yet. PMP consumes only `gen-tnt-demo` over HTTP, same as Gen_Auth.
