# Public API Inventory

Every public type in `ppm-core`, classified as **Public API** (consumers call/receive it directly), **SPI** (consumers implement it), or **Internal** (implementation detail, technically public for Spring proxying but never called or implemented by consumers). For each aggregate: models, use cases, ports, exceptions, and what the host must supply.

Classification is documentation only — see the [Phase 3.5 scope](../ROADMAP.md) for why visibility was not changed as part of this pass.

## Legend

- **Public API** — a consumer calls this directly, or receives it as a return value.
- **SPI** — a consumer must provide an implementation.
- **Internal** — public for Spring's benefit (`@Service` proxying, `@InjectMocks` testability) but not part of the intended consumer contract; treat as if package-private.

---

## common

| Type | Classification | Notes |
|---|---|---|
| `BaseEntity` | Public API (base class) | Consumers read `getId()`/`getVersion()`/`pullDomainEvents()` on any domain model; extended internally only, never by host code. |
| `AuditableEntity` | Public API (base class) | Consumers read audit fields (`getCreatedAt()`, etc.) on returned domain models. |
| `DomainEvent` | Public API (base class) | Consumers who wish to publish domain events subscribe to concrete subclasses; none exist yet (no aggregate currently registers an event) — this is scaffolding for future use, already in place per BaseEntity's `registerEvent`/`pullDomainEvents`. |
| `BillingCycle` | Public API (enum, wire vocabulary) | `@JsonValue`/`@JsonCreator`, same "domain vocabulary" treatment as `PlanVisibility`/`ModuleCode`. Placed in `common` rather than under `planprice` or `addonprice` because it is genuinely shared, symmetric vocabulary between two peer aggregates (`PlanPrice` and `AddOnPrice`) — neither aggregate owns the other, so no single aggregate package is the correct home. This is the first (and so far only) enum promoted to `common` for that reason; see [PACKAGE_GUIDE.md](../PACKAGE_GUIDE.md). |

## exception

| Type | Classification | Notes |
|---|---|---|
| `ErrorCode` | Public API | Consumers match on `getCode()` to build machine-readable API error responses. |
| `BusinessException` | Public API | Base type; consumers' `@RestControllerAdvice` (or equivalent) catches this as the fallback case. |
| `ResourceNotFoundException` | Public API | Maps to 404. |
| `AccessDeniedException` | Public API | Maps to 403. Distinct from Spring Security's own exception of the same simple name — do not conflate when writing exception handlers. |
| `InvalidStateException` | Public API | Maps to 409. |
| `BusinessRuleViolationException` | **Deprecated** — do not use in new code | `@Deprecated(forRemoval = true)`. Kept only for any pre-extraction caller still referencing it; prefer `BusinessException` with an explicit `ErrorCode`. |

---

## plan (Plan + PlanVersion + Catalog composite)

**Models:** `Plan`, `PlanVersion`, `PlanVisibility` (enum, `@JsonValue`/`@JsonCreator` — wire vocabulary), `PlanVersionMetaResponse`, `PlanVersionLimitsResponse`, `CatalogPlanSummaryResponse`, `CatalogPlanDetailResponse`, `CatalogVersionResponse`, `CatalogModuleResponse`, `CatalogEntitlementResponse` — all Public API (returned directly from use cases).

**Ports (SPI — host implements):** `PlanRepositoryPort`, `PlanVersionRepositoryPort`.

**Use cases (Public API):**

- `PlanApplicationService` — `createPlan`, `updatePlan`, `getPlan`, `getPlanBySlug`, `listPlans`, `deletePlan`, `getDefaultTrialPlan`, `getPlanByCode`. Nested record `PlanWithActiveVersion`. Slug is system-generated and immutable — callers never supply or change it.
- `PlanVersionApplicationService` — `createVersion`, `updateVersion`, `getVersion`, `getLatestVersion`, `listVersions`, `deleteVersion`, `getPlanVersionMeta`, `getPlanVersionLimits`. Auto-closes the previous open-ended version on create; enforces non-overlapping effective-date ranges.
- `CatalogQueryService` — `listPublicPlans`, `getPlanDetail(slug)`. Pure read engine composing five aggregates. **Not currently wired to any controller in `ppm-svc`** — dead HTTP-wise as of this writing, but kept in `ppm-core` because the composition logic is a business rule, not a presentation concern. Name evaluated and kept: "Catalog" accurately describes a cross-aggregate read composition, and "Query" signals CQRS-style read-only intent — no rename warranted.

**Exceptions thrown:** `PLAN_NOT_FOUND`, `PLAN_CODE_ALREADY_EXISTS`, `PLAN_VERSION_NOT_FOUND`, `PLAN_VERSION_ALREADY_EXISTS`, `PLAN_VERSION_DATE_CONFLICT`, `VALIDATION_ERROR`.

**Host responsibilities:** REST controller + DTOs + MapStruct mapper (`PlanController`/`PlanApiMapper`, `PlanVersionController`/`PlanVersionApiMapper` in `ppm-svc`); slug generation sequence (`ppm_plan_slug_seq`) lives in the host's persistence adapter, not in `ppm-core`.

---

## module

**Models:** `Module`, `ModuleCode` (enum, wire vocabulary) — Public API.

**Ports (SPI):** `ModuleRepositoryPort`.

**Use cases (Public API):** `ModuleApplicationService` — `createModule`, `updateModule`, `getModule`, `listModules`, `getModuleByCode`, `deleteModule`.

**Exceptions:** `MODULE_NOT_FOUND`, `MODULE_CODE_ALREADY_EXISTS`.

**Host responsibilities:** `ModuleController` + DTOs/mapper.

---

## addon

**Models:** `AddOn`, `AddOnType` (enum) — Public API.

**Ports (SPI):** `AddOnRepositoryPort`.

**Use cases (Public API):** `AddOnApplicationService` — `createAddOn`, `updateAddOn`, `getAddOn`, `getAddOnByCode`, `listAddOns`, `deleteAddOn`.

**Exceptions:** `ADD_ON_NOT_FOUND`, `ADD_ON_CODE_ALREADY_EXISTS`.

**Host responsibilities:** `AddOnController` + DTOs/mapper. Note: `AddOnController` also collaborates with `AddOnPriceApplicationService` (see the `addonprice` entry below) for its price-resolution sub-endpoint.

---

## entitlement

**Models:** `Entitlement`, `EntitlementType` (enum) — Public API.

**Ports (SPI):** `EntitlementRepositoryPort`.

**Use cases (Public API):** `EntitlementApplicationService` — `createEntitlement`, `updateEntitlement`, `getEntitlement`, `getEntitlementByCode`, `listEntitlements`, `deleteEntitlement`.

**Exceptions:** `ENTITLEMENT_NOT_FOUND`, `ENTITLEMENT_CODE_ALREADY_EXISTS`.

**Host responsibilities:** `EntitlementController` + DTOs/mapper.

---

## promocode (PromoCode + PromoValidation)

**Models:** `PromoCode`, `DiscountType` (enum), `PromoValidationReason` (enum, wire vocabulary), `PromoValidationResult` — Public API.

**Ports (SPI):** `PromoCodeRepositoryPort`.

**Use cases (Public API):**

- `PromoCodeApplicationService` — `createPromoCode`, `updatePromoCode`, `getPromoCode`, `getPromoCodeByCode` (returns `Optional<PromoCode>`), `listPromoCodes`, `deletePromoCode`.
- `PromoValidationService` — `validate(String code, UUID planId)` → `PromoValidationResult`. Pure rule engine (PV-1..PV-9): existence, active flag, validity window, usage cap, plan-restriction eligibility.

**`PromoValidationResult` vs. the host's `PromoValidationResponse` — the one deliberate type split in this library.** The host's `PromoValidationResponse` DTO is actively serialized by a live, tested `PromoValidationController` with `@JsonInclude(NON_NULL)` (discount fields are omitted from the wire response when the code is invalid). Moving that exact type into `ppm-core` would have put a serialization annotation inside the library. Instead, `ppm-core` returns `PromoValidationResult` — field-for-field identical, but with no Jackson annotations — and the host's controller maps it to `PromoValidationResponse` itself, preserving the existing wire contract exactly. If you are implementing a new host and don't care about `NON_NULL` suppression, you can serialize `PromoValidationResult` directly; if you do care, mirror `ppm-svc`'s host-side DTO + mapping step.

**Exceptions:** `PROMO_CODE_NOT_FOUND`, `PROMO_CODE_ALREADY_EXISTS`, `VALIDATION_ERROR`, `PLAN_NOT_FOUND` (thrown by `PromoValidationService.validate` when the plan doesn't exist — the only hard failure in that engine; all promo-specific failures return `valid=false` in the result instead of throwing).

**Host responsibilities:** `PromoCodeController`, `PromoValidationController` + DTOs/mappers.

---

## planmodule (join aggregate: Plan ↔ Module)

**Models:** `PlanModule` (pure UUID-FK join, no object references) — Public API, though most hosts only ever see it via the aggregated `List<Module>` returned by the use case, not the join row itself.

**Ports (SPI):** `PlanModuleRepositoryPort`.

**Use cases (Public API):** `PlanModuleApplicationService` — `assignModules` (returns `List<Module>`), `getModules` (returns `List<Module>`), `replaceModules` (atomic replace, returns `List<Module>`), `removeModule`.

**Exceptions:** `PLAN_NOT_FOUND`, `MODULE_NOT_FOUND`, `MODULE_ALREADY_ASSIGNED_TO_PLAN`, `PLAN_MODULE_MAPPING_NOT_FOUND`.

**Host responsibilities:** `PlanModuleController` + DTOs/mapper. Per [ADR-001](../../ppm-svc/docs/adr/ADR-001-plan-module-ownership.md), this join table is the single source of truth for plan feature ownership — do not build a parallel entitlement cache on top of it without re-reading that ADR.

---

## planaddon (join aggregate: Plan ↔ AddOn)

**Models:** `PlanAddOn` — Public API (returned as-is by the use case; unlike `PlanModule`, this one is not resolved into `List<AddOn>` — the host resolves `AddOn` details itself if needed).

**Ports (SPI):** `PlanAddOnRepositoryPort`.

**Use cases (Public API):** `PlanAddOnApplicationService` — `assignAddOns`, `replaceAddOns` (abort-before-delete atomic replace), `getPlanAddOns` (returns `List<PlanAddOn>`), `removeAddOn`.

**Exceptions:** `PLAN_ADD_ON_ALREADY_ASSIGNED`, `PLAN_ADD_ON_MAPPING_NOT_FOUND`.

**Host responsibilities:** `PlanAddOnController` + DTOs/mapper.

---

## planentitlement (join aggregate: Plan ↔ Entitlement + resolver)

**Models:** `PlanEntitlement`, `ResolvedEntitlementResponse` (plain record — despite the "Response" suffix, this is a domain read-model, not a host DTO; carries zero framework annotations) — Public API.

**Ports (SPI):** `PlanEntitlementRepositoryPort`.

**Use cases (Public API):**

- `PlanEntitlementApplicationService` — `assignEntitlements`, `replaceEntitlements`, `getPlanEntitlements`, `removeEntitlement`. All the "get"-shaped methods return `List<ResolvedEntitlementResponse>` (code/value pairs), not the raw `PlanEntitlement` join rows.
- `EntitlementResolver` — narrower single-method interface (`resolveEntitlements(planId)`) intended for downstream services (e.g. a future gateway or provisioning engine) that only need the read path, not the full assignment-management surface. Implemented by `DefaultEntitlementResolver`, which is **Internal** (implementation detail; consumers inject the `EntitlementResolver` interface, never the `Default*` class).

**Exceptions:** `PLAN_NOT_FOUND`, `ENTITLEMENT_NOT_FOUND`, `PLAN_ENTITLEMENT_ALREADY_ASSIGNED`, `PLAN_ENTITLEMENT_MAPPING_NOT_FOUND`.

**Host responsibilities:** `PlanEntitlementController` + DTOs/mapper.

---

## promocodeplan (join aggregate: PromoCode ↔ Plan restriction)

**Models:** `PromoCodePlan` (plain POJO, hard-deleted not soft-deleted — no `AuditableEntity`) — Public API.

**Ports (SPI):** `PromoCodePlanRepositoryPort`.

**Use cases (Public API):** `PromoCodePlanApplicationService` — `assignPlans`, `replacePlans` (validate-all-before-delete atomic replace), `getRestrictedPlans` (empty list = unrestricted, applies to all plans), `removePlan`.

**Exceptions:** `PROMO_CODE_NOT_FOUND`, `PLAN_NOT_FOUND`, `PROMO_CODE_PLAN_ALREADY_ASSIGNED`, `PROMO_CODE_PLAN_MAPPING_NOT_FOUND`.

**Host responsibilities:** `PromoCodePlanController` (or wherever `ppm-svc` exposes promo-code plan restriction management) + DTOs/mapper.

---

## Internal implementation classes (all aggregates)

Every `<Aggregate>ApplicationServiceImpl` class (`PlanApplicationServiceImpl`, `ModuleApplicationServiceImpl`, ... `PromoValidationServiceImpl`) plus `DefaultEntitlementResolver` and `CatalogQueryServiceImpl` are **Internal**: they must be `public` for Spring to proxy them as `@Service` beans, but a consumer should always type against the interface, never the `Impl` class directly. None of these are referenced by class name anywhere in `INTEGRATION_GUIDE.md` for exactly this reason.

## planprice (PlanPrice + PricingResolver)

**Models:** `PlanPrice` — Public API.

**Ports (SPI):** `PlanPriceRepositoryPort`.

**Use cases (Public API):**

- `PlanPriceApplicationService` — `createPrice`, `updatePrice`, `getPrice`, `listPrices`, `deletePrice`. Pricing identity is `(planId, region, currency, cycle, effectiveFrom)`; region/currency are normalised to uppercase before persistence; `taxInclusive` defaults to `false`.
- `PricingResolver` — `resolvePrice(planId, region, currency, cycle)` → `PlanPrice`. Pure read engine (PR-1..PR-7): plan existence, exact region/currency match (no fallback, no currency conversion), active + not-yet-effective filtering, latest-`effectiveFrom`-wins selection. Co-located with `PlanPrice` (not a separate top-level package) because it resolves exclusively over `PlanPrice` rows — same rationale as `EntitlementResolver` co-locating with `PlanEntitlement`.

**Host DTO note:** unlike `PromoValidationService`, no type split was needed here — the host's `ResolvedPriceResponse` and `PlanPriceResponse` carry no `@JsonInclude` or other serialization annotations, so the host's `PlanPriceApiMapper` maps the returned `PlanPrice` domain model directly.

**Exceptions:** `PLAN_NOT_FOUND`, `PLAN_PRICE_NOT_FOUND`, `PLAN_PRICE_ALREADY_EXISTS`, `PLAN_PRICE_NOT_RESOLVED`, `VALIDATION_ERROR`.

**Host responsibilities:** `PlanPriceController`, `PricingResolverController` + DTOs/`PlanPriceApiMapper`; JPA entity, persistence mapper, `PlanPriceJpaRepository`, and the `BillingCycleConverter` JPA `AttributeConverter` all remain host-side.

---

## addonprice (AddOnPrice)

**Models:** `AddOnPrice` — Public API.

**Ports (SPI):** `AddOnPriceRepositoryPort` — note this port exposes full CRUD (`save`, `findById`, `findAll`, `findByAddOnId`, `exists`, `findActivePrice`, `softDelete`), but `AddOnPriceApplicationService` currently exposes only `resolveActivePrice`. This asymmetry (full port, narrow use-case) predates the migration and was preserved as-is — no create/update/list/delete surface was added; YAGNI applies here as it did before extraction.

**Use cases (Public API):** `AddOnPriceApplicationService` — `resolveActivePrice(addOnId, region, currency, cycle)` → `AddOnPrice`. The single operation needed by BSM C4: resolving the currently-effective active price for a purchasable add-on.

**Exceptions:** `ADD_ON_PRICE_NOT_FOUND`.

**Host responsibilities:** `AddOnController` (the `/{addOnId}/prices/active` sub-resource endpoint — there is no dedicated `AddOnPriceController`) owns the `AddOnPrice` → `AddOnPriceResponse` mapping itself, since no host mapper class existed for this type even before the migration.

---

## Migration complete

As of Batch 4, every reusable PPM business aggregate has been migrated into `ppm-core`. See [ROADMAP.md](../ROADMAP.md) for the full history and the Phase 5 Library Readiness Audit that follows.
