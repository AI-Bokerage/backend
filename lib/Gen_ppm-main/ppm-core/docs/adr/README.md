# Architecture Decision Records

Index of ADRs governing `ppm-core` and the wider PPM service. ADR files themselves currently live under `ppm-svc/docs/adr/` for historical reasons (they predate the `ppm-core` module split) — this index is the canonical entry point regardless of which module's `docs/` folder a given file physically sits in.

| ADR | Title | Status | Governs |
|---|---|---|---|
| [ADR-001](../../../ppm-svc/docs/adr/ADR-001-plan-module-ownership.md) | Plan → PlanModule → Module as single source of truth for feature ownership | Accepted | The `planmodule` join aggregate; any future feature resolving "what can this tenant do?" must walk `Plan → PlanModule → Module`, never denormalize or duplicate the join. |
| [ADR-002](../../../ppm-svc/docs/adr/ADR-002-ppm-core-extraction-pattern.md) | `ppm-core` extraction pattern (library boundaries, package convention, migration checklist) | Accepted | The library/host boundary, the feature-first package convention documented in [PACKAGE_GUIDE.md](../../PACKAGE_GUIDE.md), the actor-identity-as-parameter rule, and the per-aggregate migration checklist every batch (1 through 4, the full extraction) followed without a single revision. |
| [ADR-003](../../../ppm-svc/docs/adr/ADR-003-starter-port-validation-design.md) | Spring Boot starter port-availability validation: anchor-port detection, not any-required-port detection | Accepted | `ppm-spring-boot-starter`'s `PpmPortAvailabilityValidator` and `AggregatePortRequirement` — why partial-port-wiring detection anchors on each aggregate's genuinely unique port instead of any required port, and why `CatalogQueryService`/`PromoValidationService` are excluded from that validator entirely. |

## Adding a new ADR

1. Number sequentially (`ADR-003-...md`), place it under `ppm-svc/docs/adr/` alongside the existing two, following their heading structure (Status / Date / Affects / Context / Decision / Consequences).
2. Add a row to the table above.
3. If the ADR changes anything described in [ARCHITECTURE.md](../../ARCHITECTURE.md) or [PACKAGE_GUIDE.md](../../PACKAGE_GUIDE.md), update those documents in the same change — they should never describe a decision that contradicts an Accepted ADR.
4. An ADR is only superseded by another ADR, never silently ignored — if a later decision reverses an earlier one, mark the earlier one's Status as `Superseded by ADR-00N` rather than deleting it.
