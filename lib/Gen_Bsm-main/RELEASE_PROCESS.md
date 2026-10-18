# Release Process

Applies to publishing `bsm-core` and `bsm-spring-boot-starter` (released in
lockstep — see `VERSIONING.md`). `bsm-svc` and `bsm-demo` are not published
artifacts; they are the reference host and reference second-consumer
respectively, and stay at their own independent versions.

## Pre-release checklist

1. **Full build is green.**
   ```
   ./gradlew clean build
   ```
   This runs, across every module: compilation, unit tests,
   `bsm-core`'s `ArchitectureTest`, `bsm-spring-boot-starter`'s
   `ApplicationContextRunner` suite, `bsm-demo`'s end-to-end workflow test,
   and `bsm-svc`'s full test suite (including Testcontainers-backed
   integration tests).
2. **`publishToMavenLocal` succeeds for both publishable modules.**
   ```
   ./gradlew :bsm-core:publishToMavenLocal :bsm-spring-boot-starter:publishToMavenLocal
   ```
3. **Version bump decided** per `VERSIONING.md` — walk the "What counts as
   a breaking change" list against the diff since the last release; pick
   PATCH/MINOR/MAJOR accordingly, not by habit.
4. **`CHANGELOG.md` updated** with the new version's entry — Added /
   Changed / Deprecated / Removed / Fixed / Security sections as
   applicable, following Keep a Changelog conventions.
5. **Documentation verified against the new version**: if the release adds
   a port, service, or config property, confirm `PUBLIC_API.md`,
   `PORT_REFERENCE.md`, and/or `CONFIGURATION_REFERENCE.md` were updated in
   the same PR — these are audit documents, not aspirational ones; they
   must reflect what actually ships.
6. **`build.gradle`/`build.gradle.kts` `version` fields updated** in both
   `bsm-core` and `bsm-spring-boot-starter` to the new version number
   (currently both at `0.0.1-SNAPSHOT` — see `COMPATIBILITY.md`).
7. **External-consumer smoke check** (see `SECOND_CONSUMER_VALIDATION.md`):
   re-run the outside-monorepo consumer validation for any MAJOR release,
   and for any MINOR release that touches auto-configuration or the port
   set.

## Build / test / publish / doc verification (the actual commands)

```
./gradlew clean build
./gradlew :bsm-core:publishToMavenLocal :bsm-spring-boot-starter:publishToMavenLocal
```

Both must complete with `BUILD SUCCESSFUL` and zero skipped/failed tests
before tagging.

## Tagging

Tag the release commit as `vX.Y.Z` (matching the Gradle `version` field
exactly, without a `-SNAPSHOT` suffix). Do not tag until the pre-release
checklist above is fully green — a tag is a promise that the artifact at
that commit is what was published.

## Rollback

If a published version is found to be broken after release:

1. Do not delete or overwrite the broken artifact's Maven coordinates — a
   published version number is immutable once consumers may have resolved
   it (standard Maven/Gradle repository hygiene; re-publishing under the
   same coordinates is a correctness hazard for anyone who already cached
   it).
2. Publish a new PATCH version with the fix, as fast as reasonable.
3. Add a note to `CHANGELOG.md` under the broken version's own entry (in
   addition to the new fixed version's entry) flagging it as broken and
   pointing at the fix version, so anyone reading history understands not
   to use it.
4. If the break is severe (data loss, security issue), also update
   `SECURITY.md`'s reporting/disclosure trail if applicable.

## Snapshot vs. release builds

This repository has not yet cut a real `1.0.0` — all modules are currently
at `0.0.1-SNAPSHOT`. The checklist above is written for when that first
real release happens; until then, `publishToMavenLocal` continues to work
for local/second-consumer validation exactly as it does today (see
`SECOND_CONSUMER_VALIDATION.md`), but no `-SNAPSHOT` artifact should be
treated as durable or referenced by anything outside active development.
