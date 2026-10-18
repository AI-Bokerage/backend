# ppm-demo

Independent second consumer of `ppm-core` — built to answer one question: **can a team that has never seen `ppm-svc` pick up `ppm-core` and use it?**

## What this proves

- `ppm-demo` depends on exactly one thing: `implementation project(':ppm-core')`. No class, DTO, mapper, or config was copied from `ppm-svc`.
- Its own code lives under `com.company.ppmdemo` — a different base package than the library (`com.company.ppmsvc`), proving `ppm-core` does not require a consumer to share its namespace.
- It implements six repository ports (`PlanRepositoryPort`, `PlanVersionRepositoryPort`, `ModuleRepositoryPort`, `PlanModuleRepositoryPort`, `PromoCodeRepositoryPort`, `PromoCodePlanRepositoryPort`) with a trivial in-memory `ConcurrentHashMap`-backed adapter each — proof that the ports are genuinely storage-agnostic, not JPA-shaped in disguise.
- It exercises a representative slice of the public API end-to-end: create a plan (auto-generated slug via `nextSlugSequenceValue()`), list plans, create a module, assign the module to the plan, create a promo code, and validate it (both a valid and an invalid case). Originally proven via a manual `curl` session against a live `bootRun` instance (see the Phase 5 report) — now also codified as an automated test, `PpmDemoApplicationIntegrationTest`, which boots the real application (real ppm-core beans, real in-memory adapters, real Spring MVC dispatch, `@MockBean`-free) and runs the same sequence over `MockMvc`. This is the regression net for `ppm-core` and `ppm-spring-boot-starter`: if a change to either breaks this test, a real consumer's wiring broke.
- `ConcurrentUseCaseThreadSafetyTest` empirically validates the thread-safety claim in the Phase 5 report (every use-case bean is a stateless singleton) by firing 200 concurrent `createPlan` calls and 200 concurrent reads through the real Spring context, rather than relying on static field inspection alone.

## What this deliberately does NOT do

- No security, no persistence, no messaging — those are host concerns `ppm-core` never touches, so a demo proving library reusability doesn't need them either.
- No exhaustive port coverage. Only the aggregates actually exercised (Plan/PlanVersion, Module, PlanModule, PromoCode/PromoCodePlan) have adapters. See the two findings below for what that surfaced.

## Two real findings this surfaced (see the Phase 5 report for full detail)

1. **Broad `@ComponentScan` of `com.company.ppmsvc` instantiates every use-case bean across all thirteen aggregates**, not just the ones a consumer calls — because ppm-core has no per-aggregate scan boundary (e.g. no `spring.factories`-style auto-configuration scoping). A consumer must scan only the specific aggregate sub-packages it needs, or implement every port in the library. This app scans four sub-packages deliberately (`plan`, `module`, `promocode`, `planmodule`) rather than the whole library.
2. **`CatalogQueryServiceImpl` co-locates in the `plan` package** (per `PACKAGE_GUIDE.md`) but depends on four other aggregates' ports (Module, Entitlement, PlanModule, PlanEntitlement). Scanning `com.company.ppmsvc.plan` for `PlanApplicationService` alone also drags this composite service in. This app excludes it explicitly via a component-scan filter, since it has no catalog-listing endpoint.

Neither finding required a change to `ppm-core` itself — both are documented as guidance for `INTEGRATION_GUIDE.md` instead.

## Running it

```bash
./gradlew :ppm-demo:bootRun
# in another terminal:
curl -X POST localhost:8199/demo/plans -H 'Content-Type: application/json' \
  -d '{"code":"starter","name":"Starter Plan","trialDays":14}'
```

See `DemoController` for the full endpoint list.
