# Gen_PPM — PPM (Plan & Pricing Management) Platform

A Gradle multi-module repository holding a framework-agnostic business library for subscription plan, module, entitlement, promo-code, and pricing catalog management — plus its official Spring Boot integration, a validation consumer proving it's genuinely reusable, and a production reference host.

## Why this repository is shaped this way

The business logic (`ppm-core`) started life embedded inside a single Spring Boot service (`ppm-svc`). It was extracted into its own framework-agnostic module so that **any** Spring application — not just `ppm-svc` — can depend on it, implement a handful of repository ports, and get PPM's business rules (uniqueness checks, state-transition validation, entitlement/pricing resolution) for free, correctly, without copying code.

That extraction is complete. What you're looking at now is the result: a library with a frozen public API, an official Spring Boot integration layer, a second independent consumer proving the library actually works outside its original host, and the release engineering to back a real `1.0.0`.

## Module architecture

```
Gen_PPM
│
├── ppm-core                  Framework-agnostic business library.
│                             Domain models, use cases, repository ports (SPI),
│                             domain exceptions. No Spring MVC, no Spring Security,
│                             no JPA, no messaging — a host supplies all of that.
│
├── ppm-spring-boot-starter   Official Spring Boot integration for ppm-core.
│                             Auto-configures one use-case bean per aggregate,
│                             validates a consumer's port wiring at startup,
│                             exposes a small `ppm.*` configuration namespace.
│                             Zero business logic; zero dependency on ppm-svc.
│
├── ppm-demo                  Independent second consumer. Proves ppm-core is
│                             reusable, not just extracted: depends only on
│                             ppm-core, implements six ports with trivial
│                             in-memory adapters, runs a real Spring Boot app.
│
└── ppm-svc                   Production reference host. REST, JPA persistence,
                              Spring Security (JWT), RabbitMQ, Flyway — every
                              host-shaped concern ppm-core deliberately excludes.
```

Each module's responsibility is intentionally narrow:

| Module | Owns | Never contains |
|---|---|---|
| `ppm-core` | Business rules, domain models, use cases, repository ports | Controllers, DTOs, JPA entities, security, messaging |
| `ppm-spring-boot-starter` | Auto-configuration, `@ConfigurationProperties`, startup port validation | Business logic, controllers, persistence |
| `ppm-demo` | A working demonstration of consuming `ppm-core` | Anything copied from `ppm-svc` |
| `ppm-svc` | REST, persistence, security, messaging — the real production host | Business rules that belong in `ppm-core` |

## Quick start

```bash
git clone <this-repo>
cd Gen_PPM

# Build and test everything
./gradlew :ppm-core:test :ppm-svc:test :ppm-spring-boot-starter:test :ppm-demo:test

# Run the validation consumer and try it
./gradlew :ppm-demo:bootRun
curl -X POST localhost:8199/demo/plans -H 'Content-Type: application/json' \
  -d '{"code":"starter","name":"Starter Plan","trialDays":14}'
```

To depend on `ppm-core` from your own Spring Boot application, add `ppm-spring-boot-starter` and implement the repository ports for the aggregates you need — see [`ppm-core/INTEGRATION_GUIDE.md`](ppm-core/INTEGRATION_GUIDE.md) and [`ppm-spring-boot-starter/README.md`](ppm-spring-boot-starter/README.md).

## Requirements

Java 21, Spring Boot 4.0.6 (for the starter and demo) or bare Spring Framework 7.0.7 (`spring-context`/`spring-tx` — for `ppm-core` alone). See [`ppm-core/COMPATIBILITY.md`](ppm-core/COMPATIBILITY.md) for the full matrix and what's explicitly unsupported.

## Documentation map

Start with whichever module you're actually touching:

| I want to... | Read |
|---|---|
| Understand why the architecture is shaped this way | [`ppm-core/ARCHITECTURE.md`](ppm-core/ARCHITECTURE.md) |
| Know where a new file goes | [`ppm-core/PACKAGE_GUIDE.md`](ppm-core/PACKAGE_GUIDE.md) |
| Depend on `ppm-core` from my own application | [`ppm-core/INTEGRATION_GUIDE.md`](ppm-core/INTEGRATION_GUIDE.md), [`ppm-spring-boot-starter/README.md`](ppm-spring-boot-starter/README.md) |
| See every public type, classified | [`ppm-core/docs/API_INVENTORY.md`](ppm-core/docs/API_INVENTORY.md) |
| Know what's stable across versions | [`ppm-core/VERSIONING.md`](ppm-core/VERSIONING.md), [`ppm-core/COMPATIBILITY.md`](ppm-core/COMPATIBILITY.md) |
| Cut a release | [`ppm-core/RELEASE_PROCESS.md`](ppm-core/RELEASE_PROCESS.md) |
| See what changed | [`ppm-core/CHANGELOG.md`](ppm-core/CHANGELOG.md) |
| Understand where the project is headed | [`ppm-core/ROADMAP.md`](ppm-core/ROADMAP.md) |
| See what's deliberately deferred (and why) vs. explicitly rejected | [`ppm-core/docs/TECHNICAL_DEBT_REGISTER.md`](ppm-core/docs/TECHNICAL_DEBT_REGISTER.md) |
| See a real second consumer working end to end | [`ppm-demo/README.md`](ppm-demo/README.md) |
| Read the architecture decisions that shaped all of this | [`ppm-core/docs/adr/README.md`](ppm-core/docs/adr/README.md) |
| Contribute | [`CONTRIBUTING.md`](CONTRIBUTING.md) |
| Report a security concern | [`SECURITY.md`](SECURITY.md) |

## Testing summary

| Module | What's tested | Count |
|---|---|---|
| `ppm-core` | Use-case business rules (via `ppm-svc`'s test suite, which exercises `ppm-core` classes directly), plus `ppm-core`'s own architecture-enforcement tests (ArchUnit — forbidden dependencies, package-naming rules) | 1025 (`ppm-svc`) + 9 (`ppm-core`) |
| `ppm-spring-boot-starter` | Successful startup, partial-port-wiring failure, bean overrides, conditional activation, properties binding, classpath conditions | 14 |
| `ppm-demo` | Real end-to-end HTTP flow (no mocks) through the actual Spring context; concurrent-use-case thread-safety | 11 |

Every module's test suite is a regression net for the others — see [`ppm-core/docs/PHASE_5_READINESS_REPORT.md`](ppm-core/docs/PHASE_5_READINESS_REPORT.md) and [`ppm-core/docs/PHASE_7_RELEASE_ENGINEERING_REPORT.md`](ppm-core/docs/PHASE_7_RELEASE_ENGINEERING_REPORT.md) for what these tests actually found (including a real, previously-invisible packaging bug — not just what they verify).

## Release policy

Not yet published to any external Maven repository. See [`ppm-core/VERSIONING.md`](ppm-core/VERSIONING.md) for the SemVer policy that applies once versioned releases begin, and [`ppm-core/RELEASE_PROCESS.md`](ppm-core/RELEASE_PROCESS.md) for what's left before `1.0.0`.

## License

Proprietary, internal to the CPMS platform ecosystem — see [`LICENSE`](LICENSE).
