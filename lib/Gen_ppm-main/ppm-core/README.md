# ppm-core

Framework-agnostic PPM (Plan & Pricing Management) business logic — domain models, use cases, and outbound repository ports, extracted so any Spring Boot application can depend on it and get PPM's business rules for free.

`ppm-core` is **not** a Spring Boot starter and **not** an SDK. It is a business library: a host application implements the outbound ports (typically backed by JPA/Spring Data), wires the use-case beans into its own Spring context, and owns everything HTTP-, security-, and persistence-shaped around it.

`ppm-svc` (in this same repository) is the reference host — a Spring Boot service providing REST, JPA persistence, security, and messaging around `ppm-core`. Read its controllers and adapters as the canonical example of consuming this library.

## Status

**Extraction, consumer validation, Spring Boot integration, and repository hardening are all complete — not yet published as a versioned artifact.** Every reusable PPM business aggregate has been migrated from `ppm-svc` into `ppm-core` following [ADR-002](../ppm-svc/docs/adr/ADR-002-ppm-core-extraction-pattern.md), with zero ADR revisions required across the entire project. `ppm-svc` now contains only host responsibilities (REST, persistence, security, messaging, configuration); [`ppm-demo`](../ppm-demo) proves the library works for a genuinely independent second consumer; [`ppm-spring-boot-starter`](../ppm-spring-boot-starter) is the official Spring Boot integration layer. See [ROADMAP.md](ROADMAP.md) for the full history — only release engineering (a real repository URL and a publishing-target decision) remains before `1.0.0`.

Until the first tagged release, package layout, method signatures, and even aggregate boundaries may still shift — see [VERSIONING.md](VERSIONING.md) for what stability guarantee (if any) currently applies.

## What's inside

Fourteen business aggregates / composite services across eleven top-level packages, each organized feature-first (see [PACKAGE_GUIDE.md](PACKAGE_GUIDE.md)):

| Aggregate | Package | Purpose |
|---|---|---|
| Plan | `plan` | Subscription plan catalog (code, slug, visibility, trial) |
| PlanVersion | `plan` (co-located) | Versioned plan snapshots — effective-dated limits, auto-close lifecycle |
| Module | `module` | Named platform capability catalog |
| AddOn | `addon` | Optional add-on catalog |
| Entitlement | `entitlement` | Entitlement definition catalog (quota/flag/text) |
| PromoCode | `promocode` | Discount code catalog + validation rule engine |
| PromoValidationService | `promocode` (co-located) | Discount/eligibility validation rule engine over PromoCode + PromoCodePlan + Plan |
| PlanModule | `planmodule` | Plan ↔ Module assignment (join aggregate) |
| PlanAddOn | `planaddon` | Plan ↔ AddOn assignment (join aggregate) |
| PlanEntitlement | `planentitlement` | Plan ↔ Entitlement assignment + resolved-entitlement read model |
| PromoCodePlan | `promocodeplan` | PromoCode ↔ Plan restriction (join aggregate) |
| Catalog (composite) | `plan` (co-located) | Cross-aggregate read model composing Plan/Module/Entitlement for public catalog display |
| PlanPrice + PricingResolver | `planprice` | Region/currency/cycle-specific plan pricing catalog + applicable-price resolution engine |
| AddOnPrice | `addonprice` | Region/currency/cycle-specific add-on pricing catalog |

Plus two foundational packages with no aggregate of their own:

- `common` — `BaseEntity`, `AuditableEntity`, `DomainEvent`, `BillingCycle` (shared pricing vocabulary)
- `exception` — `BusinessException` and its subtypes, `ErrorCode`

## Requirements

- **Java 21** (Gradle toolchain-pinned — see `build.gradle`)
- **Spring Framework** — `spring-context` + `spring-tx` only (for `@Service`/`@Transactional`). No Spring Boot, Spring MVC, or Spring Security dependency inside `ppm-core` itself; the host supplies those.
- Dependency-managed against the `spring-boot-dependencies:4.0.6` BOM for version alignment with a Spring Boot 4.x host — see [DEPENDENCY_AUDIT.md](docs/DEPENDENCY_AUDIT.md).

## Quick start

```groovy
// host's settings.gradle
includeBuild('path/to/Gen_PPM') // or a published coordinate once versioned releases begin

// host's build.gradle
dependencies {
    implementation 'com.company:ppm-core:0.0.1-SNAPSHOT'
}
```

Then implement the outbound ports (e.g. `PlanRepositoryPort`) with your persistence stack of choice and register the use-case implementations as Spring beans. Full walkthrough: [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md).

## Documentation

| Doc | Audience | Purpose |
|---|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Consumers & contributors | Hexagonal architecture, library/host boundary, why it's shaped this way |
| [PACKAGE_GUIDE.md](PACKAGE_GUIDE.md) | Contributors | Package convention, when to add a new package |
| [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) | Consumers | How to depend on `ppm-core` and wire it into a Spring Boot host |
| [docs/API_INVENTORY.md](docs/API_INVENTORY.md) | Consumers | Every public type, classified as Public API / SPI / internal, plus per-aggregate consumer contracts |
| [docs/DEPENDENCY_AUDIT.md](docs/DEPENDENCY_AUDIT.md) | Contributors | Every dependency, why it exists, API vs. implementation detail |
| [VERSIONING.md](VERSIONING.md) | Consumers | Compatibility policy (pre-1.0 caveat) |
| [CHANGELOG.md](CHANGELOG.md) | Everyone | What changed, release to release |
| [ROADMAP.md](ROADMAP.md) | Everyone | Migration status, what's next |
| [docs/adr/README.md](docs/adr/README.md) | Contributors | Architecture Decision Record index |
| [docs/PHASE_5_READINESS_REPORT.md](docs/PHASE_5_READINESS_REPORT.md) | Everyone | Consumer validation via `ppm-demo`, API/dependency/thread-safety/release-readiness audits, Go/No-Go for starter + publishing |
| [RELEASE_PROCESS.md](RELEASE_PROCESS.md) | Maintainers | Pre-release checklist — including the mandatory external-consumer verification step that caught a real packaging bug |
| [COMPATIBILITY.md](COMPATIBILITY.md) | Consumers | Java/Spring version requirements, what's explicitly unsupported |
| [docs/PHASE_7_RELEASE_ENGINEERING_REPORT.md](docs/PHASE_7_RELEASE_ENGINEERING_REPORT.md) | Everyone | The packaging bug found and fixed, architecture-enforcement tests, release-readiness exit criteria |

## Proven by a second consumer

[`ppm-demo`](../ppm-demo) is an independent Spring Boot application depending only on `ppm-core` — no code shared with `ppm-svc`. It implements six repository ports with trivial in-memory adapters and exercises Plan/Module/PromoCode end-to-end. See its README and the [Phase 5 report](docs/PHASE_5_READINESS_REPORT.md) for what it proved and the two real integration findings it surfaced.

## Official Spring Boot integration

[`ppm-spring-boot-starter`](../ppm-spring-boot-starter) automates exactly what `ppm-demo` had to do by hand: implement the repository ports for the aggregates you want, and the starter registers every corresponding use-case bean automatically, validates your port implementations are complete at startup, and exposes a small `ppm.*` configuration namespace. It contains no business logic and no dependency on `ppm-svc` or `ppm-demo`. See its README for the full auto-configured-beans table and configuration reference.

## License / ownership

Internal to the CPMS platform ecosystem. Not published externally.
