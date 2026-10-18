# Gen_ADM Phase 2: Offboarding Saga — Design Spec

## Goal

Add tenant-scoped user offboarding to Gen_ADM: a resumable, retry-only job/step
saga triggered by a REST call, with session revocation as a mandatory built-in
step and everything else pluggable.

## Why

Source-audited from CPMS-Platform's `adm-svc` (`docs/source-audit-notes.md`).
Decision matrix names `OffboardingSagaOrchestrator` + `StepExecutor` (job/step
tracking, outbox-after-commit, linear-backoff retry) as a real extraction
target, but flags two blockers: it's a retry saga with no compensation logic,
and its fully-coded `HttpAuthSessionRevocationGateway` is never actually called
by the step executor — a dead-code gap. This spec ports the job/step/retry
shape, fixes the session-revocation gap by making revocation a mandatory
step-zero, and drops the CPMS-specific step bodies (task reassignment via
PMT-SVC, ticket reassignment via SDS-SVC don't travel — Category B, per
`PMP_Service_Genericization_Categories.md`) in favor of a pluggable port.

## Scope

**In scope (this spec, Phase 2):**
- `OffboardingJob` / `OffboardingStep` schema, tenant-scoped, RLS-protected
  (reusing Phase 1's `TenantContextAspect`).
- Mandatory built-in step 0: session revocation, via a required
  `SessionRevocationGateway` port — no default no-op, consumer must implement.
- Pluggable `OffboardingStepHandler` port — consumer registers zero or more
  additional steps as Spring beans; no built-in step bodies beyond revocation.
- Optional `OffboardingEventPublisher` port, no-op default
  (`@ConditionalOnMissingBean`) — a minimal hook, not an outbox/broker
  integration.
- Idempotent, resumable `OffboardingStepExecutor` — skips COMPLETED steps,
  resumes from the first non-COMPLETED step, safe to invoke repeatedly for the
  same job.
- Retry-only failure model: linear backoff `min(5 × attemptCount, 30)`
  minutes, capped by `maxAttempts`. No compensation, no rollback — matches
  source behavior, explicitly not a saga-with-undo.
- In-process `afterCommit` kickoff (`TransactionSynchronizationManager`) —
  same mechanism as source, no external scheduler/queue required for the
  common case.
- REST controllers for initiate/status/retry, reusing Phase 1's
  `PermissionChecker`, `GenAdmPrincipal`, and exception taxonomy.
- `gen-adm-demo` reference: a sample `OffboardingStepHandler` and
  `SessionRevocationGateway` implementation + smoke test.

**Out of scope (explicitly deferred):**
- Compensating/rollback logic — retry-only, as in source.
- A scheduled retry/timeout sweeper (source's `OffboardingRetryScheduler` /
  `OffboardingTimeoutScheduler`) — the `afterCommit` kickoff handles the
  common path; a consumer needing stuck-job sweeping can poll
  `OffboardingJobRepository` themselves via the exposed status API. Revisit if
  a real consumer hits stuck jobs in practice.
- Any built-in step body (task reassignment, ticket reassignment, notification
  fanout) — 100% consumer-supplied via the handler port.
- Cross-service event bus / outbox table — `OffboardingEventPublisher` is a
  synchronous in-process hook only.

## Architecture

Same starter/demo split as Phase 1, same package root (`com.example.admsvc`),
additive to the existing autoconfiguration — no changes to RBAC's public
surface.

```
gen-adm-starter/
  domain/port/
    OffboardingStepHandler.java       (pluggable, 0+ beans)
    SessionRevocationGateway.java     (mandatory, consumer must provide)
    OffboardingEventPublisher.java    (optional, no-op default)
  infrastructure/persistence/entity/
    OffboardingJobEntity.java
    OffboardingStepEntity.java
  infrastructure/persistence/repository/
    OffboardingJobRepository.java
    OffboardingStepRepository.java
  application/service/
    OffboardingService.java           (initiate/status/retry)
  application/impl/
    OffboardingServiceImpl.java       (tenant-scoped, @Transactional)
    OffboardingStepExecutor.java      (afterCommit-triggered, resumable)
  api/dto/{request,response}/...
  api/controller/OffboardingController.java
gen-adm-demo/
  ...sample SessionRevocationGateway + OffboardingStepHandler + smoke test
```

`OffboardingServiceImpl.initiate(...)` builds one `OffboardingJobEntity` plus
one `OffboardingStepEntity` per registered step (session revocation always
first, sequence 0; remaining steps in the order their `OffboardingStepHandler`
beans are supplied, sequence 1..N), then registers an `afterCommit`
synchronization that calls `OffboardingStepExecutor.execute(jobId)` — matching
source's exact `TransactionSynchronizationManager` pattern, so the executor
never runs against a job row the transaction hasn't committed yet.

## Components

- **`SessionRevocationGateway`** (port, mandatory, no default bean) — single
  method `revoke(GenAdmPrincipal principal, UUID userId)`. Always step
  sequence 0. Fixes the source's dead-code gap: here it is unconditionally
  wired into the step list, not an optional call site nothing invokes.
- **`OffboardingStepHandler`** (port, pluggable) — `String stepName()` +
  `void handle(UUID tenantId, UUID userId, GenAdmPrincipal initiatedBy)`.
  Consumer supplies zero or more Spring beans; `OffboardingServiceImpl`
  collects them via constructor injection (`List<OffboardingStepHandler>`)
  and orders steps by bean list order after the mandatory revocation step.
- **`OffboardingEventPublisher`** (port, optional) — `void onCompleted(...)` /
  `void onFailed(...)`, no-op default via `@ConditionalOnMissingBean`. Not an
  outbox; a synchronous in-process hook for a consumer wanting to react
  (e.g. fire their own event) without Gen_ADM owning a broker dependency.
- **`OffboardingJobEntity`** — adapted from source's schema
  (`tenantId`, `userId`, `initiatedBy`, `reason`, `status`, `attemptCount`,
  `maxAttempts`, `nextRetryAt`, timestamps). Drops source's `requestPayload`/
  `currentStep` fields — step progress lives on `OffboardingStepEntity` rows,
  not duplicated onto the job.
- **`OffboardingStepEntity`** — adapted from source (`jobId`, `tenantId`,
  `userId`, `sequence` (int, execution order — 0 always session revocation),
  `stepName` (String, not source's closed `OffboardingStep` enum — step names
  are consumer-defined via the pluggable port), `status`, `attemptNumber`,
  `errorMessage`, timestamps). Unique constraint on `(jobId, sequence)`.
- **`OffboardingStepExecutor`** — `execute(UUID jobId)`: loads the job and its
  steps ordered by sequence; skips any step already COMPLETED; runs the first
  non-COMPLETED step. On handler success, marks it COMPLETED and continues to
  the next step in the same invocation. On handler exception, marks the step
  FAILED, increments the job's `attemptCount`, sets
  `nextRetryAt = now + min(5 × attemptCount, 30) minutes`, sets job status
  FAILED, and stops (no further steps run until re-invoked). If
  `attemptCount >= maxAttempts`, job status becomes PERMANENTLY_FAILED instead
  and `nextRetryAt` is cleared — no further retry is possible.
  Re-invoking `execute(jobId)` for a FAILED job (below `maxAttempts`) resumes
  from the first non-COMPLETED step; this is what "resumable" means here —
  there is no separate resume method.
- **`OffboardingController`** — `POST /api/v1/offboarding` (initiate),
  `GET /api/v1/offboarding/{jobId}` (status), `POST /api/v1/offboarding/{jobId}/retry`
  (manually re-invoke `execute` — the only way a FAILED job progresses again,
  since the scheduled sweeper is out of scope). All three call
  `PermissionChecker.require(principal, code)` first, same as Phase 1's
  controllers; permission codes are consumer-configured, not built in.

## Data Flow & Error Handling

1. Caller hits `POST /api/v1/offboarding` with `{ userId, reason }`.
   Controller resolves `GenAdmPrincipal` from `SecurityContextHolder`,
   calls `PermissionChecker.require(principal, "offboarding:initiate")`.
2. `OffboardingServiceImpl.initiate(...)` (`@Transactional`, tenant-scoped via
   `TenantContextAspect`) inserts one `OffboardingJobEntity` (status PENDING)
   and one `OffboardingStepEntity` per step (sequence 0 = session revocation,
   PENDING; sequence 1..N = registered handlers, PENDING), then registers an
   `afterCommit` synchronization.
3. On commit, `OffboardingStepExecutor.execute(jobId)` runs in-process:
   session revocation first, then each handler in order. Any exception from a
   step is caught inside the executor (never propagates to the caller — the
   initiating HTTP request already returned 202/200 before this runs).
4. Success path: all steps COMPLETED → job status COMPLETED, `completedAt`
   set, `OffboardingEventPublisher.onCompleted(...)` called.
5. Failure path: a step throws → that step FAILED with `errorMessage` set,
   job FAILED, `nextRetryAt` set per the backoff formula,
   `OffboardingEventPublisher.onFailed(...)` called. No rows already
   COMPLETED are re-run.
6. Retry: `POST /api/v1/offboarding/{jobId}/retry` re-invokes
   `OffboardingStepExecutor.execute(jobId)` synchronously (not via
   `afterCommit` — there's no new job-row mutation to wait on) and returns the
   resulting job status. Calling retry on a COMPLETED or PERMANENTLY_FAILED
   job is a no-op that just returns current status (validated up front,
   not treated as an error — matches Phase 1's REST error-taxonomy convention
   of only raising `GenAdmConflictException` for genuine state conflicts).
7. Cross-tenant job lookups (status/retry on a job belonging to a different
   tenant) surface as 404 (`GenAdmNotFoundException`), never 403 — same
   anti-enumeration convention as Phase 1.
8. One honest limitation, matching Phase 1's experience: any Testcontainers-
   based test in this project has so far only been verified by code review in
   this sandbox (confirmed Docker-detection limitation, not a code defect).
   This plan will carry the same caveat rather than assume it resolves itself.

## Testing

- Unit tests (Mockito, no Testcontainers) for `OffboardingStepExecutor`:
  resumability (a job with step 0 COMPLETED and step 1 PENDING re-invoked only
  runs step 1), backoff math at attempt boundaries (1st, 5th, 6th attempt vs.
  `maxAttempts`), PERMANENTLY_FAILED transition, and that a FAILED step's
  exception never propagates out of `execute(...)`.
- Unit tests for `OffboardingServiceImpl.initiate(...)`: step-zero is always
  session revocation regardless of registered handler order; `afterCommit`
  synchronization is registered (verified via
  `TransactionSynchronizationManager`, matching Phase 1's own test patterns
  for transactional side effects).
- Unit test confirming `SessionRevocationGateway` has no default bean (startup
  fails without a consumer-supplied implementation) — a compile/context-load
  assertion, directly closing the source's dead-code gap.
- Controller tests (MockMvc standalone, matching Phase 1's `RoleControllerTest`
  pattern): permission-check-required on all three endpoints, 404 on
  cross-tenant job access, retry no-op on terminal states.
- Integration test (Testcontainers Postgres, same caveat as Phase 1's RLS
  tests): full initiate → afterCommit → executor → COMPLETED flow against a
  real DB, and a forced-failure step proving `nextRetryAt`/`attemptCount`
  persist correctly and a subsequent manual `execute(jobId)` resumes rather
  than re-running COMPLETED steps.
