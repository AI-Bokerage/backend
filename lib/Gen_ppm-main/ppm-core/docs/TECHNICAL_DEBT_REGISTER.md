# Technical Debt Register

Every deliberately-deferred item across this repository, in one place, so a future contributor doesn't have to reconstruct "was this forgotten or decided?" from scattered doc comments. Nothing here is new — every entry already exists as a decision recorded somewhere else; this register just indexes them. Do not implement anything here without first re-reading its source document — some of these are conditional ("revisit if X happens"), not unconditional TODOs.

## Deferred, revisit only if a concrete need materializes

| Item | Why deferred | Revisit trigger | Source |
|---|---|---|---|
| Non-Jackson serialization support | `jackson-annotations` on wire-vocabulary enums (`PlanVisibility`, `ModuleCode`, `DiscountType`, `BillingCycle`, `PromoValidationReason`) is a real, accepted coupling | A real host without a Jackson-compatible stack | `ppm-core/docs/DEPENDENCY_AUDIT.md`, `ppm-core/ARCHITECTURE.md` |
| JPMS / `module-info.java` | Strategic decision with real consequences for reflection-based frameworks (Spring, Jackson, Lombok); no concrete requirement evaluated against | A concrete need for strong module boundaries at the JVM level | `ppm-core/COMPATIBILITY.md` |
| Kotlin DSL migration for `build.gradle` | Cosmetic parity with another project, not a problem this repo has | Never, absent a real Gradle limitation Groovy DSL can't express | Phase 7 commit message |
| Gradle version catalog | Same as above — solves a problem (version drift across many modules) this 4-module repo doesn't yet have at scale | If the module count grows enough that version drift becomes a real risk | Phase 7 commit message |
| `AddOnPriceRepositoryPort` vs. `AddOnPriceApplicationService` asymmetry | Port exposes full CRUD; use-case only exposes `resolveActivePrice`. Pre-existing (predates the extraction), deliberately preserved — YAGNI | A real consumer need for add-on price CRUD via the use-case layer | `ppm-core/docs/API_INVENTORY.md` |
| `EntitlementResolver` narrow interface vs. `PlanEntitlementApplicationService` full interface | Intentional split for downstream services that only need the read path | N/A — this is a stable design, not deferred work | `ppm-core/docs/API_INVENTORY.md` |
| `BusinessRuleViolationException` | `@Deprecated(since = "foundation", forRemoval = true)` — superseded by `BusinessException` + explicit `ErrorCode` | A MAJOR version bump (per `VERSIONING.md`'s deprecation policy) is the earliest point it can be removed | `ppm-core/docs/API_INVENTORY.md`, `ppm-core/VERSIONING.md` |

## Deferred, on the explicit roadmap (not "maybe someday")

| Item | Status | Blocking on | Source |
|---|---|---|---|
| Real repository URL / SCM metadata in both `build.gradle` POM blocks | Placeholder (`https://github.com/your-org/Gen_PPM`) | A decision on where this repository actually lives publicly/internally | `ppm-core/RELEASE_PROCESS.md` step 6 |
| Publishing target (internal registry vs. elsewhere) | Not decided | A decision by whoever owns release infrastructure | `ppm-core/ROADMAP.md` "Phase 8" |
| `1.0.0` tag + release notes | Not started | The two items above | `ppm-core/ROADMAP.md`, `ppm-core/RELEASE_PROCESS.md` |

## Explicitly rejected, do not re-propose without new information

| Item | Why rejected |
|---|---|
| A "cycle-freedom" ArchUnit rule across aggregate packages | Produces false positives against the legitimate, intended two-directional coupling between join aggregates and their parent's port (e.g. `PlanModuleApplicationServiceImpl` verifying the plan exists via `PlanRepositoryPort`). Tried and removed in Phase 7 — see `ppm-core/src/test/.../ArchitectureTest.java`'s comments. |
| "Any required port present" partial-wiring detection in the starter's validator | Produces false positives whenever a port is shared across independently-adoptable aggregates (nearly every port involving `PlanRepositoryPort`). Replaced with anchor-port detection — see [ADR-003](../../ppm-svc/docs/adr/ADR-003-starter-port-validation-design.md). |
| A full `docs/architecture/`, `docs/guides/`, `docs/examples/` directory restructure | Considered in a Phase 9 proposal; the current flat-but-cross-linked documentation set (each doc links to the ones it depends on, indexed from the root `README.md`) already serves the same purpose without the risk of broken relative links from a large-scale file move. Revisit only if the number of documents grows enough that flat organization genuinely becomes hard to navigate — not for parity with another project's directory shape. |
| Speculative extension points (custom event publisher, custom validation hooks, custom clock/ID-generator providers) | No consumer has asked for any of these; `ppm-core` doesn't have them today. Documenting an "intended" extension model for hooks that don't exist risks promising something not built. Revisit only when a concrete extension request lands — see `ppm-core/ARCHITECTURE.md` for the one extension point that does exist and is used (`RepositoryPort` implementations). |
