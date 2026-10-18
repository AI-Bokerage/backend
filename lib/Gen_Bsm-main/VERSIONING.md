# Versioning Policy

`bsm-core`, `bsm-spring-boot-starter`, and `bsm-demo`'s published artifacts
(`bsm-demo` itself is not published — see `RELEASE_PROCESS.md`) follow
Semantic Versioning (SemVer 2.0.0): `MAJOR.MINOR.PATCH`.

`bsm-core` and `bsm-spring-boot-starter` are versioned and released
together, in lockstep, at the same version number — the starter's whole
purpose is wiring bsm-core, and a starter version that doesn't match its
bsm-core version is a support burden with no benefit. There is currently no
scenario in this project's design where they'd need independent version
numbers.

## What each part means

- **PATCH** (`1.0.x`): bug fixes, documentation corrections, dependency
  patch bumps, internal refactors — no change to any Public Library API or
  Extension SPI signature (see `PUBLIC_API.md` for tier definitions). Safe
  to upgrade without code changes.
- **MINOR** (`1.x.0`): backward-compatible additions — a new port, a new
  application service, a new optional configuration property, a new
  overload. Existing consumers keep compiling and keep working unchanged.
  Safe to upgrade without code changes; new capabilities are opt-in.
- **MAJOR** (`x.0.0`): breaking changes — anything in the "What counts as
  breaking" list below. Consumers should expect to read the changelog and
  possibly change code before upgrading.

## What counts as a breaking change

Any of the following requires a MAJOR version bump:

- Adding, removing, or changing a method signature on any interface
  classified as **Extension SPI** in `PUBLIC_API.md` (the 40 ports plus
  `PaymentGatewayResolver`) — every implementer (bsm-svc adapters, bsm-demo
  adapters, any external consumer's adapters) must recompile against the
  new shape.
- Removing or renaming a method on any interface classified as **Public
  Library API** — consumers call these directly.
- Removing a public class entirely, or moving it to a different package.
- Changing the meaning of an existing configuration property (not just
  adding a new one) — e.g. changing what `bsm.trial.default-days` measures,
  or changing a default value in a way that changes behavior for consumers
  who never set the property explicitly.
- Changing an `@AutoConfiguration` class's `@ConditionalOnBean` requirements
  in a way that makes a previously-sufficient port set insufficient (a
  consumer who was fully wired before the upgrade would now fail
  `BsmPortAvailabilityValidator`'s startup check).
- Any change to `ArchitectureTest`'s enforced rules that a previously
  compliant consumer's *own* code (not this repo's) could now violate. (This
  is rare, since `ArchitectureTest` only constrains `bsm-core`'s own source,
  not consumer code — listed for completeness.)

## What does NOT count as breaking

- Adding a new port, service, or configuration property with a sensible
  default.
- Adding a new method to a **Public Library API** interface's *implementing
  class* without changing the interface (not applicable today — these are
  interfaces, not abstract classes, so this doesn't arise, but documented
  for clarity).
- Internal-tier class changes (see `PUBLIC_API.md`) — these carry no
  compatibility guarantee at all, at any version.
- Reclassifying a class from Experimental to Public Library API tier (this
  is a promotion, not a break — see below).

## Experimental tier and pre-1.0 types

Classes marked **Experimental** in `PUBLIC_API.md` (currently:
`CommercialEngineService` and `BillingDashboardService`, both of which
still live in `bsm-svc` rather than `bsm-core` — see
`TECHNICAL_DEBT_REGISTER.md`) carry no compatibility guarantee at all, at
any version, until they are promoted to Public Library API tier in a
release note. A breaking change to an Experimental type is a MINOR or even
PATCH release, not a MAJOR one — but it will always be called out in
`CHANGELOG.md`.

## Deprecation policy

A Public Library API or Extension SPI member is never removed in the same
release it's deprecated:

1. Mark it `@Deprecated` with a Javadoc note explaining the replacement and
   the release it was deprecated in.
2. Keep it functional for at least one MINOR release cycle after
   deprecation.
3. Remove it only in a MAJOR release, called out explicitly in
   `CHANGELOG.md`'s "Removed" section.

## Removal policy

Removal of a deprecated member is always a MAJOR bump, always documented in
`CHANGELOG.md` with the version it was originally deprecated in, so
consumers can gauge how long they have to migrate.
