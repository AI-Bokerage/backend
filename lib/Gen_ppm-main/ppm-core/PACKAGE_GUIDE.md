# Package Guide

`ppm-core` is organized **feature-first, then by DDD concept** — every business aggregate gets its own top-level package, and within it, up to three fixed subpackages. This document describes the convention and, just as importantly, when *not* to deviate from it.

This convention was set by [ADR-002](../ppm-svc/docs/adr/ADR-002-ppm-core-extraction-pattern.md) and is frozen — do not restructure it without a genuine architectural blocker.

## The shape

```
com.company.ppmsvc
├── common/              # BaseEntity, AuditableEntity, DomainEvent — foundational, cross-aggregate
├── exception/           # BusinessException, ErrorCode, ResourceNotFoundException, etc. — shared, not per-aggregate
└── <aggregate>/         # e.g. plan, module, entitlement, promocode, planmodule
    ├── model/           # domain model classes + aggregate-local enums
    ├── port/            # outbound repository ports
    └── usecase/         # use-case interface + implementation, same package
```

## The two foundational packages

- **`common`** — types every aggregate depends on: `BaseEntity` (identity, optimistic-lock version, domain-event buffer), `AuditableEntity` (adds `createdAt`/`updatedAt`/`createdBy`/`updatedBy`), `DomainEvent` (event base class). Nothing aggregate-specific belongs here.
- **`exception`** — `BusinessException` and its typed subclasses, plus `ErrorCode`. Shared across every aggregate because the host's exception handler maps exception *type* to HTTP status uniformly; aggregate-specific error codes live as enum constants inside the shared `ErrorCode`, not as separate per-aggregate exception classes.

## The three aggregate subpackages

### `model/`

Domain model classes and any enum whose values are part of that aggregate's vocabulary (e.g. `ModuleCode`, `PlanVisibility`, `DiscountType`, `PromoValidationReason`). Also holds plain read-model records that are a byproduct of that aggregate's own reads (e.g. `PlanVersionMetaResponse`, `ResolvedEntitlementResponse`) — these are still domain types, just shaped for a specific query rather than for persistence.

No JPA, no Spring MVC, no Bean Validation annotations. Jackson annotations are permitted only when the wire value genuinely *is* domain vocabulary (see the Jackson note in ADR-002) — not for generic serialization convenience.

### `port/`

Outbound repository port interfaces — one per aggregate root, named `<Aggregate>RepositoryPort`. Interface only; `ppm-core` never provides an implementation. Method signatures speak only in domain models and primitives.

A second inbound-port style (e.g. a message-driven trigger for the same use case) would warrant a `port/inbound` subpackage — this doesn't exist yet for any aggregate, so don't pre-create it.

### `usecase/`

The use-case interface (the inbound port, in hexagonal terms) and its implementation, **in the same package** — not a nested `impl/` subpackage. Naming: `<Aggregate>ApplicationService` / `<Aggregate>ApplicationServiceImpl`. A few aggregates deviate from that exact suffix where the name better reflects the responsibility (`CatalogQueryService`, `PromoValidationService`, `EntitlementResolver` — see [docs/API_INVENTORY.md](docs/API_INVENTORY.md) for why each of those names was kept rather than normalized).

## Co-located aggregates

Not every aggregate gets a 1:1 package. Two examples, both deliberate:

- **`plan/`** holds `Plan`, `PlanVersion`, and the cross-aggregate `CatalogQueryService` read model. `PlanVersion` is a child concept of `Plan` (versioned snapshots of the same catalog entry) — it was never given its own top-level package. The catalog composite lives here because its center of gravity is "how a plan and its version are presented," not a new aggregate root of its own.
- **`planentitlement/`** holds `PlanEntitlement`, its resolved-value read model, and `EntitlementResolver`/`DefaultEntitlementResolver`. The resolver was migrated alongside `PlanEntitlement` rather than into a new package because resolving entitlements *is* `PlanEntitlement`'s primary read capability, not a separate concern.

When co-locating, ask: does the second type exist *because of* the first aggregate, or does it merely *use* the first aggregate? The former co-locates; the latter gets its own package (e.g. `CatalogQueryService` uses `Module` and `Entitlement` but is not "the module aggregate's use case" — it lives in `plan` because a catalog listing is fundamentally about plans).

## When to add a new package

Add a new top-level aggregate package when a genuinely new aggregate root is introduced (has its own identity, its own repository port, its own lifecycle) — not simply because a new class is being written.

Add a new subpackage inside an aggregate (beyond `model`/`port`/`usecase`) only when there is something concrete to put in it right now. Do not pre-create empty `policy/` or `workflow/` packages for a shape that doesn't apply yet — the Plan aggregate has none of these because Plan currently has no cross-aggregate business rules of its own.

## What never belongs in `ppm-core`

`api`, `controller`, `infrastructure`, `persistence`, `adapter`, or a service-oriented `config` package. These are host-layer names; their presence inside `ppm-core` is itself a signal that something host-specific has leaked into the library. If you find yourself wanting one, the code probably belongs in the host, not here.
