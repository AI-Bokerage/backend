# Versioning Policy

## Current status: pre-1.0, unpublished

`ppm-core` and `ppm-spring-boot-starter` are at `0.0.1-SNAPSHOT` and are **not published to any external Maven repository** — only to the local Maven cache, for release-process verification (see `RELEASE_PROCESS.md`). Within this monorepo, `ppm-core` has three consumers: `ppm-svc` (the production reference host), `ppm-demo` (an independent validation consumer using only published-artifact-shaped dependencies), and `ppm-spring-boot-starter` (the official Spring Boot integration layer).

Extraction is complete (all fourteen aggregates/composite services migrated — see `ROADMAP.md`), consumer validation, the Spring Boot starter, and repository/release-engineering hardening are all done. What remains before a `1.0.0` release is purely release-engineering: replacing the placeholder `url`/`scm` values in both `build.gradle` POM blocks and deciding an actual publishing target (see `RELEASE_PROCESS.md` and `ROADMAP.md`'s "Phase 8" section).

**No backward-compatibility guarantee applies yet.** Until the first tagged release, method signatures, package layout, and even aggregate boundaries could in principle still change — though none has changed since the architecture froze under ADR-002. Every change is verified against `ppm-svc`'s full test suite (and now `ppm-demo`'s and the starter's) before merging, so breakage is always caught immediately — but there is no deprecation window, no `@Deprecated` grace period, and no changelog entry required for a pre-1.0 breaking change today.

## The policy that will apply once versioned releases begin

When `ppm-core` is first published as a versioned artifact, it (and `ppm-spring-boot-starter`, released alongside it at the same version — see `RELEASE_PROCESS.md`) will adopt **Semantic Versioning (SemVer 2.0.0)**:

- **MAJOR** — a breaking change to any type documented as **Public API** or **SPI** in [docs/API_INVENTORY.md](docs/API_INVENTORY.md): a use-case method signature changes, a port method is removed, an exception type changes its parent class, a domain model field is removed.
- **MINOR** — a backward-compatible addition: a new aggregate, a new use-case method, a new port method with a default implementation, a new optional constructor overload.
- **PATCH** — a bug fix that does not change any Public API or SPI signature: a business-rule correction that still satisfies the documented contract, a dependency bump, an internal refactor.

Changes to types classified as **Internal** in [docs/API_INVENTORY.md](docs/API_INVENTORY.md) (e.g. any `*ApplicationServiceImpl` class) do not require a MAJOR bump even if their signature changes, because consumers are not expected to reference them directly — but see the note below on Spring's proxy visibility.

## What counts as the public surface

A change is only "breaking" if it affects a type documented as Public API or SPI. Use [docs/API_INVENTORY.md](docs/API_INVENTORY.md) as the authoritative reference when judging whether a change requires a MAJOR bump — do not rely on Java's `public` keyword alone, since `Impl` classes must be `public` for Spring proxying but are not part of the intended contract.

## Deprecation policy (once versioned)

A type or method marked `@Deprecated` will remain functional for at least one MINOR release cycle before removal in a subsequent MAJOR release, with the Javadoc `@deprecated` tag pointing at its replacement — following the existing pattern already used for `BusinessRuleViolationException` (`@Deprecated(since = "foundation", forRemoval = true)`).

## Compatibility with the Spring Boot BOM

`ppm-core` pins its Spring Framework transitive versions via the `spring-boot-dependencies` BOM (currently `4.0.6`) rather than declaring bare Spring Framework versions. A MAJOR Spring Boot BOM bump that changes a transitive API `ppm-core` re-exports (`spring-context`, `spring-tx`) will itself be treated as a MINOR-or-MAJOR `ppm-core` bump depending on whether it breaks a consuming host — evaluated case by case, not automatically inherited from Spring's own versioning.
