# fin-document — Stage 3 Design: Brand Profile

Date: 2026-08-03
Status: Approved for planning
Part of: Phase 12C — Document Engine Completion (MVP for FIN-SVC), Stage 3 of 6 (Stages 3-8)

## Context

Stages 1-2 (Foundation, Template + Placeholder Engine) are complete, reviewed, and frozen — this and every following stage is purely additive and must not modify any existing public signature. Phase 12C targets the minimum set of enterprise document capabilities CPMS FIN-SVC needs, explicitly deferring themes/typography/white-label/multi-brand-variants to a documented future roadmap (Stage 8's `DOCUMENT.md`).

Stage 3 delivers **Brand Profile** (Phase 12C Group 1): a generic, provider-agnostic representation of "who this document is from" — company name, logo, address, contact info, tax/business registration numbers. The SDK never knows where a `BrandProfile` comes from (database, config file, hardcoded) — applications register instances directly, mirroring how `DocumentTemplate` registration works in Stage 2. No SPI is needed here because, unlike Letterhead (Stage 4) which needs to fetch bytes from arbitrary storage, a `BrandProfile` is a small, fully-in-memory value object the application constructs and hands over directly.

## Where This Plugs Into the Pipeline

Brand Profile does not yet plug into `DocumentComposer` in this stage — that wiring (a `BrandDocumentComposer` or an extension of `TemplateDocumentComposer`'s placeholder resolution to expose brand fields as placeholders) is deliberately deferred to Stage 6 (PDF Renderer), where brand data first has an actual visual consumer. Stage 3 only builds the value model, registry, and resolver — the same "build the type, prove it resolves" pattern Stage 1 used for renderers before anything rendered PDF.

```
BrandProfile (registered by application)
     │
     ▼
BrandRegistry (internal) — register(BrandProfile) / lookup(BrandId)
     │
     ▼
BrandResolver (port) — resolve(BrandId): BrandProfile
```

`BrandId` already exists (Stage 1 identity). No new identity type needed.

## Package Layout

```
io.genfin.document
├── api
│   └── brand           BrandProfile, BrandIdentity, BrandAssets, BrandLogo, BrandHeader, BrandFooter,
│                        BrandContactInformation, BrandRegistrationInformation
├── port
│   └── BrandResolver
└── internal
    └── brand
        ├── DefaultBrandRegistry
        └── DefaultBrandResolver
```

## Core Types

**`BrandLogo`** (api.brand) — `byte[] imageBytes`, `String mimeType` (e.g. `"image/png"`). Defensive-copies the byte array on construction and on `imageBytes()` return, per the exact lesson from Stage 1's `RenderResult` (a Stage 1 finding caught a test that didn't verify this — this stage writes the mutation-based test from day one, not after review).

**`BrandContactInformation`** (api.brand) — `String phone`, `String email`, `String website`. All optional (empty string, not null, when absent — consistent with `KeyValueElement`'s null-becomes-empty convention); no validation beyond non-null (blank is a valid "not provided" state, distinct from Placeholder's key-must-not-be-blank rule since a phone number is data, not an identifier).

**`BrandRegistrationInformation`** (api.brand) — `String taxRegistrationNumber`, `String businessRegistrationNumber`. Same optional/empty-not-null convention as contact information.

**`BrandIdentity`** (api.brand) — `String companyName` (required, non-blank — this is the one field a brand cannot meaningfully lack), `String addressLine` (single-string address, kept simple per the MVP scope — multi-line/structured address is future-roadmap territory, not this stage's job).

**`BrandHeader`** / **`BrandFooter`** (api.brand) — each wraps a single `String text` (e.g. a header tagline, a footer legal line). Kept minimal and symmetric; richer header/footer composition (multiple blocks, conditional content) is Stage 6/future-roadmap territory once there's a renderer that actually lays them out.

**`BrandAssets`** (api.brand) — bundles `BrandLogo logo` (nullable — not every brand has a logo yet) with `Optional<BrandLogo> logo()` accessor.

**`BrandProfile`** (api.brand) — the aggregate: `BrandId id`, `BrandIdentity identity`, `BrandAssets assets`, `BrandContactInformation contact`, `BrandRegistrationInformation registration`, `BrandHeader header`, `BrandFooter footer`. Built via `BrandProfile.builder(BrandId id, BrandIdentity identity)` (Group 10's builder requirement pulled forward for this one type since a 7-field constructor is exactly the case builders exist for — every other Stage 3 type stays a plain `of(...)` factory since none of them has more than 3 fields). Builder methods: `.assets(BrandAssets)`, `.contact(BrandContactInformation)`, `.registration(BrandRegistrationInformation)`, `.header(BrandHeader)`, `.footer(BrandFooter)`, `.build(): BrandProfile`. Unset optional fields default to empty-valued instances (`BrandContactInformation.of("", "", "")`, etc.) — a `BrandProfile` is always fully constructed, never partially null, so downstream consumers (Stage 6's renderer) never null-check.

**`BrandResolver`** (port) — `resolve(BrandId): BrandProfile`, mirrors `RendererResolver`/`TemplateResolver` exactly. `DefaultBrandRegistry`/`DefaultBrandResolver` (internal.brand) follow the identical register/lookup/resolve-throws-on-miss shape as Stage 2's `DefaultTemplateRegistry`/`DefaultTemplateResolver`, minus caching (a `BrandProfile` isn't compiled from anything, so there's nothing to cache — `resolve` is a direct map lookup, throwing `BrandNotFoundException` — new sibling to `TemplateNotFoundException`/`RendererNotFoundException` in `api.exception` — on miss).

A `BrandResolvers.standard()` factory (port), mirroring `TemplateEngines.standard()`'s "registration handle + facade in one call" shape, is included in this stage rather than deferred — Stage 2's final review found that exact gap (no consumer-reachable factory) after the fact, and it is cheaper to build it in from the start here.

## Error Handling

`BrandProfile.builder(...)` validates `id` and `identity.companyName` are non-null/non-blank at `build()` time via `ValidationException` (reusing `io.genfin.api.validation.Validate`, per the Stage 1 final-review lesson — never hand-roll a blank/null check this module already has a shared helper for). `BrandNotFoundException` (unchecked, `api.exception`) on resolving an unregistered `BrandId` — a genuine caller error, not an expected outcome, matching `TemplateNotFoundException`'s precedent.

## Testing

- Value-object tests: `BrandLogo`'s defensive copy (mutation-based, not equality-based — the exact test shape a Stage 1 review had to add after the fact), `BrandProfile.builder()` producing a fully-defaulted profile when only required fields are set, validation failures on missing `id`/`companyName`.
- `DefaultBrandRegistry`/`DefaultBrandResolver`: resolve-after-register, `BrandNotFoundException` on miss, duplicate-registration behavior (decide and test one way — either reject like `DefaultRendererRegistry` or allow overwrite; **decision: reject via `IllegalStateException`**, matching the renderer registry's precedent, since silent overwrite of brand identity data is a worse failure mode than a loud rejection).
- `BrandResolvers.standard()` integration test: register, resolve, confirm the same `BrandProfile` instance-equal round-trips.
- Architecture test: no new packages need a new ArchUnit exception unless `BrandResolvers` (a port-level factory reaching into `internal.brand`) creates the same kind of cycle Stage 2's `TemplateEngines`/`PlaceholderResolvers` did — if so, add one more entry to the existing named-factory-class regex pattern already established in `DocumentArchitectureTest.java`, never a package move (this module's now-five-times-learned lesson).

## Out of Scope for Stage 3

Themes, typography, white-label, color systems, brand variants (per Phase 12C's explicit exclusions — documented in Stage 8's Future Roadmap, not built). Multi-line/structured addresses. Any actual rendering of brand data (Stage 6). Wiring `BrandProfile` into `DocumentComposer`/placeholder resolution (Stage 6, once there's a PDF renderer that consumes it — building that wiring now with no consumer would be speculative).
