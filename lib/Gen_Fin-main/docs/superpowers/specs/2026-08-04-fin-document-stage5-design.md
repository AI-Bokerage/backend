# fin-document — Stage 5 Design: Layout Engine (Minimal) + Watermark

Date: 2026-08-04
Status: Approved for planning
Part of: Phase 12C — Document Engine Completion (MVP for FIN-SVC), Stage 5 of 6 (Stages 3-8)

## Context

Stages 1-4 (Foundation, Template + Placeholder Engine, Brand Profile, Letterhead + QR) are complete, reviewed, and frozen. Stage 5 delivers Phase 12C Groups 3 and 4: **Layout Engine (Minimal)** and **Watermark**. Both are pure declarative configuration — page geometry and a stamped-text overlay policy — with zero rendering logic. Stage 6's PDF renderer is the first and only consumer of either. This is the same "build the type, prove it's a valid immutable value, defer the visual consumer" pattern every prior stage has used.

Unlike Letterhead/Brand/QR, neither Layout nor Watermark needs a provider SPI or a resolver — a `PageLayout` is a small, fully-specified value an application constructs directly (there's nothing external to fetch, unlike a letterhead's bytes or a brand's registration data), and a `Watermark` is likewise a direct value (a standard label like `DRAFT` plus a policy). Both are built with the plain `of(...)`/builder pattern already established, not the provider/registry/resolver pattern.

## Where This Plugs Into the Pipeline

Neither wires into `DocumentComposer` in this stage. `PageLayout` and `Watermark` are inputs Stage 6's `PdfRenderer` will accept directly (likely via `RendererConfiguration`, Stage 1's existing extensible config carrier, or a new PDF-specific configuration type Stage 6 defines) — this stage only builds the value types themselves.

## Package Layout

```
io.genfin.document
└── api
    ├── layout       PaperSize, StandardPaperSize, Orientation, StandardOrientation, Margins,
    │                LayoutHeader, LayoutFooter, PageNumberConfiguration, PageLayout
    └── watermark    Watermark, StandardWatermarkLabel, WatermarkPlacement, StandardWatermarkPlacement,
                     WatermarkOpacity
```

No `port`/`internal` packages this stage — there is no SPI, no registry, no resolver to hide. This is a deliberate, smaller architectural footprint than every prior stage, matching the actual complexity of the requirement (per the brief's own "No advanced layouts... No custom graphics yet" scope limits).

## Core Types — Layout

**`PaperSize`** (api.layout) — open-value type (interface + record-with-constants, mirroring `StandardDocumentType`'s pattern exactly). `StandardPaperSize` constants: `A4`, `LETTER`. Carries no dimensions in this stage (a real renderer needs width/height in points, but hardcoding those into this type would tie the value model to a specific rendering library's unit system prematurely) — Stage 6's PDF renderer maps the resolved `PaperSize` code to PDFBox's own dimension constants internally; this module never needs to know what a point is.

**`Orientation`** (api.layout) — open-value type. `StandardOrientation` constants: `PORTRAIT`, `LANDSCAPE`.

**`Margins`** (api.layout) — `int topPoints`, `int bottomPoints`, `int leftPoints`, `int rightPoints` (all non-negative, validated). `Margins.of(int top, int bottom, int left, int right)` + `Margins.uniform(int allSides)` convenience factory + `Margins.none()` (all-zero, the actual zero-value default — not a null case).

**`LayoutHeader`** / **`LayoutFooter`** (api.layout) — each wraps a single `String text` (same minimal shape as Stage 3's `BrandHeader`/`BrandFooter` — this stage's header/footer text is layout-level, distinct from brand-level; Stage 6's renderer decides how the two combine, that's a rendering decision, not a value-model decision).

**`PageNumberConfiguration`** (api.layout) — `boolean enabled`, `String format` (e.g. `"Page {n} of {total}"` — a plain string template the renderer substitutes `{n}`/`{total}` into; this is NOT the Stage 2 Template Engine, it's a much simpler fixed substitution scheme scoped to exactly this one use, since pulling in the full template engine for two tokens would be over-engineering). `PageNumberConfiguration.disabled()` (the default) and `PageNumberConfiguration.enabled(String format)`.

**`PageLayout`** (api.layout) — the aggregate: `PaperSize paperSize`, `Orientation orientation`, `Margins margins`, `LayoutHeader header`, `LayoutFooter footer`, `PageNumberConfiguration pageNumbers`. Built via `PageLayout.builder(PaperSize paperSize, Orientation orientation)` (required fields) with chainable `.margins(Margins)`, `.header(LayoutHeader)`, `.footer(LayoutFooter)`, `.pageNumbers(PageNumberConfiguration)`, `.build()` — mirrors `BrandProfile.Builder`'s exact shape, **including the Stage 4 lesson**: every optional setter must coalesce `null` to its empty/disabled default inside the constructor (not silently store `null`), per the exact defect class caught twice already in this module (Stage 3's `BrandProfile`, and implicitly guarded against in Stage 4's `Letterhead`/`QrCodeContent`).

## Core Types — Watermark

**`StandardWatermarkLabel`** (api.watermark) — open-value type. Constants: `DRAFT`, `PAID`, `VOID`, `COPY`, `CONFIDENTIAL` (exactly the brief's list) — plus `of(String label)` for a custom label, since "Keep the API generic" is explicit in the brief.

**`WatermarkPlacement`** (api.watermark) — open-value type, mirroring `LetterheadPlacement`/`QrCodePlacement`'s exact shape. `StandardWatermarkPlacement` constant: `DIAGONAL_CENTER` (the conventional "stamped diagonally across the page" placement — the only one this MVP needs; kept open for Stage 6+ to add more without an API break).

**`WatermarkOpacity`** (api.watermark) — `double value` (0.0–1.0, validated range — this is the one type in the whole module so far with a numeric range constraint rather than a blank/null check, so it gets its own explicit validation test for both boundary violations). `WatermarkOpacity.of(double value)`, with a sensible non-magic default exposed as `WatermarkOpacity.DEFAULT` (0.3 — a conventional light stamped-watermark opacity; documented as a default, not hardcoded silently into the renderer per Group 9's "no constants" configuration philosophy carried forward).

**`Watermark`** (api.watermark) — the aggregate: `StandardWatermarkLabel label` (or any custom `WatermarkLabel`... **decision: model label as the same open-value-type pattern, `WatermarkLabel` interface + `StandardWatermarkLabel` record**, exactly like every other open-value type in this module, rather than a raw `String`, so it's consistent and so a consuming app can't accidentally pass an unrelated string where a label was intended), `WatermarkPlacement placement`, `WatermarkOpacity opacity`. `Watermark.of(WatermarkLabel label, WatermarkPlacement placement, WatermarkOpacity opacity)` — no builder needed (3 required fields, no optional ones, unlike `PageLayout`/`BrandProfile`).

## Error Handling

All open-value types validate via `io.genfin.api.validation.Validate.notBlank` on their code, per the established pattern. `Margins`'s four ints validate non-negative (a negative margin is nonsensical, not merely unusual) via the shared `Validate.nonNegative` helper, which already exists in `Validate.java` alongside `Validate.positive` and `Validate.required` — use these directly rather than hand-rolling an `if (value < 0) throw new ValidationException(...)` check. `WatermarkOpacity` validates `0.0 <= value <= 1.0` via `Validate.required` with an explicit range condition. `PageLayout.builder(...)` validates `paperSize`/`orientation` non-null at `build()` time, and — the Stage 4 lesson applied here from day one — every optional setter's stored value is coalesced to its non-null default inside the private constructor, never left nullable.

## Testing

- Open-value type tests (`PaperSize`/`Orientation`/`WatermarkLabel`/`WatermarkPlacement`): constants expose expected codes, `of()` builds custom values, blank/null rejected — mirroring `StandardDocumentType`'s test shape exactly, by now a completely mechanical pattern in this module.
- `Margins`: `of`/`uniform`/`none` construction, negative-value rejection for each of the four fields independently.
- `WatermarkOpacity`: valid range accepted (0.0, 1.0, 0.3 all succeed), below-0.0 and above-1.0 both rejected — explicit boundary tests since this is the module's first numeric-range type.
- `PageLayout.builder()`: required-fields-only construction defaults every optional field correctly (mirroring `BrandProfileTest`'s `buildsWithOnlyRequiredFieldsAndDefaultsTheRest` test, including the **explicit null-setter test** this module now writes from day one instead of after a review catches its absence — call `.margins(null).header(null).footer(null).pageNumbers(null).build()` and assert every accessor returns its non-null default, exactly mirroring the exact test Stage 3's final review had to add after the fact).
- `Watermark`: straightforward construction/accessor test, no builder complexity to test.
- Architecture test: this stage adds no `port`/`internal` packages and no factory reaching into `internal`, so no new ArchUnit `.ignoreDependency(...)` exception is expected — if one turns out to be needed, add it the established narrow-additive way, never via a package move (this module's sixth-plus such lesson).

## Out of Scope for Stage 5

Responsive layouts, complex grids, columns, magazine layouts, rich typography (explicit Phase 12C exclusions — Stage 8's Future Roadmap documents these, not built here). Custom watermark graphics (explicit exclusion — only text-label stamping is in scope). Any actual page-geometry rendering, header/footer drawing, page-number substitution, or watermark stamping (all Stage 6 — the PDF renderer is the only thing that turns these values into pixels).
