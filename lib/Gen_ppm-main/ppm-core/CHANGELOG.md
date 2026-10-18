# Changelog

All notable changes to `ppm-core` are recorded here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/); semantic versioning does not yet apply — see [VERSIONING.md](VERSIONING.md) for why.

This log starts from the point `ppm-core` was created as a distinct Gradle module. Earlier history belongs to `ppm-svc`/CPMS-Platform and is not repeated here.

## [Unreleased]

### Added — Phase 8B (partial): Integration Test Matrix
- `ppm-demo/src/test/.../PpmDemoApplicationIntegrationTest.java`: ppm-demo's first automated tests. Boots the real application end to end (no `@MockBean` on any ppm-core service) and runs the same plan/module/promo-code flow the Phase 5 manual `curl` session proved, via `MockMvc` — 9 tests, including two `GlobalExceptionHandler` cases.
- `ppm-demo/src/test/.../ConcurrentUseCaseThreadSafetyTest.java`: empirically validates the Phase 5 report's thread-safety claim by firing 200 concurrent `createPlan` calls and 200 concurrent reads through the real Spring context.
- `ppm-spring-boot-starter`'s test suite expanded 10 → 14: properties actually bind onto `PpmProperties` (not just gate bean presence); `@ConditionalOnClass` tested with a `FilteredClassLoader` simulating ppm-core's genuine absence, not just the property-based disable.
- `scripts/verify-published-artifacts.sh`: automates the Phase 7 external-consumer verification procedure into a single repeatable command. `RELEASE_PROCESS.md` updated to reference it directly.
- Scoped deliberately, not exhaustively: backward-compatibility testing (nothing to compare against pre-1.0), performance benchmarking (no concrete perf requirement yet), and repository-wide documentation restructuring were explicitly not attempted this pass — see the commit message for the reasoning.

### Added — Phase 7: Repository Hardening & Release Engineering
- **Critical fix — packaging bug**: `ppm-core`'s `api` dependencies (`spring-context`, `spring-tx`, `slf4j-api`, `jackson-annotations`) and `ppm-spring-boot-starter`'s `spring-boot-autoconfigure` dependency were published with no version at all in their POM/Gradle Module Metadata — `io.spring.dependency-management` resolves versions for this build's own compilation but does not propagate them into what gets published. Invisible to every prior check (compiles, test suites, `publishToMavenLocal` itself) because every consumer so far resolved `ppm-core` via a Gradle `project(...)` reference within the same build. Caught only by building a genuinely external throwaway consumer (Maven coordinates + `mavenLocal()`, zero project references) and attempting to compile against the published artifacts. Fixed by pinning explicit literal versions, each with a comment explaining why.
- `maven-publish` wired for `ppm-core` and `ppm-spring-boot-starter`: `withSourcesJar()`/`withJavadocJar()`, POM metadata (name, description, license, developers, scm placeholder).
- Javadoc doclint relaxed to `-reference` only, plus ~12 port interfaces' stale Javadoc fixed — they referenced a specific `ppm-svc`-internal adapter class by FQN, a leftover from before the library/host split that both broke javadoc generation and was factually misleading (the whole point of a port is that any consumer supplies its own adapter).
- `ArchitectureTest` (ArchUnit, 9 assertions) added to `ppm-core`: enforces ADR-002's forbidden-dependency and package-naming rules as a failing test. A cycle-freedom rule was tried and removed after it flagged the legitimate, intended two-directional coupling between join aggregates and their parent (see the class's own comments for why).
- Governance docs added: `LICENSE`, `CONTRIBUTING.md`, `SECURITY.md` (repo root); `RELEASE_PROCESS.md`, `COMPATIBILITY.md` (`ppm-core`). `RELEASE_PROCESS.md` documents the external-consumer verification procedure as mandatory for every release, not just this one.
- Full detail: `docs/PHASE_7_RELEASE_ENGINEERING_REPORT.md`.
- No business logic, package convention, or architecture changed in this phase.

### Added — Phase 6: Spring Boot Starter (`ppm-spring-boot-starter`)
- New `ppm-spring-boot-starter` module: the official Spring Boot integration layer for `ppm-core`, containing zero business logic and no dependency on `ppm-svc` or `ppm-demo`.
- `PpmAutoConfiguration` + `PpmUseCaseAutoConfiguration`: registers one use-case bean per aggregate (16 beans across 14 aggregates/composite services), each `@ConditionalOnBean` on its required repository ports and `@ConditionalOnMissingBean` so a consumer's own bean always wins.
- `CatalogQueryService` made opt-in via the new `ppm.catalog.enabled` property, directly addressing the Phase 5 finding that its five-aggregate dependency footprint surprises consumers scanning for it as part of Plan.
- `PpmProperties`: the `ppm.*` configuration namespace (`ppm.enabled`, `ppm.catalog.enabled`), established as the stable home for future starter configuration.
- `PpmPortAvailabilityValidator` + `AggregatePortRequirement` + `PpmMissingRepositoryPortException`: fails startup fast, with a message naming the aggregate and the exact missing port(s), when a consumer has implemented some but not all of an aggregate's required ports. Anchored on each aggregate's genuinely unique port rather than any required port, after a real false-positive was found and fixed during the starter's own test-writing: `PlanRepositoryPort` is shared by nearly every aggregate, so its presence alone does not indicate intent toward any single one of them.
- 10 passing `ApplicationContextRunner`-based tests covering successful startup, partial-port failure (plus the shared-port false-positive regression test), bean-override precedence, and conditional activation.
- No business logic, package convention, or `ppm-core` architecture changed in this phase.

### Added — Phase 5: Library Readiness & Consumer Validation
- New `ppm-demo` Gradle module: an independent Spring Boot application depending only on `ppm-core` (`implementation project(':ppm-core')`), with no code shared with `ppm-svc`. Implements six repository ports (`PlanRepositoryPort`, `PlanVersionRepositoryPort`, `ModuleRepositoryPort`, `PlanModuleRepositoryPort`, `PromoCodeRepositoryPort`, `PromoCodePlanRepositoryPort`) with in-memory adapters and exercises Plan/Module/PromoCode end-to-end via a live `bootRun` + `curl` session.
- Two real integration findings from building `ppm-demo`, both resolved via documentation rather than a `ppm-core` code change: (1) `ppm-core` has no per-aggregate component-scan boundary, so scanning the whole `com.company.ppmsvc` base package instantiates every use-case bean across all aggregates; (2) `CatalogQueryServiceImpl` co-locating in the `plan` package pulls in four other aggregates' ports when scanning `plan` for the Plan aggregate's own service. `INTEGRATION_GUIDE.md` §3 now documents both, with the exact scan-scoping and exclusion-filter syntax `ppm-demo` uses.
- `docs/PHASE_5_READINESS_REPORT.md`: full report covering consumer validation, API audit, dependency review, documentation validation, public API freeze confirmation, SPI review, thread-safety review, performance review, package audit, and release-engineering readiness. Concludes **Go** on building a Spring Boot starter and **Conditional Go** on publishing `ppm-core` (pending `groupId`/license/`maven-publish` — no code or architecture change needed).
- No business logic, package convention, or architecture changed in this phase — documentation and a new independent consumer module only.

### Added — Batch 4: pricing layer (final business migration)
- `PlanPrice` + `PricingResolver` migrated to `ppm-core` under `planprice/{model,port,usecase}`, co-locating the resolver with `PlanPrice` (same rationale as `EntitlementResolver`/`PlanEntitlement`).
- `AddOnPrice` migrated to `ppm-core` under `addonprice/{model,port,usecase}`. The pre-existing asymmetry between the full-CRUD `AddOnPriceRepositoryPort` and the narrow `AddOnPriceApplicationService` (`resolveActivePrice` only) was preserved as-is.
- `BillingCycle` enum promoted to `ppm-core`'s `common` package — the first (and so far only) enum shared symmetrically between two peer aggregates rather than owned by one.
- `AddOnController`, `PlanPriceController`, and `PricingResolverController` updated to own DTO↔domain mapping and actor-id resolution; all corresponding service/controller tests rewritten for domain-only signatures.
- **Repository completion audit**: legacy `domain/`, `application/service`, `application/impl` package trees in `ppm-svc` fully removed (zero files remained after this batch); one stray file (`AuthenticatedUser`) relocated from the legacy `domain.model` package name to `application/util` alongside its sole consumer, `SecurityUtils`. Every remaining `ppm-svc` class enumerated and classified — zero classes contain reusable business logic. Full audit recorded in [ROADMAP.md](ROADMAP.md).
- Verified: `:ppm-core:compileJava`, `:ppm-svc:compileJava`, `:ppm-core:compileTestJava`, `:ppm-svc:compileTestJava` all clean; `:ppm-svc:test` 1025/1025 passing (3 fewer than pre-batch — the removed service-layer `AccessDeniedException`-on-unauthenticated tests, consistent with the actorId-as-parameter pattern already established in Batch 3a).
- **This is the final business-logic migration.** `ppm-core` now contains the complete reusable PPM business domain; `ppm-svc` contains only host responsibilities.

### Added — Batch 3a: business services
- `PlanVersionApplicationService` — full CRUD + version lifecycle rules (uniqueness, auto-close of the previous open-ended version, date-range-conflict detection), plus `getPlanVersionMeta`/`getPlanVersionLimits` composite reads.
- `CatalogQueryService` — cross-aggregate public-catalog read engine (`listPublicPlans`, `getPlanDetail`), composing Plan/PlanVersion/PlanModule/Module/PlanEntitlement/Entitlement. Confirmed unused by any current `ppm-svc` controller; its five `Catalog*Response` records moved to `ppm-core` as plain read models with `@JsonInclude(NON_NULL)` stripped.
- `PromoValidationService` — pure rule engine (PV-1..PV-9), now returning the new `PromoValidationResult` (plain, ppm-core, no Jackson annotations) instead of the host's `PromoValidationResponse`.
- `PromoValidationReason` enum relocated from `ppm-svc`'s `domain.enums` to `ppm-core`'s `promocode.model`.

### Added — Phase 3.5: library engineering & consumer readiness baseline
- Public API inventory ([docs/API_INVENTORY.md](docs/API_INVENTORY.md)) classifying every type as Public API / SPI / Internal.
- Dependency audit ([docs/DEPENDENCY_AUDIT.md](docs/DEPENDENCY_AUDIT.md)).
- Consumer-facing documentation set: `README.md`, `ARCHITECTURE.md`, `PACKAGE_GUIDE.md`, `INTEGRATION_GUIDE.md`.
- Governance documents: `VERSIONING.md`, this `CHANGELOG.md`, `ROADMAP.md`, [docs/adr/README.md](docs/adr/README.md) ADR index.
- No code changes in this phase — documentation and governance only.

## Batch 2 — join aggregates
- Migrated `PlanAddOn`, `PlanModule`, `PlanEntitlement` (+ `EntitlementResolver`/`DefaultEntitlementResolver`), `PromoCodePlan` into `ppm-core`.
- Dependency-map sanity check performed before the batch (confirmed all four are pure UUID-FK joins with no object references and no circular dependencies).
- End-of-batch legacy-import audit performed after.

## Batch 1 — Entitlement + PromoCode
- Migrated `Entitlement` and `PromoCode` aggregates into `ppm-core`.

## Phase 4.2 — AddOn
- Migrated `AddOn` aggregate into `ppm-core`.

## Phase 4.1 — Module
- Migrated `Module` aggregate into `ppm-core`.

## Phase 0 — Extraction foundation
- `ppm-svc` extracted from the CPMS-Platform monorepo into this standalone repository, with `.git` history promoted (not re-initialized) to preserve blame/log continuity.
- `ppm-core` Gradle module created; Plan aggregate migrated first as a proof of the extraction pattern (domain model, port, use case).
- [ADR-002](../ppm-svc/docs/adr/ADR-002-ppm-core-extraction-pattern.md) authored, capturing the library/host boundary, feature-first package convention, and the per-aggregate migration checklist every subsequent batch follows.
- `ppm-svc`'s dependency on the CPMS-Platform monorepo's `libs:java-common` removed; five actually-used security classes vendored into `ppm-svc`'s own `infrastructure.security` package with zero framework dependency beyond `org.springframework.security.oauth2.jwt.Jwt` and `java.*`.
- Security push-readiness audit performed (no secrets found in history; `.env` added to `.gitignore` as the one real gap).

---


