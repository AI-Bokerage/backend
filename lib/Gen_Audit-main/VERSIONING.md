# Versioning

## `audit-core`

`audit-core` follows semantic versioning as described in [the root README's "The Dependency
Rule" section](README.md#the-dependency-rule-audit-core) — there is no separate
`audit-core/README.md`; a stale link to one was found and fixed by a release-readiness audit. The
`api` and `port` packages are the versioned public surface, `internal` carries no compatibility
guarantee and can change in any release.

Current version: **0.4.0** (Phase 8 added `AuditAppender.create(ChainRepository, Clock, int)`, an
additive overload — the existing 2-arg overload's default behavior is unchanged — → minor bump).
Made only once measured evidence (not speculation) showed it was needed: see `CHANGELOG.md`'s
Phase 8 entry and `docs/adr/0001-distributed-ordering-advisory-locks.md`. Before that, `0.3.0`
(Phase 6 added `port.ObjectLockPort`, additive → minor bump). Published to Maven as of Phase 7
Part B, but publishing itself is packaging, not a code change — publishing `audit-core` for the
first time did not, on its own, move its version. See "How a release decision gets made" below.

## `audit-spring-boot-starter`

Versioned independently from `audit-core` as of Phase 7 Part B: they are separate published
artifacts with separate compatibility surfaces, even though this module depends on that one via
an ordinary Gradle project reference internally (translated to the correct
`com.company.audit:audit-core:<version>` Maven coordinate in the generated POM automatically).

Current version: **0.3.1**. Its first version, `0.1.0`, was set in Phase 7 Part B — prior to that
this module had no explicit version at all. `1.0.0` is still reserved for real external
validation (an actual CPMS service depending on this starter in production), same reasoning
`audit-core` already applied to its own path to `1.0.0`.

**Retroactive correction, applied by a release-readiness audit**: Phase 8 and Phase 9 each added
additive surface to this module — `AuditMetricsRecorder` gaining
`recordPartitionLockWait`/`recordStalenessRejection`/`recordLockTimeout` in Phase 8, and
`sharding.PartitionShardResolver` plus `recordShardOwnershipAccepted`/`recordShardMismatch` in
Phase 9 — without the version moving off `0.1.0` at the time. That was an oversight, not a
deliberate exception; this file's own mechanical rule (below) says each of those phases should
have been a minor bump. Applied after the fact: `0.1.0` → `0.2.0` (Phase 8) → `0.3.0` (Phase 9's
additive sharding surface) → `0.3.1` (Phase 9's message-converter fix — a bug fix to
`RabbitEventConsumer`'s internal listener wiring, not a change to any documented public surface,
so patch rather than minor).

## `audit-demo`

Never published, and carries no compatibility guarantee at any version — it doesn't have one.

## How a release decision gets made

- **Additive, backward-compatible change to `audit-core`'s `api`/`port`** (new constructor
  overload, new optional field, new default method) → minor version bump.
- **Breaking change to `audit-core`'s `api`/`port`** (removed/renamed method, changed field
  semantics) → major version bump. None have happened yet.
- **Any change confined to `audit-core`'s `internal` package** → no version policy implication;
  document in `CHANGELOG.md` for traceability, but it is not part of the compatibility contract.
- **Change to the starter's surface, pre- or post- its own first version** → documented in
  `CHANGELOG.md`; once the starter has a version (Phase 7 Part B onward), the same
  additive-minor/breaking-major/neither-unchanged rule that governs `audit-core` applies to it
  too — mechanically, not by how significant a phase "feels."
- **Packaging-only change (build/publish infrastructure, no source or API change) to either
  publishable module** → version does not move. Phase 7 Part B is the concrete precedent: it made
  `audit-core` and `audit-spring-boot-starter` real Maven artifacts without touching either
  module's code, so `audit-core` stayed at `0.3.0` — publishing something for the first time is
  not, by itself, a reason to bump its version.
