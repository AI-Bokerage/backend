# Gen_TBR — Tenant Branding & Custom Domains — Design

## Goal

Build Gen_TBR: the fifth and final Gen_MS library, owning a tenant's
white-label visual identity (branding) and custom-domain ownership
verification. TypeScript/Express/Prisma, deliberately the same stack as
Gen_REG so the two Node-side siblings feel like one product.

## Why

Gen_TNT's own design spec explicitly carved this scope out of itself:

> **Gen_TBR** (separate, later spec) — branding/custom-domain.
> Node/Express/Prisma, at `Gen_MS/Gen_TBR`. Not covered here.

and, explaining why `tnt-svc`'s legacy `CustomDomain` model was dropped
during Gen_TNT's own genericization:

> `tnt-svc`'s own `CustomDomain` model — that's `Gen_TBR`'s job, not
> duplicated here.

(`Gen_TNT/docs/superpowers/specs/2026-07-20-gen-tnt-provisioning-design.md`,
lines 23 and 37.) Gen_TBR is that deferred piece: Gen_TNT provisions a
tenant; Gen_TBR brands it and lets it claim a custom domain. The two
services are independent — Gen_TBR never imports Gen_TNT as a code
dependency (see Cross-Library Integration below).

Of the five Gen_MS libraries, this is the only one starting from a blank
directory rather than a `pre-context/` CPMS source — there is no legacy
`tbr-svc` reference to port from, only the boundary Gen_TNT's spec drew
around where its own scope stops.

## Scope

**In scope (v1):**
- **Branding** — one row per tenant (`TenantBranding`): logo/dark-logo/
  favicon URLs, primary/secondary/accent colors, font family, theme
  (`SYSTEM`/`LIGHT`/`DARK`), display name, tagline, an escape-hatch
  `rawMeta` JSON field. Exposed via a **public, unauthenticated manifest**
  endpoint a host's front-end fetches to render a branded UI before any
  user is logged in.
- **Custom domains** — many rows per tenant (`TenantDomain`): claim a
  domain, verify ownership via a DNS TXT or CNAME challenge, track
  `PENDING → VERIFIED → ACTIVE → DETACHED` lifecycle, designate one
  `isPrimary` domain per tenant.
- Both ship as independently toggleable modules (`modules.branding`,
  `modules.domains`), sharing one factory and one Postgres database.
- Asset upload (logo/favicon) via a swappable `IAssetStore` port, default
  S3-compatible implementation. Gen_TBR stores only the resulting URL,
  never the bytes.
- DNS verification via a swappable `IDnsVerifier` port, default
  implementation wrapping `node:dns/promises`.
- An optional `ITntClient` port (mirroring Gen_REG's own `ITntClient`) for
  a **future** "tenant suspended → detach domains" webhook — not required
  or exercised by any v1 feature.

**Out of scope (v1 — each with its own rationale):**
- **SSL/TLS provisioning.** Gen_TBR verifies domain ownership only. It
  never runs ACME/Let's Encrypt, never calls AWS ACM/Cloudflare, never
  accepts or stores an uploaded certificate or private key. Rationale:
  this keeps Gen_TBR a pure state/config library — no cert-issuance
  worker, no key storage, no renewal scheduler. The host's edge layer
  (Caddy, Cloudflare, nginx, a CDN) handles TLS once Gen_TBR marks a
  domain `ACTIVE`. If ACME is wanted later, that's a Phase 2 sub-project
  adding a worker + an `ICertIssuer` port — a new design question, not an
  assumption baked in now.
- **Tenant-existence validation against Gen_TNT.** Gen_TBR trusts the
  caller's `tenantId` outright (validated only as a UUID shape). A
  tenant is created once by Gen_TNT (driven by Gen_REG) and branded
  later; adding a synchronous Gen_TBR→Gen_TNT lookup on every write would
  couple the two services tightly for no real safety win, since the
  caller is already an authenticated internal/admin API that knows the
  tenant is real. Authorization — whether *this caller* may act on *this
  tenant* — is the host's job (typically via Gen_ADM's
  `PermissionChecker`), not Gen_TBR's.
- **Redis/Valkey, or any background worker, in v1.** DNS verification is
  on-demand only (`POST /domains/:id/verify` triggers one lookup).
  Gen_REG needs Redis for webhook dedup; Gen_TBR has no webhook to dedup,
  so adding the dependency would be pure overhead. A periodic
  re-verification scheduler is a natural Phase 2 (and would want a
  distributed lock at that point) but nothing in v1 needs it.
- **IDN/punycode domain support.** ASCII hostnames only in v1; noted as a
  known limitation in `integration-guide.md`, not silently unsupported.
- **Edge routing / reverse-proxy configuration.** Gen_TBR reports which
  domains are `ACTIVE`; routing traffic to the right tenant is entirely
  the host's edge layer's job.

## Architecture

### Cross-library integration (the design crux)

Gen_TBR is the least coupled of the five Gen_MS libraries — it imports no
sibling as an npm/code dependency. All cross-library interaction is
HTTP-or-port:

- **Identity**: the caller passes `tenantId` (a UUID) on every request;
  Gen_TBR treats it as authoritative and performs no existence check.
  Documented loudly in `integration-guide.md`: *"Gen_TBR performs no
  tenant-existence check. The host must ensure tenantId is valid and the
  caller is authorized."*
- **`ITntClient`**: included as a port + default `HttpTntClient` adapter
  (mirroring Gen_REG's own `ITntClient`/`resolveTntClient` shape), but not
  required by any v1 feature. If `config.tntClient` is unset and no
  `GEN_TNT_BASE_URL` env var is present, the resolved client is
  `undefined` — this is only a problem for a feature that explicitly
  needs it, and no v1 feature does.
- **Auth**: Gen_TBR ships an `internalSecret` middleware (checks
  `X-Internal-Secret` against `GEN_TBR_INTERNAL_SECRET`) for its demo and
  for `/internal` routes, but does not itself enforce authorization on
  `/api/v1` routes — the host mounts its own auth middleware in front. The
  one deliberate exception is the public manifest endpoint, which has no
  auth at all by design (see below).

### The factory pattern

Consumed via one function, `createGenTbr(config: GenTbrConfig):
GenTbrInstance`, mirroring `createGenReg` exactly — no Spring-Boot-style
autoconfiguration (there is none in Node; everything is explicit at
construction time).

```ts
export interface GenTbrConfig {
  brandingRepo?: ITenantBrandingRepo;
  domainRepo?: ITenantDomainRepo;
  assetStore?: IAssetStore;
  dnsVerifier?: IDnsVerifier;
  tntClient?: ITntClient;
  modules?: GenTbrModulesConfig;
  internalSecret?: string;
}

export interface GenTbrModulesConfig {
  branding?: boolean;   // default true
  domains?: boolean;    // default true
  manifest?: boolean;   // default true
  assets?: boolean;     // default true
}

export interface GenTbrInstance {
  app: Express;
}
```

Each config field gets a module-level `resolveXxx(override)` function
(copied idiom from `create-gen-reg.ts`): if the caller supplies an
override, use it as-is; otherwise build the default adapter from env vars
**lazily**, via `requireEnv`, which throws `GenTbrConfigError`
synchronously at `createGenTbr()` call time — never as a mid-request 500.
Module-specific env vars are read only by the resolver of a module that
is actually enabled, never eagerly validated for a disabled module.

Unlike Gen_REG, `GenTbrInstance` has no `worker` field — there is no
background worker in v1 (see Scope).

### Domain model

**`TenantBranding`** (table `tenant_branding`) — one row per tenant,
`tenantId` itself is the primary key (a tenant has exactly one brand; no
separate unique index needed beyond the PK). Fields: `displayName`
(required), `tagline`, `logoUrl`, `logoDarkUrl`, `faviconUrl`,
`primaryColor`/`secondaryColor`/`accentColor` (hex, validated
`^#[0-9a-fA-F]{6}$`), `fontFamily`, `theme` (enum `SYSTEM`/`LIGHT`/`DARK`,
default `SYSTEM`), `rawMeta` (JSON escape hatch), `updatedAt` only — no
`createdAt`, since branding has no meaningful creation-vs-mutation
distinction (it's an upsert-on-first-write record, not an event log).

**`TenantDomain`** (table `tenant_domain`) — many rows per tenant. Fields:
`id` (UUID PK), `tenantId` (indexed), `domain` (lowercased, trimmed,
globally unique — `@@unique([domain])`, so two tenants can never claim
the same domain), `verificationToken` (64-char hex, `crypto.randomBytes(32)`),
`status` (enum `PENDING`/`VERIFIED`/`ACTIVE`/`DETACHED`),
`verificationMethod` (enum `TXT`/`CNAME`), `verifiedAt`, `lastCheckedAt`,
`isPrimary` (boolean, default false, at most one true per tenant —
enforced in the service layer inside a transaction, backstopped by a
partial unique index added via a raw migration since Prisma cannot
express partial uniques), `createdAt`/`updatedAt`.

State machine:

```
            create              verify (DNS match)
   ─────────────────► PENDING ──────────────────► VERIFIED ──► ACTIVE
                        │                                          │
                        │ re-verify fails / owner detaches         │ set as primary
                        ▼                                          ▼
                    DETACHED  ◄────────────────────────────── (revoke primary)
```

`PENDING` — created, token issued, not yet verified. `VERIFIED` — a DNS
lookup matched the token at least once. `ACTIVE` — verified and
designated usable by the host (the default state a domain moves to
immediately after a successful verify in v1 — `isPrimary` is a separate,
orthogonal flag on top of `ACTIVE`). `DETACHED` — soft-deleted or
owner-revoked; kept for history, excluded from default list queries.

A single failed `verify` call is not treated as a permanent failure —
real DNS propagation takes minutes, so verification returns `409
DomainVerificationFailedError` (retryable) and records `lastCheckedAt`,
never a hard delete or automatic `DETACHED`.

### API surface (v1)

Base path `/api/v1`, internal path `/internal`. Every mutating endpoint
except the public manifest requires either an `X-Internal-Secret` header
or in-process invocation (the factory itself enforces no auth — the host
mounts its own).

**Branding:** `GET /branding/:tenantId`, `PUT /branding/:tenantId`
(upsert), `PATCH /branding/:tenantId` (partial update), `GET
/branding/:tenantId/manifest` — **public, unauthenticated**, the one
surface with no auth requirement at all, since a public site must show
its brand before any user is logged in. Resolvable by `tenantId` directly
or by `?domain=` (looked up against `tenant_domain` where `status IN
(VERIFIED, ACTIVE)`). Returns `404 BrandingNotFoundError` if nothing
matches — Gen_TBR never falls back to a default brand; that decision
belongs to the host.

**Assets** (if `modules.assets`): `POST /branding/:tenantId/assets`
(multipart upload, stores via `IAssetStore`, returns the URL and
optionally writes it into `logoUrl`/`faviconUrl`), `GET
/branding/:tenantId/assets/:assetId`.

**Domains:** `POST /domains` (create, `PENDING`, issues token + the DNS
record to add), `GET /domains?tenantId=` (list, excludes `DETACHED` by
default), `POST /domains/:id/verify` (DNS lookup via `IDnsVerifier`,
`PENDING→VERIFIED` on match else `409`), `POST /domains/:id/activate`
(`VERIFIED→ACTIVE`), `POST /domains/:id/detach` (`→DETACHED`), `POST
/domains/:id/primary` (set primary, revoking the prior one). **All four
mutating routes (`verify`/`activate`/`detach`/`primary`) require
`{tenantId}` in the request body**, checked against the domain record's
own `tenantId` before any status transition — an unowned or mismatched
`tenantId` is a `404 DomainNotFoundError`, not a silent cross-tenant
mutation. *(2026-07-27 addendum: `primary` had this from the start; a
final-review finding showed `verify`/`activate`/`detach` took only the
path `id` with no tenant check, so the same ownership guard was added to
all three.)*

**Health:** `GET /health` → `{ status: "ok" }`, unauthenticated.

### Error taxonomy

`AppError` base class (`statusCode`, `code`, `message`), copied verbatim
from Gen_REG's shape:

```ts
export class AppError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = new.target.name;
  }
}
```

Subclasses: `BrandingNotFoundError` (404), `BrandingValidationError` (400
— bad hex color, oversized tagline), `DomainNotFoundError` (404),
`DomainAlreadyClaimedError` (409 — the `@@unique([domain])` collision
surfaced cleanly), `DomainVerificationFailedError` (409),
`InvalidDomainStatusTransitionError` (409 — e.g. `DETACHED→ACTIVE`
directly), `AssetStoreError` (500/502), `AssetNotFoundError` (404). One
exception to the `AppError` hierarchy, matching Gen_REG's own
`GenRegConfigError`: `GenTbrConfigError` is a plain `Error` subclass
(no HTTP status — it's thrown at `createGenTbr()` boot time, never
reaches the request-level `errorHandler`).

Every error response shape: `{ error: code, message: humanText }`.

## Data Flow & Error Handling

**Branding write → manifest read**: `PUT /branding/:tenantId` upserts the
row (Prisma `upsert` keyed on the `tenantId` PK) → `GET
/branding/:tenantId/manifest` reads it back directly. No caching layer in
v1 (no Redis) — every manifest fetch is a live DB read; acceptable given
Postgres's own read performance and the lack of a webhook/dedup need that
would otherwise justify Redis.

**Domain claim → verify → activate**: `POST /domains` inserts a `PENDING`
row and returns the DNS record instructions. The tenant configures DNS
out-of-band (this can take minutes to propagate — outside Gen_TBR's
control). `POST /domains/:id/verify` calls `IDnsVerifier.resolveTxt` or
`.resolveCname` against the live DNS system resolver; a match flips
`PENDING→VERIFIED` and stamps `verifiedAt`+`lastCheckedAt`; a non-match
stamps only `lastCheckedAt` and returns `409` for the tenant to retry
later, not a hard failure. `POST /domains/:id/activate` is a separate,
explicit step (`VERIFIED→ACTIVE`) rather than automatic, so a host can
gate "verified" from "actually serving traffic" if it wants a review step
in between.

**Primary domain invariant**: `POST /domains/:id/primary` runs inside one
Prisma transaction — clear the tenant's current primary (if any), then
set the new one — so the invariant (at most one `isPrimary=true` per
tenant) is never observably violated mid-request. The database's partial
unique index is a backstop against a bug in that transaction, not the
primary enforcement mechanism (Prisma cannot express a partial unique in
its schema DSL, so this index is added via a raw SQL migration).

**Asset upload failure**: `IAssetStore.put(...)` failures (S3 unreachable,
quota, etc.) surface as `AssetStoreError` (500/502) — the branding row is
never partially updated; the `logoUrl`/`faviconUrl` write only happens
after the store confirms the object was written and returns its URL.

## Testing

Mirrors Gen_REG's tiering exactly:
- **Unit tests** (Vitest, no DB) — `BrandingService`/`DomainService`
  against mocked ports: color-hex validation, domain normalization
  (lowercase/trim/length), every status-transition guard (including the
  409 cases), the primary-domain invariant logic in isolation.
- **Repository integration tests** (real Postgres via
  `@testcontainers/postgresql`, migrations applied via `prisma migrate
  deploy` against the ephemeral container — same idiom as Gen_REG's
  `tests/support/postgres-container.ts`) — upsert branding, claim/verify a
  domain, the global `@@unique([domain])` collision surfacing as
  `DomainAlreadyClaimedError`, the partial-unique primary index.
- **DNS verifier tests** — against an injected fake `IDnsVerifier`
  (match / no-match / partial-match / timeout-shaped failures). Never hit
  real DNS in tests.
- **HTTP tests** (`supertest` against the built `app`) — each endpoint's
  happy path, the manifest's genuine lack of auth, the internal-secret
  gate on mutating routes.
- **Factory/resolver tests** — `createGenTbr({})` boots cleanly with all
  env set; throws `GenTbrConfigError` listing every missing var with no
  env; a disabled module's routes genuinely 404 rather than merely being
  undocumented.
- **Smoke script** (`scripts/smoke-branding.sh`) — create brand → upload a
  fake asset → claim a domain → mock-verify → fetch the manifest, against
  the demo app end-to-end.

## Delivery Approach

Same subagent-driven-development cadence used for every Gen_ADM feature
this session: this spec → an implementation plan broken into small,
independently-reviewable tasks → dispatch one fresh implementer subagent
per task → task-level spec-compliance + quality review → fix loop as
needed → a final whole-branch review once all tasks land → push straight
to `main` after explicit confirmation (Gen_TBR has no CPMS
`pre-context/` reference, so unlike Gen_ADM's ports there is no source
audit step — the plan is written directly from this spec and from reading
Gen_REG's actual shipped code as the pattern template).

Build order, bottom-up (per `SYSTEM_PROMPT.md` §15): repo scaffolding
(workspace `package.json`, both sub-packages, `tsconfig.json`/
`vitest.config.ts` copied from Gen_REG, `docker-compose.yml` on port
5437, `.gitignore`, `.env.example`) → Prisma schema + migrations → ports →
default adapters (`PrismaTenantBrandingRepo`, `PrismaTenantDomainRepo`,
`S3AssetStore`, `NodeDnsVerifier`, `HttpTntClient`) → services
(`BrandingService`, `DomainService`) → controllers/routers → the
`createGenTbr` factory → the demo app → `integration-guide.md` +
`README.md` + the smoke script.

## Assumptions (confirmed with the user 2026-07-25 — proceed as specified)

1. SSL/TLS is entirely the host's responsibility; Gen_TBR never issues,
   stores, or renews certificates.
2. Branding and custom domains both ship in v1, as two independently
   toggleable modules sharing one factory and one database.
3. Asset storage is behind an `IAssetStore` port with an S3-compatible
   default; Gen_TBR stores URLs only, never bytes, in Postgres.
4. No tenant-existence check against Gen_TNT — the caller's `tenantId` is
   trusted; authorization is the host's job.
5. No Redis/Valkey and no background worker in v1 — DNS verification is
   on-demand only.
6. No IDN/punycode support in v1 — ASCII hostnames only, documented as a
   known limitation.
