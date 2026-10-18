# Roadmap

## Where we are

`ppm-core` extraction is **complete**. Every reusable PPM business aggregate has been migrated out of `ppm-svc` one aggregate (or dependency-aware batch) at a time, following the checklist in [ADR-002](../ppm-svc/docs/adr/ADR-002-ppm-core-extraction-pattern.md). Every migration was verified against `ppm-svc`'s full test suite before moving to the next; the architecture has not required a single ADR revision across fourteen aggregates/composite services (eleven top-level packages) and four batches.

**ADR-002 remained sufficient. No architectural changes were required throughout the entire extraction project.**

### Migrated (14 aggregates / composite services, across 11 top-level packages)

Plan, PlanVersion, Module, AddOn, Entitlement, PromoCode, PlanAddOn, PlanModule, PlanEntitlement (+ `EntitlementResolver`), PromoCodePlan, CatalogQueryService, PromoValidationService, PlanPrice (+ `PricingResolver`), AddOnPrice.

See [docs/API_INVENTORY.md](docs/API_INVENTORY.md) for the full per-aggregate public surface.

### Repository completion audit (Batch 4)

Performed as part of closing out the pricing migration — the last remaining aggregate batch:

- **Legacy import audit**: zero references to any pre-migration `domain.*`/`application.service`/`application.impl` package remain anywhere in the repository.
- **Dead package cleanup**: `ppm-svc`'s `domain/` tree (model, port, enums, event, exception subpackages) and `application/service`, `application/impl` are fully removed — they held zero files after the pricing migration completed. One stray file, `AuthenticatedUser` (a security value object used only by `SecurityUtils`, misfiled under the legacy `domain.model` package name), was relocated to `application/util` alongside its sole consumer rather than left as orphaned legacy-package residue.
- **Remaining business logic scan**: every remaining class in `ppm-svc` was enumerated and classified as Host Infrastructure, Persistence Adapter, REST Adapter, Security, Messaging, Configuration, or Integration Adapter. **Zero classes classified as remaining business logic.** See the classification table below.
- **Public API audit**: the two new use-case interfaces (`PlanPriceApplicationService`, `AddOnPriceApplicationService`) and the resolver (`PricingResolver`) were added to [docs/API_INVENTORY.md](docs/API_INVENTORY.md) with the same Public API/SPI/Internal classification applied to every prior aggregate — no new type was left undocumented or given an inconsistent visibility treatment.

**`ppm-svc` package classification (post-migration, exhaustive):**

| Package | Classification |
|---|---|
| `api/controller` | REST Adapter |
| `api/dto` (request/response) | REST Adapter |
| `api/mapper` | REST Adapter (DTO ↔ domain mapping) |
| `api/advice` (`GlobalExceptionHandler`) | REST Adapter (domain exception → HTTP status mapping) |
| `api/converter` (5× `StringToXConverter`) | REST Adapter (Spring MVC query-param converters for wire-vocabulary enums) |
| `application/util` (`SecurityUtils`, `AuthenticatedUser`) | Security |
| `config` (Flyway/JPA/Jackson/OpenAPI/Rabbit/Scheduler/Security) | Configuration |
| `infrastructure/persistence` (adapter/converter/entity/mapper/repository) | Persistence Adapter |
| `infrastructure/security` (JWT conversion, authorization filters, Redis role-permission resolver) | Security |
| `infrastructure/web` (`PpmRequestContextFilter`, `RequestContext`) | Host Infrastructure (request correlation ID / client IP, no business logic) |

**Conclusion: `ppm-core` now contains the complete reusable PPM business domain. `ppm-svc` functions purely as the reference host implementation — every remaining class is host infrastructure, an adapter, or configuration.**

## Phase 5 — Library Readiness & Consumer Validation ✅ Complete

Full results in [docs/PHASE_5_READINESS_REPORT.md](docs/PHASE_5_READINESS_REPORT.md). Summary:

- **Consumer validation** — [`ppm-demo`](../ppm-demo), an independent Spring Boot app depending only on `ppm-core`, built and run live end-to-end (not just compiled): create plan, list plans, create module, assign module to plan, create promo code, validate promo (valid + invalid cases). Two real integration findings surfaced (component-scan scoping across aggregates; `CatalogQueryService`'s cross-aggregate dependency footprint when scanning by sub-package) — both resolved via documentation in [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md), neither required a `ppm-core` code change.
- **API audit** — [docs/API_INVENTORY.md](docs/API_INVENTORY.md) re-validated against actual `ppm-demo` usage; zero surprises, zero visibility changes needed before 1.0.
- **Dependency audit** — [docs/DEPENDENCY_AUDIT.md](docs/DEPENDENCY_AUDIT.md) re-validated against a second consumer; no hidden dependency requirement found.
- **Thread safety** — every use-case implementation confirmed stateless (`@RequiredArgsConstructor`, final injected ports only, no mutable instance or static state).
- **SPI, performance, package, and documentation reviews** — all clean; one pre-existing, already-documented asymmetry reconfirmed (`AddOnPriceRepositoryPort` vs. `AddOnPriceApplicationService`).
- **Release engineering** — Go/No-Go recommendation: **Go** on building the Spring Boot starter (informed by the two findings above); **Conditional Go** on publishing, pending `groupId` finalization, a license declaration, and `maven-publish` wiring — none of which requires touching `ppm-core`'s code or architecture.

## Phase 6 — Spring Boot Starter ✅ Complete

[`ppm-spring-boot-starter`](../ppm-spring-boot-starter) built exactly as Phase 5 recommended: it automates what `ppm-demo` proved by hand, not what was guessed in advance.

- **Auto-configuration** (`PpmAutoConfiguration` + `PpmUseCaseAutoConfiguration`) registers one use-case bean per aggregate, gated on all of its required repository ports being present (`@ConditionalOnBean`) and always yielding to a consumer-defined bean (`@ConditionalOnMissingBean`).
- **`CatalogQueryService` is opt-in** via `ppm.catalog.enabled` — directly addressing Phase 5 Finding 2 (its five-aggregate dependency footprint surprising consumers who scan for it as if it were part of Plan).
- **`PpmProperties`** establishes the `ppm.*` configuration namespace (currently `ppm.enabled`, `ppm.catalog.enabled`) as the stable place for future starter configuration.
- **`PpmPortAvailabilityValidator`** fails startup fast, with a specific message, when an aggregate's ports are *partially* implemented — anchored on each aggregate's genuinely unique port (not a widely-shared one like `PlanRepositoryPort`, whose presence alone says nothing about intent — this false-positive was caught and fixed during the starter's own test-writing, not left as a latent bug).
- **10 passing tests** via `ApplicationContextRunner` covering: successful startup (full/single/zero-port aggregates), partial-port failure (plus the shared-port false-positive regression), bean override precedence, and conditional activation (`ppm.enabled`, `ppm.catalog.enabled`).
- Zero dependency on `ppm-svc` or `ppm-demo` — enforced by `build.gradle`, not just documented.

Full detail: [`ppm-spring-boot-starter/README.md`](../ppm-spring-boot-starter/README.md).

## Phase 7 — Repository Hardening & Release Engineering ✅ Complete

Full detail: [docs/PHASE_7_RELEASE_ENGINEERING_REPORT.md](docs/PHASE_7_RELEASE_ENGINEERING_REPORT.md).

- **A real, previously-invisible packaging bug was found and fixed**: `ppm-core`'s `api` dependencies and `ppm-spring-boot-starter`'s `spring-boot-autoconfigure` dependency were published with no version at all — `io.spring.dependency-management` resolves versions for this build's own compilation but doesn't propagate them into the published POM. Every check up to this point (compiles, test suites, even `publishToMavenLocal` itself) passed regardless, because every consumer so far resolved `ppm-core` via a Gradle `project(...)` reference in the same build. Only building a genuinely external throwaway consumer (Maven coordinates only, `mavenLocal()`, no project reference) surfaced it. Fixed by pinning explicit literal versions on the five affected dependencies.
- **`maven-publish` wired** for both `ppm-core` and `ppm-spring-boot-starter`, with sources/javadoc jars and POM metadata (license, developers, scm placeholder).
- **`ArchitectureTest`** (ArchUnit) added to `ppm-core` — turns ADR-002's package-boundary and dependency rules into a failing test, not just documentation. One rule (aggregate-package cycle-freedom) was tried and removed after producing a false positive against a legitimate, intended design pattern (join aggregates depending back on their parent's port).
- **Governance docs** added at the repo root and in `ppm-core`: `LICENSE`, `CONTRIBUTING.md`, `SECURITY.md`, `RELEASE_PROCESS.md` (which now documents the external-consumer verification procedure as a mandatory, every-release step), `COMPATIBILITY.md`.
- **Explicitly not done**, per the phase's own non-goals: Kotlin DSL migration, `module-info.java`/JPMS, a Gradle version catalog, `CODE_OF_CONDUCT.md`, a redundant `SUPPORTED_VERSIONS.md` — none solve a problem this repository currently has.

## Phase 8B (partial) — Integration Test Matrix

Scoped deliberately to real gaps, not a full re-listing of everything a testing pyramid could contain — see the commit for what was explicitly skipped and why (nothing to compare against for backward-compatibility testing pre-1.0; no concrete perf requirement to benchmark against).

- **`ppm-demo` gained its first automated tests.** `PpmDemoApplicationIntegrationTest` boots the real application (real `ppm-core` beans, real in-memory adapters, real Spring MVC dispatch) and exercises the same plan/module/promo-code flow the Phase 5 manual `curl` session proved, now via `MockMvc` — 9 tests, including two exception-mapping cases through `GlobalExceptionHandler`.
- **`ConcurrentUseCaseThreadSafetyTest`** empirically validates the Phase 5 report's static thread-safety claim: 200 concurrent `createPlan` calls and 200 concurrent reads through the real Spring context, asserting no duplicate IDs/slugs and consistent reads.
- **`ppm-spring-boot-starter`'s test suite expanded** from 10 to 14: properties actually bind onto `PpmProperties` (not just gate bean presence), and `@ConditionalOnClass` is tested with a `FilteredClassLoader` simulating `ppm-core`'s genuine absence from the classpath — not just the property-based disable already covered.
- **`scripts/verify-published-artifacts.sh`** automates the manual external-consumer verification procedure from Phase 7 (the one that caught the unversioned-dependency bug) so it's a repeatable one-line command, not manual toil repeated by hand every release. `RELEASE_PROCESS.md` now points at it directly.

## What's next: Phase 8 — publishing

1. Replace the placeholder `url`/`scm` values in both `build.gradle` POM blocks with the real repository URL.
2. Decide the actual publishing target (internal registry vs. something else) — `RELEASE_PROCESS.md` step 6 covers what's needed once decided.
3. **Publish `ppm-core` and `ppm-spring-boot-starter` 1.0.0** (not another `0.x` — see [VERSIONING.md](VERSIONING.md)), as companion artifacts released together, following the checklist in `RELEASE_PROCESS.md` — including its mandatory external-consumer verification step.

## Explicitly deferred, no committed timeline

See [docs/TECHNICAL_DEBT_REGISTER.md](docs/TECHNICAL_DEBT_REGISTER.md) for the full, consolidated list — including non-Jackson serialization support, JPMS, and a few items explicitly considered and rejected (a cycle-freedom architecture rule, "any port present" partial-wiring detection, a full documentation-tree restructure, speculative extension points).
