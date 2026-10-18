# ADR-003 — Starter Port-Availability Validation: Anchor-Port Detection, Not Any-Required-Port Detection

**Status:** Accepted
**Date:** 2026-07-22
**Affects:** `ppm-spring-boot-starter`'s `PpmPortAvailabilityValidator` and `AggregatePortRequirement` (package `com.company.ppmstarter`)

---

## Context

`ppm-spring-boot-starter` auto-registers one use-case bean per `ppm-core` aggregate, but only when all of that aggregate's required repository ports are present as Spring beans (`@ConditionalOnBean`). An aggregate with zero of its ports implemented is not an error — that's a consumer who doesn't want it. But a consumer who implements *some but not all* of an aggregate's required ports has almost certainly made a mistake (forgot one bean), and the failure they'd otherwise get — a generic `NoSuchBeanDefinitionException` when some unrelated bean later tries to `@Autowired` the missing use-case service — gives no hint about which port was the actual cause.

The first implementation of `PpmPortAvailabilityValidator` detected this by checking: "is *any* of this aggregate's required ports present, but not all of them?" This seemed correct and shipped with an initial (passing) unit test suite.

## The problem this ADR records

While writing the starter's own `ApplicationContextRunner`-based tests (a completely ordinary "register only the ports I actually want" scenario — registering `PlanRepositoryPort` and `PlanVersionRepositoryPort` together to test `PlanApplicationService`), the validator raised **false positives** against five unrelated aggregate groups: `PlanAddOn`, `PlanModule`, `PlanEntitlement`, `PromoCodePlan`, and `PlanPrice`. All five failed with "partially wired" errors, even though the test never intended to wire any of them.

Root cause: `PlanRepositoryPort` is required by nearly every aggregate in `ppm-core` — Plan itself, and every join aggregate that verifies its parent plan exists before mutating a relationship (`PlanAddOn`, `PlanModule`, `PlanEntitlement`, `PromoCodePlan`, `PlanPrice`, `CatalogQueryService`). "Any required port present" is not evidence of intent toward *any specific one* of these — a consumer implementing only `PlanRepositoryPort` + `PlanVersionRepositoryPort` (because they want Plan itself) trivially satisfies "some port of `PlanAddOn` is present" without wanting `PlanAddOn` at all.

`CatalogQueryService` and `PromoValidationService` have an even more fundamental version of this problem: **all** of their required ports belong to other, independently-adoptable aggregates. There is no "one cohesive join" a consumer implements together for either of them — their entire port list is the union of aggregates a consumer might reasonably want on their own, for unrelated reasons. There is no way to distinguish "this composite was partially wired by mistake" from "these underlying aggregates were each fully and correctly wired for their own sake, and just happen to overlap."

## Decision

Two changes, both applied before this ADR was written (the fix and this record were done together, per the extraction project's practice of documenting a design decision at the point it's made, not retroactively):

1. **Anchor-port detection, not any-required-port detection.** Each `AggregatePortRequirement` now names a single `anchorPort` — a port genuinely unique to that aggregate, not shared with any independently-adoptable aggregate (e.g. `PlanAddOnRepositoryPort` for the `PlanAddOn` group, `PlanVersionRepositoryPort` for the `Plan`/`PlanVersion` group). The validator only flags a group as "partially wired" when its anchor is present but some other required port is missing. Presence of a widely-shared port like `PlanRepositoryPort` alone never triggers a violation on its own.

2. **`CatalogQueryService` and `PromoValidationService` are excluded from the validator entirely** — not given a (nonexistent) anchor, simply not modeled as `AggregatePortRequirement` entries. `CatalogQueryService`'s registration is separately gated by the opt-in `ppm.catalog.enabled` property (see `PpmProperties`), which is the correct mechanism for "don't silently create a heavy composite service" — a concern distinct from "warn me if I forgot a port."

A regression test (`planRepositoryPortAloneIsNotFlagged` in `PpmAutoConfigurationTest`) locks in the fix: registering only `PlanRepositoryPort` and `ModuleRepositoryPort` must not raise any validator exception.

## Consequences

- Every future aggregate added to `ppm-core` that introduces a new join or composite must be evaluated for whether it has a genuine anchor port before being added to `PpmPortAvailabilityValidator`'s `REQUIREMENTS` list. If it doesn't (i.e. all its ports belong to other independently-adoptable aggregates, the same shape as `CatalogQueryService`/`PromoValidationService`), it must be excluded from partial-wiring validation, the same way, with the same reasoning recorded in that class's comments — not silently given a wrong anchor just to have one.
- This is a case where a test suite existing *for the tool that enforces correctness* (the starter) caught a real bug in that same tool, before it shipped to a real consumer — the value of building `PpmAutoConfigurationTest` was not only "prove the starter works" but also "prove the starter's own safety net doesn't cry wolf."
