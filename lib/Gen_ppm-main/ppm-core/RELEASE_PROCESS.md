# Release Process

Covers `ppm-core` and `ppm-spring-boot-starter` — the two published artifacts in this repository. `ppm-svc` and `ppm-demo` are not published; they're the reference host and validation consumer respectively.

## Pre-release checklist

1. **Full build green.** `./gradlew :ppm-core:test :ppm-svc:test :ppm-spring-boot-starter:test` — including `ppm-core`'s `ArchitectureTest` and `ppm-spring-boot-starter`'s `PpmAutoConfigurationTest`.
2. **`docs/API_INVENTORY.md` current.** Every public type added since the last release is classified.
3. **`CHANGELOG.md` updated.** Move the `[Unreleased]` section's relevant entries under a new version heading.
4. **Local publish + external consumer verification** (see below) — do not skip this. It has caught a real release-blocking bug before (see the note at the bottom of this document).
5. **Version bump.** Update `version` in both `ppm-core/build.gradle` and `ppm-spring-boot-starter/build.gradle`. They are released together, at the same version, since the starter has a hard compile-time dependency on the exact `ppm-core` version it was built against.
6. **`groupId`/`url`/`scm` placeholders in both `build.gradle` files** — replace `https://github.com/your-org/Gen_PPM` with the real repository URL before any release intended for consumption outside this monorepo's own CI.

## Local publish + external consumer verification

**Do not trust `publishToMavenLocal` succeeding as proof the artifact works for a consumer.** `io.spring.dependency-management` resolves dependency versions for *this build's own compilation* but does not automatically propagate a resolved version into the published POM/Gradle Module Metadata. A dependency that compiles fine inside this monorepo (where Gradle resolves it via `project(...)` references sharing the same build) can be **completely unresolvable** for a genuinely external consumer pulling only from Maven coordinates — Gradle will report `Could not find <group>:<artifact>:` with no version, because none was published.

This is exactly what happened during Phase 7: `ppm-core`'s `api` dependencies (`spring-context`, `spring-tx`, `slf4j-api`, `jackson-annotations`) and `ppm-spring-boot-starter`'s `spring-boot-autoconfigure` dependency were all published with **no version at all** until caught by the procedure below. `publishToMavenLocal` itself reported success in every case — the bug was invisible until an artifact was actually consumed from a separate build.

Procedure (repeat for every release, not just when a dependency changes):

```bash
./scripts/verify-published-artifacts.sh
```

This automates: publishing both artifacts to Maven local, scaffolding a throwaway Gradle project **outside this repository** (`mktemp -d`, cleaned up automatically) with `mavenLocal()` in `repositories {}` and `implementation 'com.company:ppm-spring-boot-starter:<version>'` as its only ppm dependency — no `project(...)` reference anywhere in its graph — and compiling one trivial class referencing a `ppm-core` type against it.

**If the script fails to resolve a transitive dependency**, check that dependency's `<version>` in the published POM (`~/.m2/repository/com/company/.../*.pom`). A missing `<version>` tag means it needs a literal version pinned explicitly in the owning module's `build.gradle`, with a comment explaining why (see the existing examples in `ppm-core/build.gradle` and `ppm-spring-boot-starter/build.gradle` for the pattern).

## After publishing

1. Tag the release: `git tag ppm-core-v<version>` (and a matching tag for the starter if versioned independently in the future — currently they move together).
2. Confirm `ppm-demo` still builds against the published coordinates if you choose to point it there (currently it uses `project(':ppm-core')` for monorepo simplicity — switching it to the published artifact is a valid future strengthening of the consumer-validation story, not required for every release).
