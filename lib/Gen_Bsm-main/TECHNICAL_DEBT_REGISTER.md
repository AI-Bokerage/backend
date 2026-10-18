# Technical Debt Register

Documented compromises, deferrals, and known gaps — why they exist, their
actual impact, and a recommendation. This is a record of honest tradeoffs
made during the bsm-core extraction, not a bug tracker for undiscovered
defects.

## 1. `BillingDashboardServiceImpl` uses raw `JdbcTemplate` instead of a port

**What**: `BillingDashboardServiceImpl` (lives in `bsm-svc`, not `bsm-core`)
queries the database directly via `JdbcTemplate` for dashboard aggregation
queries, rather than going through a `bsm-core` repository port.

**Why deferred**: dashboard aggregation queries are read-heavy,
cross-aggregate (joining subscription, invoice, and payment data) SQL that
doesn't map cleanly onto the existing per-aggregate repository ports
without either (a) a new dashboard-specific query port with SQL-shaped
method signatures baked into its contract, or (b) N+1 calls across multiple
existing ports reassembled in application code. Neither was judged worth
the design effort during the extraction phases, since the dashboard feature
itself was not the focus of the extraction.

**Impact**: `BillingDashboardService`'s interface lives in `bsm-core`'s
public API surface, but its only real implementation is host-specific and
not portable — a second consumer (like `bsm-demo`) cannot get working
dashboard queries without writing their own SQL against their own schema.
This is why `BillingDashboardService` is classified **Experimental** tier
in `PUBLIC_API.md`, not Public Library API.

**Recommendation**: design a proper `BillingDashboardQueryPort` (or set of
smaller, composable read ports) before promoting this out of Experimental
tier. This is a 2.x candidate per `ROADMAP.md`, not a 1.0.0 blocker, since
the interface can remain Experimental indefinitely without breaking anyone.

## 2. `CommercialEngineServiceImpl` depends on `io.cpms.common.plan.PpmTierRegistry`

**What**: `CommercialEngineServiceImpl` (lives in `bsm-svc`, not
`bsm-core`) depends directly on a CPMS-platform-specific
`PpmTierRegistry` class, which is not part of `libs/security-spi` or any
other portable abstraction.

**Why deferred**: `PpmTierRegistry` encodes CPMS-specific plan-tier
business rules that don't have an obvious framework-agnostic port
abstraction without a larger redesign of how tier metadata is sourced. This
was evaluated during Phase 3 (see the Phase 3 completion report) and judged
out of scope for the aggregate-migration phase.

**Impact**: same shape as item 1 — `CommercialEngineService`'s interface is
in `bsm-core`, but its real implementation isn't portable. Classified
**Experimental** tier in `PUBLIC_API.md`.

**Recommendation**: same as item 1 — a 2.x candidate requiring a proper
port design for tier metadata resolution, not a 1.0.0 blocker.

## 3. `SubscriptionAddOnService` / `SubscriptionChangeService` not auto-configured

**What**: unlike the ten other application services, these two are not
wired by any `bsm-spring-boot-starter` `@AutoConfiguration` class — a
consumer must manually construct and register them as beans (documented in
`docs/examples/add-on-purchase.md` and `docs/examples/plan-upgrade.md`).

**Why deferred**: these two services were added after the initial starter
design (Phase 6) and, at the time, weren't judged essential enough to
justify designing their anchor-port sets and updating
`BsmAggregateSpec`/`BsmPortAvailabilityValidator` before the starter
shipped. This was a scope-management decision, not a technical blocker —
both services work correctly today, they just require one extra manual
`@Bean` method.

**Impact**: minor consumer friction — anyone using add-on purchase or plan
change functionality needs to read the manual-wiring docs rather than
getting it for free. No functional gap; purely a starter-completeness gap.

**Recommendation**: a 1.x candidate per `ROADMAP.md` — purely additive
(new `@AutoConfiguration` classes), no breaking change required.

## 4. `BsmAuthorizationPort` has zero implementations

**What**: `BsmAuthorizationPort` exists as a `domain/port` interface, but a
repo-wide search found no class anywhere in `bsm-svc` or `bsm-demo` that
implements it. `ARCHITECTURE_CERTIFICATION.md` had previously stated
`TenantScopePort`/`BsmAuthorizationPort` both map to
`BsmTenantScopeEnforcer`, but `BsmTenantScopeEnforcer` implements only
`TenantScopePort` — this was a documentation inaccuracy, corrected during
the Phase 7 port audit (see `PORT_REFERENCE.md`).

**Why deferred**: unclear — this appears to be a port that was designed but
never actually wired to a real authorization mechanism, possibly superseded
by `TenantScopePort` covering the tenant-isolation need that
`BsmAuthorizationPort` was originally meant to address.

**Impact**: this is a speculative, unimplemented extension point sitting in
a published library's SPI surface — exactly the kind of thing
`CONTRIBUTING.md` asks future contributors not to add. It costs nothing at
runtime (nothing depends on it being implemented) but is a source of
confusion for anyone reading the port list and wondering what implements
it.

**Recommendation**: either give `BsmAuthorizationPort` a real, distinct
purpose (something `TenantScopePort` doesn't already cover) and implement
it, or remove it entirely. This is flagged as a 2.x candidate in
`ROADMAP.md` since removing a port is a breaking change; it is not fixed in
this Phase 7 pass because Phase 7's scope explicitly excludes architecture
changes and new abstractions — this is a decision for whoever owns the
next design pass, not something to silently resolve during a
documentation/governance audit.

## 5. `bsm-svc` carries two AWS S3 SDKs (v1 and v2)

**What**: `bsm-svc` declares both `software.amazon.awssdk:s3` (SDK v2) and
`com.amazonaws:aws-java-sdk-s3` (SDK v1), each with real, active usage (2
source files each) — see `DEPENDENCY_AUDIT.md`.

**Why deferred**: unclear from the codebase alone — likely an incremental
migration from v1 to v2 that was never completed, or two separate features
added by different contributors at different times without noticing the
overlap.

**Impact**: increased classpath size, two SDKs' worth of transitive
dependencies, and ongoing maintenance surface (security patches, version
bumps) for functionality that could be served by one. This is a `bsm-svc`
host concern, not a `bsm-core`/starter API concern — it doesn't affect
consumers of the library at all.

**Recommendation**: consolidate onto AWS SDK v2 (the actively maintained
one) in a dedicated `bsm-svc` cleanup PR. Not fixed during this audit
because it requires touching adapter source code (choosing which SDK the
storage adapter should standardize on), which is outside Phase 7's
dependency-declaration-only scope.

## 6. `ReconciliationProperties.intervalMs` bound but never consumed

**What**: `ReconciliationProperties` (bsm-svc) has a `intervalMs` field
that binds correctly from `payment.reconciliation.interval-ms` but is never
read by `PolicyBeansConfig`'s `reconciliationPolicy` bean — see
`CONFIGURATION_REFERENCE.md`.

**Why deferred**: discovered during the Phase 7 configuration audit itself;
not previously known.

**Impact**: low — the property is harmlessly bound and ignored. Anyone
setting `payment.reconciliation.interval-ms` expecting it to control
reconciliation scheduling frequency would be surprised it has no effect.

**Recommendation**: either wire it into the actual reconciliation scheduler
(if scheduling frequency should be configurable) or remove the unused
field. A small, low-risk `bsm-svc`-only fix — good candidate for the next
routine `bsm-svc` maintenance pass, not urgent enough to block 1.0.0.

## 7. Scheduler `@Value` code defaults disagree with shipped `application.yaml` defaults

**What**: `bsm.schedule.executor-interval-ms` and
`bsm.provider-sync.retry-interval-ms` have `@Value` fallback defaults in
code (300000ms, 600000ms) that don't match the values actually shipped in
`application.yaml` (10000ms, 60000ms) — see `CONFIGURATION_REFERENCE.md`.
The yaml value wins today since it's present, so there's no current
behavioral bug, only a latent one.

**Why deferred**: discovered during the Phase 7 configuration audit;
harmless as long as `application.yaml` continues to ship with the repo.

**Impact**: none today. Would silently change scheduler timing (10s → 300s,
60s → 600s) if `application.yaml`'s relevant keys were ever removed without
someone noticing the code-level defaults don't match intent.

**Recommendation**: align the `@Value` code defaults with the intended
production values (10000ms/60000ms) so the yaml file becomes redundant
documentation rather than the only source of truth. Low-risk `bsm-svc`-only
fix, good candidate for routine maintenance.
