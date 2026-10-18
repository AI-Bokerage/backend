# Compatibility Policy

## Supported platform versions

| Platform | Supported version | Notes |
|---|---|---|
| Java | 21 (toolchain-pinned) | All modules declare `JavaLanguageVersion.of(21)` in their Gradle `java { toolchain { } }` block. Not tested against other Java versions. |
| Spring Boot | 4.0.6 | `bsm-core` and `bsm-spring-boot-starter` import `spring-boot-dependencies:4.0.6` as a BOM via `io.spring.dependency-management`. `bsm-core`'s own `api` dependencies (`spring-context`, `spring-tx`) resolve to the versions that BOM provides (currently 7.0.7). |
| Gradle | 9.x (see `gradle/wrapper/gradle-wrapper.properties` for the exact pinned version used by this repo's own build) | Consumers using Gradle are not required to match this repo's Gradle version — only to resolve the published artifacts' Gradle Module Metadata correctly, which any reasonably current Gradle version does. |
| Maven | Any version supporting standard POM consumption | The published POM for `bsm-core`/`bsm-spring-boot-starter` has no Maven-specific requirements beyond the Java 21 toolchain requirement on the consumer's own build. |

## Backward-compatibility guarantees

Within a MAJOR version line (e.g. all of `1.x.y`):

- Every **Public Library API** interface method signature is stable — see
  `PUBLIC_API.md` for the tier classification.
- Every **Extension SPI** (port) method signature is stable — a consumer's
  adapter written against `1.0.0` still compiles and works against `1.3.0`.
- Every configuration property's meaning and default is stable, unless
  explicitly called out as a bug fix in `CHANGELOG.md` (in which case the
  fix corrects behavior that was already wrong, not a compatibility break).
- `bsm-spring-boot-starter`'s existing `@AutoConfiguration` gating
  (`@ConditionalOnBean` sets per aggregate, described in `PORT_REFERENCE.md`
  and `STARTER_GUIDE.md`) does not become stricter — a port set that
  satisfies an aggregate today keeps satisfying it within the same MAJOR
  line.

**Internal**-tier classes (see `PUBLIC_API.md`) carry no guarantee at any
version — they can change or disappear in a PATCH release. **Experimental**-
tier classes carry no guarantee until promoted (see `VERSIONING.md`).

## What is explicitly NOT guaranteed

- Wire/serialization format stability for any domain model — `bsm-core`
  ships no Jackson annotations (confirmed in `DEPENDENCY_AUDIT.md`) and
  makes no promise about how a consumer's own JSON mapping of domain models
  behaves across versions. If you serialize domain models directly, that's
  your own compatibility surface to manage.
- Database schema compatibility — `bsm-core` has no schema; `bsm-svc`'s
  Flyway migrations are host-specific and not part of the library's
  compatibility contract at all.
- Behavior of **Internal**-tier or **Experimental**-tier classes.
- Exact log message text or log level choices (not currently a stable
  concern for `bsm-core`, which has minimal direct logging — see
  `DEPENDENCY_AUDIT.md`'s note on `@Slf4j` usage).

## Cross-module version compatibility

`bsm-spring-boot-starter` version `X.Y.Z` requires exactly `bsm-core`
version `X.Y.Z` (see `VERSIONING.md` — they release in lockstep). Mixing
versions (e.g. starter `1.1.0` with core `1.0.0`) is unsupported and
untested.

`bsm-svc` (this repo's reference host) and `bsm-demo` (this repo's
second-consumer proof) both currently consume `bsm-core`/
`bsm-spring-boot-starter` at `0.0.1-SNAPSHOT` via project dependency — see
`RELEASE_PROCESS.md` for what changes once a real `1.0.0` is cut.
