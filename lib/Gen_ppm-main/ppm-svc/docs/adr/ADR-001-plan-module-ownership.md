# ADR-001 — Plan → PlanModule → Module as Single Source of Truth for Feature Ownership

**Status:** Accepted  
**Date:** 2026-06-18  
**Affects:** PPM-03 and all downstream features (PPM-04 Entitlement Engine onward)

---

## Context

The PPM service manages three core domain concepts:

- **Module** — a named platform capability (e.g. `lead_management`, `project_management`).  
  Defined once in the module catalog. Immutable in identity; can be activated/deactivated.

- **Plan** — a subscription tier offered to tenants (e.g. Starter, Growth, Enterprise).  
  Defined once in the plan catalog. Has visibility, trial days, and lifecycle state.

- **PlanModule** — a join aggregate that records which modules a plan includes.  
  Created when a module is assigned to a plan; hard-deleted when the assignment is removed.

PPM-04 (Entitlement Engine) and any future feature that asks "what can this tenant do?" must resolve that question by walking this chain:

```
Plan
  └─ PlanModule  (ppm_plan_modules — plan_id FK + module_id FK)
       └─ Module
```

The question of where to store this relationship, and what identifiers to use, was settled during PPM-03.

---

## Decision

**The `ppm_plan_modules` join table is the single source of truth for feature ownership.**

Consequences of this decision:

1. **UUID foreign keys only.** The join table stores `plan_id UUID` and `module_id UUID`. Slugs, codes, and names are API-layer identifiers and are never persisted in the join table. Any code that resolves feature ownership must resolve through UUIDs — look up the plan UUID, fetch its `PlanModule` rows, collect the `module_id` values, then resolve `Module` objects.

2. **No denormalization.** The join table carries no redundant plan or module data. If a module's name or code changes, there is nothing to backfill in `ppm_plan_modules`.

3. **Hard deletes on the join table.** `PlanModule` rows are created and deleted — never soft-deleted. A missing row means the assignment does not exist. There is no `deleted_at` column and no `@SQLRestriction` on this entity.

4. **Immutability after creation.** A `PlanModule` row is written once. The only valid mutation is deletion. There is no `updated_at` or `updated_by` on the join table.

5. **Atomic replace is the replace primitive.** When the module set for a plan changes, the operation is `deleteAllByPlanId` + `saveAll` inside a single `@Transactional` boundary. Partial updates are not supported — callers supply the complete desired set.

---

## How PPM-04 Should Consume This

The Entitlement Engine will receive a `planId` and must determine what the tenant holding that plan is entitled to. The correct read path is:

```java
// 1. Resolve the module IDs assigned to the plan
List<PlanModule> mappings = planModuleRepository.findByPlanId(planId);

// 2. Collect module UUIDs
List<UUID> moduleIds = mappings.stream()
    .map(PlanModule::getModuleId)
    .toList();

// 3. Resolve Module objects (batch fetch to avoid N+1)
List<Module> modules = moduleRepository.findAllById(moduleIds);

// 4. Build entitlements from Module fields (code, active, etc.)
```

The `PlanModuleRepositoryPort.findByPlanId(UUID)` method is already available and tested. PPM-04 should inject `PlanModuleRepositoryPort` directly — there is no need to introduce a new port or bypass this layer.

---

## What Is NOT the Source of Truth

| Rejected approach | Reason |
|---|---|
| Storing `module_code` in the join table | Codes are API identifiers, not relational keys. Violates the design invariant; creates a second place to update if a code changes. |
| Resolving entitlements from plan slug/code alone | Slugs and codes are lookup handles, not ownership records. The plan row itself carries no module list — only `ppm_plan_modules` does. |
| Storing the module list as a JSON column on `ppm_plans` | Not queryable per module; violates normalization; makes atomic replace harder to audit. |
| A separate entitlement table that duplicates the join | Premature. The join table is the entitlement record. A separate entitlement store is a PPM-04 concern only if caching or tenant-snapshot semantics require it. |

---

## Consequences

- PPM-04 has a clean, tested read path via `PlanModuleRepositoryPort.findByPlanId`.
- Any future feature asking "which plans include module X?" uses `PlanModuleRepositoryPort.findByModuleId`.
- The N+1 pattern in `getModules` (1 query + N module lookups) is acceptable at current catalog sizes (≤14 modules). If PPM-04 entitlement resolution becomes a hot path, replace the per-ID loop with a single `moduleRepository.findAllById(moduleIds)` batch fetch.
