# Contributing

This repository holds a business library (`ppm-core`), its official Spring Boot integration (`ppm-spring-boot-starter`), a validation consumer (`ppm-demo`), and the reference host (`ppm-svc`). Before changing anything, know which one you're touching — the rules differ.

## Before you start

1. Read `ppm-core/ARCHITECTURE.md` and `ppm-core/PACKAGE_GUIDE.md`. The library's package convention and library/host boundary are frozen per [ADR-002](ppm-svc/docs/adr/ADR-002-ppm-core-extraction-pattern.md) — changing them requires a new ADR, not a PR description.
2. If your change touches `ppm-core`, run `./gradlew :ppm-core:test` — this includes `ArchitectureTest`, which fails the build if you introduce a forbidden dependency (Spring MVC, Security, JPA, AMQP) or a host-layer package name (`api`, `controller`, `infrastructure`, `persistence`, `adapter`) into the library. This is enforcement, not a style suggestion — a failing architecture test blocks the PR.
3. If your change adds a new aggregate or use case to `ppm-core`, update `ppm-core/docs/API_INVENTORY.md` in the same PR — classify every new public type as Public API / SPI / Internal.

## Where to make a change

| You're changing... | It belongs in... |
|---|---|
| A business rule, domain model, use case, or repository port | `ppm-core` |
| Spring Boot auto-configuration, `@ConfigurationProperties`, startup validation | `ppm-spring-boot-starter` |
| A demonstration of consuming the library | `ppm-demo` |
| REST controllers, JPA entities, security, messaging, Flyway migrations | `ppm-svc` |

If you're not sure, it's almost never `ppm-core` or `ppm-spring-boot-starter` — those two are the smallest, most conservatively-scoped modules on purpose.

## Running the whole repo

```bash
./gradlew :ppm-core:test :ppm-svc:test :ppm-spring-boot-starter:test
./gradlew :ppm-core:compileJava :ppm-svc:compileJava :ppm-demo:compileJava :ppm-spring-boot-starter:compileJava
```

## Commit messages

One logical change per commit. If a commit touches `ppm-core`'s public API, say so explicitly in the message and note whether it's backward-compatible (see `ppm-core/VERSIONING.md`).

## Release process

See `ppm-core/RELEASE_PROCESS.md`.
