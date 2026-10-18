# Roadmap

This roadmap documents completed work and realistic near-term plans. It
does not promise features that haven't been designed — see
`TECHNICAL_DEBT_REGISTER.md` for known gaps that are candidates for future
work, not commitments.

## Completed (through Phase 7 / target 1.0.0)

- Extraction of the BSM domain, application services, and 40+ ports into a
  framework-agnostic `bsm-core` library, architecturally enforced by
  ArchUnit (`ArchitectureTest`).
- Capability-based platform abstraction (`libs/security-spi`) replacing
  direct CPMS-specific coupling.
- Resolution of JPA optimistic-lock exception leakage and REST-DTO leakage
  across bsm-core's public interfaces.
- `bsm-spring-boot-starter`: ten `@AutoConfiguration` classes, anchor-port
  wiring strategy, `BsmPortAvailabilityValidator` fail-fast validation, 15+
  `ApplicationContextRunner` tests.
- `bsm-demo`: independent second-consumer application, validated via both
  project dependencies and published Maven Local artifacts.
- Full documentation and governance set (see `CHANGELOG.md`'s 1.0.0 entry
  for the complete list).
- Dependency, public API, port, and configuration audits (`DEPENDENCY_AUDIT.md`,
  `PUBLIC_API.md`, `PORT_REFERENCE.md`, `CONFIGURATION_REFERENCE.md`).

## Planned — 1.x

Backward-compatible additions, no breaking changes required:

- Auto-configure `SubscriptionAddOnService` and `SubscriptionChangeService`
  in the starter (currently require manual wiring — see
  `TECHNICAL_DEBT_REGISTER.md`). This is additive: existing consumers who
  already manually wire these services keep working; the starter would
  simply stop requiring that manual step for new consumers.
- Provide a real (non-stub) reference implementation guidance for
  `ProjectUsagePort`/`StorageUsagePort` in documentation, since these
  currently only have stub adapters in `bsm-svc` (see
  `TECHNICAL_DEBT_REGISTER.md`).
- Expand `bsm-demo`'s port coverage beyond the current 24/41 exercised
  ports, closing gaps identified in `SECOND_CONSUMER_VALIDATION.md`, as
  time allows — this strengthens the portability proof without changing
  any library behavior.
- Resolve the `ReconciliationProperties.intervalMs` unused-field finding
  and the scheduler default mismatches documented in
  `CONFIGURATION_REFERENCE.md` — these are `bsm-svc` host-level cleanups,
  not library API changes, so they don't require a `bsm-core` version bump.

## Under consideration — 2.x (breaking changes, not yet committed)

These would each require a MAJOR version bump per `VERSIONING.md` and are
listed here as candidates for discussion, not as promises:

- Implementing or formally removing `BsmAuthorizationPort`, which currently
  has zero implementations anywhere in the codebase (see
  `TECHNICAL_DEBT_REGISTER.md`) — either give it a real purpose or delete
  it; leaving an unimplemented SPI method surface indefinitely is not a
  good long-term state for a published library.
- Promoting `CommercialEngineService` and `BillingDashboardService` from
  Experimental tier into `bsm-core` proper (currently their implementations
  live in `bsm-svc` and depend on CPMS-specific types — see
  `TECHNICAL_DEBT_REGISTER.md`) — this would require first removing their
  CPMS-specific dependencies, which is nontrivial.
- Consolidating `bsm-svc`'s dual AWS S3 SDKs (v1 and v2) down to one — a
  `bsm-svc`-only change with no `bsm-core` API impact, but worth tracking
  here since it's a real piece of technical debt.

## Explicitly not planned

- No plans to support Java versions below 21 or Spring Boot versions below
  the 4.x line documented in `COMPATIBILITY.md` — no request for this has
  been made and no work has started.
- No plans for a non-Spring integration (e.g. a Micronaut or Quarkus
  starter) — `bsm-core` remains usable by any framework because it's
  framework-agnostic already, but a dedicated starter for another framework
  is speculative and out of scope until someone actually needs it.
