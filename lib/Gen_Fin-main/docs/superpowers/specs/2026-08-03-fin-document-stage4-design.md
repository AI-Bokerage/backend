# fin-document — Stage 4 Design: Letterhead Engine + QR Code Support

Date: 2026-08-03
Status: Approved for planning
Part of: Phase 12C — Document Engine Completion (MVP for FIN-SVC), Stage 4 of 6 (Stages 3-8)

## Context

Stages 1-3 (Foundation, Template + Placeholder Engine, Brand Profile) are complete, reviewed, and frozen. Stage 4 delivers Phase 12C Groups 2 and 5: **Letterhead Engine** (mandatory) and **QR Code Support**. Both share the same architectural shape — "an application-supplied provider hands the SDK a resource (bytes, or arbitrary renderable content); the SDK never knows or cares how that resource was obtained" — so they're built together in one stage.

**Letterhead**: the SDK must never touch a filesystem, S3, blob storage, or database. Applications implement `LetterheadProvider`; the SDK only receives bytes + a declared format and resolves *which* letterhead applies (by brand, region, language, document type — resolution policy, not storage).

**QR Code**: the SDK never generates finance-specific QR content (payment links, invoice URLs). Applications implement `QrCodeProvider`, supplying already-encoded QR image bytes (or, per the recommendation accepted at Phase 12B kickoff, structured `QrCodeContent` the SDK can hand to a renderer later). The SDK only positions/renders what it's given.

Neither Letterhead nor QR is wired into `DocumentComposer` or an actual renderer in this stage — that visual consumption happens in Stage 6 (PDF Renderer), the first stage with a renderer capable of drawing an image or overlaying a PDF page. Stage 4, like Stage 3, builds the value model + provider SPI + resolution registry and proves it resolves correctly — the "build the type, prove it resolves" pattern established since Stage 1.

## Where This Plugs Into the Pipeline

```
Application's LetterheadProvider / QrCodeProvider (implements SPI)
     │
     ▼
LetterheadRegistry / QrCodeRegistry (internal) — register(provider) keyed by resolution criteria
     │
     ▼
LetterheadResolver / QrCodeResolver (port) — resolve(...): Letterhead / QrCodeContent
```

Actual overlay onto a PDF page, and actual QR image rendering/positioning, is Stage 6's job — this stage stops at "the SDK can resolve the correct letterhead/QR content for a given request," which is the prerequisite for Stage 6's renderer to consume it.

## Package Layout

```
io.genfin.document
├── api
│   ├── letterhead      Letterhead, LetterheadFormat, LetterheadPlacement, LetterheadOverlayPolicy
│   └── qr               QrCodeContent, QrCodePlacement
├── port
│   ├── LetterheadProvider   (SPI — extends Extension)
│   ├── LetterheadResolver
│   ├── QrCodeProvider       (SPI — extends Extension)
│   └── QrCodeResolver
└── internal
    ├── letterhead
    │   ├── DefaultLetterheadRegistry
    │   └── DefaultLetterheadResolver
    └── qr
        ├── DefaultQrCodeRegistry
        └── DefaultQrCodeResolver
```

## Core Types — Letterhead

**`LetterheadFormat`** (api.letterhead) — an extensible open-value type (same `interface` + `record`-with-constants pattern as Stage 1's `StandardDocumentType`), not a Java `enum`, so applications can add formats this module hasn't anticipated. `StandardLetterheadFormat` constants: `PDF`, `PNG`, `JPEG`, `SVG`, `BLANK`. `BLANK` is a real, first-class format (not a null/absent case) — per the brief's explicit "Blank Letterhead" requirement, a document can declare it deliberately has no letterhead image, distinct from "letterhead resolution failed."

**`Letterhead`** (api.letterhead) — `LetterheadId id`, `LetterheadFormat format`, `byte[] content` (empty array for `BLANK`), `String mimeType`. Same defensive-copy contract as Stage 3's `BrandLogo` (construction + return), same mutation-based test requirement from day one. `LetterheadId` already exists (Stage 1 identity) — reuse, don't redeclare.

**`LetterheadPlacement`** (api.letterhead) — where on the page the letterhead sits. MVP scope: a single fixed value `FULL_PAGE` is sufficient for Stage 4 (a letterhead is a full-page background image/PDF page), but modeled as an open-value type (same pattern as `LetterheadFormat`) rather than hardcoded, so Stage 6 or later can add `HEADER_ONLY`/`WATERMARK_LAYER`-style placements without an API break.

**`LetterheadOverlayPolicy`** (api.letterhead) — describes *how* Stage 6's PDF renderer should later combine generated content with the letterhead: `StandardLetterheadOverlayPolicy` constants `CONTENT_ON_TOP` (letterhead is the background, generated content draws over it — the only policy actually needed for the "render onto an uploaded PDF letterhead" requirement) and `LETTERHEAD_ON_TOP` (rare, but keeping the type open rather than a boolean avoids a future breaking change). This type carries no rendering logic in Stage 4 — it's a declarative value Stage 6 will branch on.

**`LetterheadProvider`** (port, SPI, extends `Extension`) — `boolean supports(LetterheadId id)`; `Letterhead resolve(LetterheadId id)`. Deliberately mirrors `PlaceholderProvider`'s shape (`supports`/`resolve` pair) rather than `DocumentTemplate`'s direct-registration shape, because a real letterhead provider is expected to be backed by an external system (the SDK "must be designed so future providers can fetch letterheads from anywhere") — a provider that lazily fetches on `resolve` is a more honest model of that requirement than a provider that must pre-register every letterhead upfront the way `DocumentTemplate` does.

**`LetterheadResolver`** (port) — `resolve(LetterheadId): Letterhead`. `DefaultLetterheadRegistry`/`DefaultLetterheadResolver` (internal.letterhead) hold a list of registered `LetterheadProvider`s and delegate to the first one whose `supports(id)` is true — same first-match-wins shape as `DefaultPlaceholderResolver`, since letterhead resolution is fundamentally a provider-lookup problem, not a direct-registration problem. Miss (`no provider supports` this id) throws `LetterheadNotFoundException` (new sibling in `api.exception`).

`LetterheadResolvers.standard()` factory (port) — mirrors `BrandResolvers.standard()`/`TemplateEngines.standard()` exactly: returns a `Bundle` with `register(LetterheadProvider)` and `resolver(): LetterheadResolver`.

## Core Types — QR Code

**`QrCodeContent`** (api.qr) — what an application's `QrCodeProvider` hands over: `byte[] imageBytes`, `String mimeType` (the SDK renders whatever image bytes it's given — it never generates a QR code itself, per the explicit "SDK only renders" requirement). Same defensive-copy contract as `Letterhead`/`BrandLogo`.

**`QrCodePlacement`** (api.qr) — open-value type (same pattern as `LetterheadPlacement`) describing where a QR code sits; MVP constant `StandardQrCodePlacement.BOTTOM_RIGHT` is sufficient for Stage 4's scope (an invoice-style QR in a corner), kept open for Stage 6 to add more.

**`QrCodeProvider`** (port, SPI, extends `Extension`) — `boolean supports(String key)`; `QrCodeContent resolve(String key)`. Keyed by a plain `String` rather than a dedicated identity type — unlike `Letterhead`/`Brand`/`Template` which are pre-declared, registered entities, a QR code is typically requested ad hoc per document instance (e.g. "the payment link for *this* invoice"), so a lightweight string key (the application defines its own key scheme — `"payment-link"`, `"invoice-verify"`, whatever) fits better than minting a new `QrCodeId` identity type for something that's often a one-off per document. This mirrors `PlaceholderProvider.supports(String key)`'s shape exactly, which resolved the analogous "per-document, app-defined key" problem already in Stage 2.

**`QrCodeResolver`** (port) — `resolve(String key): QrCodeContent`. `DefaultQrCodeRegistry`/`DefaultQrCodeResolver` (internal.qr), `QrCodeResolvers.standard()` factory (port) — identical shape to the Letterhead side, down to the exception naming convention (`QrCodeNotFoundException`).

## Error Handling

`LetterheadNotFoundException`/`QrCodeNotFoundException` (unchecked, `api.exception`) on resolution miss — genuine caller errors, matching the `TemplateNotFoundException`/`BrandNotFoundException`/`RendererNotFoundException` precedent set in every prior stage. No silent fallback to a default letterhead or blank QR — a consuming application that requests a letterhead by an unregistered id has made a real error and should see it immediately, not get a blank page silently.

## Testing

- `Letterhead`/`QrCodeContent`: mutation-based defensive-copy tests (construction + return), from day one — not added after the fact, per the lesson every prior stage has had to re-learn.
- `LetterheadFormat`/`LetterheadPlacement`/`LetterheadOverlayPolicy`/`QrCodePlacement`: open-value-type tests mirroring `StandardDocumentType`'s test shape (constants expose expected codes, `of()` builds custom values, blank/null rejected).
- `DefaultLetterheadResolver`/`DefaultQrCodeResolver`: resolve-through-first-supporting-provider (with 2+ registered providers, proving ordering — closing the exact coverage gap Stage 2's `DefaultPlaceholderResolverTest` review flagged as inherited-but-never-fixed), not-found throws the correct typed exception.
- `LetterheadResolvers.standard()`/`QrCodeResolvers.standard()`: register-then-resolve round-trip, zero internal types in the `Bundle` public signature.
- Architecture test: extend `DocumentArchitectureTest`'s factory-class exception pattern with `LetterheadResolvers`/`QrCodeResolvers` if (and only if) they trigger a genuine new port→internal cycle — this module's sixth and seventh such exceptions, added the same narrow way as the prior five, never via a package move.

## Out of Scope for Stage 4

Actual letterhead overlay rendering, actual QR image positioning/rendering onto a page (both Stage 6 — the PDF renderer is what actually consumes these resolved values). `LetterheadPlacement`/`QrCodePlacement` values beyond the one MVP constant each ship with. Any filesystem/S3/database/cloud-storage code (explicitly forbidden — providers are 100% application-supplied).
