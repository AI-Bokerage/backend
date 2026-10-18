# Gen_SUP feature-flags module — design

## Context

Third module in the Gen_SUP build order (`dashboard` → `tenants` → **feature-flags** → announcements → analytics → tickets → impersonate).

Original CPMS `sup-svc` feature-flags is a thin CRUD+audit shim over a `feature_flags` table with no resolution logic (global on/off, plan-code allowlist, tenant-UUID allowlist, rollout percent). In the Gen_MS family, `Gen_FMM` is already a fully-built 4-tier resolution engine (tenant override → plan-entitlement → flag default → rollout percentage) with its own Postgres-backed catalog (`Module`, `FeatureFlag`, `PlanModule`) and per-tenant `TenantFeatureFlag` overrides, exposed over a live public HTTP API with no auth of its own (`docs: Gen_FMM/docs/integration-guide.md`).

Gen_SUP's feature-flags module is therefore **not a reimplementation** — it's a thin, `X-Internal-Secret`-gated proxy over Gen_FMM's catalog-flags and overrides endpoints, following the exact same shape as the `tenants` module's proxy over Gen_TNT.

## Scope

In scope:
- List flags, update an existing flag's catalog fields (`moduleCode`, `defaultEnabled`, `isGradualRollout`, `rolloutPercentage`).
- Set a per-tenant override, look up overrides for a tenant, clear an override.
- Audit event publish (RabbitMQ, `platform.audit` exchange) on every flag update and override set/clear.

Out of scope (deferred, no current need):
- Flag create/delete — matches original CPMS behavior (flags are provisioned outside Gen_SUP, e.g. directly against Gen_FMM at service onboarding). Only list + update exposed.
- Plan-module catalog management (`/api/v1/catalog/modules`, `/api/v1/catalog/plan-modules`) — a separate concern (which modules exist and which plans include them), not part of the day-to-day super-admin flag-toggling workflow this module targets.
- Any resolution/entitlement logic — Gen_FMM owns this entirely; Gen_SUP never calls `/api/v1/entitlement*` or `/internal/v1/fmm/*`.

## Architecture

Same hexagonal-port shape as `tenants`:

- `FmmClientPort` (`domain/ports/fmm-client.port.ts`) — interface over the six operations below.
- `HttpFmmClient` (`infra/external/http-fmm-client.ts`) — default implementation, plain `fetch()` against `GEN_FMM_BASE_URL`. **No secret header** — Gen_FMM's `/api/v1/catalog/*` and `/api/v1/overrides*` routes are genuinely unauthenticated per Gen_FMM's own docs ("no auth of its own — front with the host's own authn/authz"); there is nothing to send. `GEN_FMM_BASE_URL` resolved via the existing lazy `requireEnv()` pattern, enforced only when `modules.featureFlags` is enabled and no `fmmClient` override is supplied.
- `EventPublisher` — reuses the existing `RabbitMqBus` / `EventPublisher` port from the `tenants` module unchanged. New routing keys added to `TENANT_ROUTING_KEYS`'s sibling constant (see below).

### `FmmClientPort` methods

```ts
interface FmmClientPort {
  listFlags(): Promise<FmmFlagResponse[]>;
  updateFlag(key: string, patch: FmmFlagPatch): Promise<FmmFlagResponse>;
  setOverride(input: FmmOverrideUpsertParams): Promise<FmmOverrideResponse>;
  getOverride(tenantId: string, flagKey: string): Promise<FmmOverrideResponse | null>;
  listOverridesForTenant(tenantId: string): Promise<FmmOverrideResponse[]>;
  clearOverride(tenantId: string, flagKey: string): Promise<boolean>; // false if none existed
}
```

Maps directly onto Gen_FMM's real endpoints (verified against `Gen_FMM/packages/gen-fmm-starter/src/modules/catalog/v1/controller.ts` and `.../modules/overrides/v1/controller.ts`):

| Port method | Gen_FMM endpoint | Notes |
|---|---|---|
| `listFlags` | `GET /api/v1/catalog/flags` | optional `?moduleCode=` not exposed at the Gen_SUP layer (YAGNI — no current caller needs it) |
| `updateFlag` | `PATCH /api/v1/catalog/flags/:key` | body: `moduleCode?`, `defaultEnabled?`, `isGradualRollout?`, `rolloutPercentage?` (0-100 int) — 404 if flag missing |
| `setOverride` | `POST /api/v1/overrides` | body: `tenantId`, `flagKey`, `enabled`, `config?`, `reason?`, `expiresAt?` — upsert, always 200 |
| `getOverride` | `GET /api/v1/overrides/:tenantId/:flagKey` | 404 if none |
| `listOverridesForTenant` | `GET /api/v1/overrides/:tenantId` | |
| `clearOverride` | `DELETE /api/v1/overrides/:tenantId/:flagKey` | 404 if none — mapped to `false` return, not surfaced as a 502 |

### Gen_SUP routes (`/api/v1/feature-flags`, `X-Internal-Secret` gated)

```
GET    /api/v1/feature-flags
PATCH  /api/v1/feature-flags/:key
PUT    /api/v1/feature-flags/overrides/:tenantId/:flagKey
GET    /api/v1/feature-flags/overrides/:tenantId
DELETE /api/v1/feature-flags/overrides/:tenantId/:flagKey
```

**`PATCH /:key` body**: `{ moduleCode?, defaultEnabled?, isGradualRollout?, rolloutPercentage?, reason }`. `reason` (string, 3-500 chars) is **required**, Gen_SUP-only — Gen_FMM's `updateFlag` schema has no such field, so it is never forwarded; it exists purely for the audit event.

**`PUT /overrides/:tenantId/:flagKey` body**: `{ enabled, config?, expiresAt?, reason }`. `reason` is required at the Gen_SUP layer and, unlike the flag-update reason, **is forwarded** into Gen_FMM's own optional `reason` field on the override row (Gen_FMM persists it) — one required input serving both the Gen_SUP audit event and Gen_FMM's own persisted record.

**`DELETE /overrides/:tenantId/:flagKey`**: no body (matches Gen_FMM's own contract — a DELETE with no request body). No reason captured for clears; the audit event carries just `tenantId`/`flagKey`.

`tenantId` path params validated as `z.string().uuid()` at the schema layer (lesson carried over from the `tenants` module's residual review finding — validate at the edge, not by hoping the client call fails cleanly).

## Error handling

New error classes in `common/errors.ts`, same shape as existing ones:
- `FlagNotFoundError` (404) — flag key doesn't exist in Gen_FMM
- `OverrideNotFoundError` (404) — no override exists for that tenant/flag pair (surfaced on `getOverride`/`clearOverride` only when the caller needs a 404, not on `clearOverride`'s idempotent-success path — see below)
- `FmmClientError` (502) — any other non-2xx or transport failure from `HttpFmmClient`

`clearOverride` returning `false` (Gen_FMM 404) is **not** surfaced as `OverrideNotFoundError` by the Gen_SUP service — DELETE is treated as idempotent (clearing a non-existent override is a no-op success, `204`), consistent with typical REST DELETE semantics and avoiding a spurious client-facing error for what's usually just a repeated/racing clear call.

A shared `mapFmmError(err, context)` helper is used by all service methods that call the client (explicit lesson from the tenants module's residual review round — gate 404/409-style mappings on which operation ran, not merely on which context fields happen to be populated, to avoid a misconfigured `listFlags()` 404 being misreported as `FlagNotFoundError` or similar cross-operation leakage).

`HttpFmmClient` follows the `HttpTntClient` conventions exactly: `FmmHttpError extends Error` (carries `.status`), response bodies logged via the shared logger (not embedded in thrown messages, and read via a `safeBody()`-style helper so a body-read failure can't itself escape unmapped — both lessons carried forward from the tenants module's two review rounds).

## Audit events

New routing keys added alongside the existing `TENANT_ROUTING_KEYS` (same `platform.audit` exchange, same `RabbitMqBus`, same fire-and-forget `publishSafely` pattern — failures logged, never fail the request):

```ts
export const FLAG_ROUTING_KEYS = {
  UPDATED: "sup.flag.updated",
  OVERRIDE_SET: "sup.override.set",
  OVERRIDE_CLEARED: "sup.override.cleared",
} as const;
```

Envelope `data` payload: for `UPDATED` — the patch fields applied plus `reason`; for `OVERRIDE_SET` — `tenantId`, `flagKey`, `enabled`, `reason`; for `OVERRIDE_CLEARED` — `tenantId`, `flagKey`.

## `createGenSup` wiring

- `GenSupModulesConfig.featureFlags?: boolean` (default `true`, same pattern as `dashboard`/`tenants`).
- `GenSupConfig.fmmClient?: FmmClientPort`, `fmmBaseUrl?: string` (override for `GEN_FMM_BASE_URL`).
- `resolveFmmClient()` helper mirrors `resolveTntClient()` exactly.
- Mounts `/api/v1/feature-flags` when enabled.
- Existing `.env.example`, `create-gen-sup.test.ts`'s pre-existing tests get `modules: { featureFlags: false }` added wherever they already needed `tenants: false` (same regression class the tenants module hit twice — must audit every pre-existing test in that file, not just the newest one).

## Known limitation to document

Gen_FMM's `/api/v1/catalog/*` and `/api/v1/overrides*` endpoints have no authentication of their own (Gen_FMM's own documented limitation — "the host fronts every route with its own auth"). Gen_SUP's `X-Internal-Secret` gate protects requests that go through Gen_SUP, but does not and cannot prevent direct network access to Gen_FMM itself bypassing Gen_SUP entirely. This is not fixable from Gen_SUP's side — it is Gen_FMM's deployment-topology responsibility (network isolation / its own future auth layer). Documented in the integration guide's Known limitations section, same as the `tenants` module documents Gen_TNT's own gaps.

## Testing

Same TDD/Vitest conventions as `tenants`: `http-fmm-client.test.ts` (mocked `fetch` via `vi.stubGlobal`) covering all 6 methods' success/404/5xx paths; `service.test.ts` (mock `FmmClientPort` + `EventPublisher`) covering the operation-gated `mapFmmError` behavior explicitly (a 404 on `listFlags` must not be misreported, mirroring the exact regression class fixed in the tenants module's residual review round); `create-gen-sup.test.ts` additions for the new module toggle and one route smoke-test.
