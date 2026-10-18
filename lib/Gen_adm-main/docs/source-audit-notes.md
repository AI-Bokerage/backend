# adm-svc → Gen_ADM — Source Audit Notes

Extracted from the CPMS-Platform-wide reuse audit in `PMP CANADA/PMP_Service_Reuse_Decision_Matrix.md`,
`PMP_Service_Genericization_Categories.md`, and `PMP_Service_Genericization_Tradeoffs.md`
(audit date 2026-07-14, direct code inspection, read-only). Kept here so Gen_ADM's own
brainstorming/planning doesn't depend on the PMP CANADA repo being present.

## What adm-svc is

Java/Spring Boot 4, Postgres, Redis. PMP module: USER (RBAC + offboarding).

## Decision matrix verdict

**PORT LOGIC ONLY** — ~45-55% effort vs. clean rebuild.

**Key extraction targets:**
- `OffboardingSagaOrchestrator` + `StepExecutor` — job/step tracking, outbox-after-commit, linear-backoff retry
- `RoleEntity` / `PermissionEntity` / `UserRoleEntity` schema
- `RoleSeeder` default-role list
- Permission-check precedence rules (SUPER_ADMIN bypass, impersonation audit)

**Blockers / risks — read before porting:**
- Offboarding is a **retry saga, not a compensating saga** — no rollback logic exists, don't assume it.
- Session-revocation-on-offboarding is fully coded but **never wired/called** — verify before trusting it's exercised; this is a real gap in the source, not a design choice.
- No RLS anywhere in adm-svc.
- RBAC enforcement via cross-service Redis pub/sub cache is over-engineered for a single-DB monolith/library context — drop it, resolve permissions from the JWT roles array directly (or from local DB) instead.

## Genericization category

**Category B — partially usable** (real generic pattern tangled with project-specific business logic).

- **Reusable slice:** RBAC engine — role/permission/user-role schema, permission-check middleware pattern, default-role seeding.
- **Non-portable part:** the offboarding saga's actual steps make CPMS-specific integration calls (HTTP to PMT-SVC for task reassignment, SDS-SVC for ticket reassignment) — these don't travel.
- **TAT for the reusable slice:** ~1 week to extract just the RBAC piece. The offboarding *shape* (job+step tracking, linear-backoff retry) is a decent pattern to reference, but its steps don't travel as-is.

## Recommended first step (from the matrix's per-module recommendations)

> **USER**: port `adm-svc`'s offboarding saga (note: retry-only, no compensation) and RBAC schema; drop the Redis permission-cache indirection.

## Cross-cutting risks noted in the full audit (apply if relevant)

- RLS is inconsistently applied across the whole CPMS inventory — adm-svc has none; don't assume tenant isolation exists anywhere in the ported logic without adding it.
- Service names are unreliable guides to contents elsewhere in the inventory (not an issue for adm-svc itself, noted for general caution when cross-referencing other services).

## Where the original source lives (copied read-only for reference)

`pre-context/adm-svc/` in this repo — full copy of CPMS-Platform's `apps/adm-svc`, build artifacts stripped.
Treat as reference only; do not build or run it as part of Gen_ADM's own test suite.
