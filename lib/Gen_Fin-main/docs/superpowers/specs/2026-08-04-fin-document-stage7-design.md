# fin-document — Stage 7 Design: Preview + Validation + Configuration + Builders + Events

Date: 2026-08-04
Status: Approved for planning
Part of: Phase 12C — Document Engine Completion (MVP for FIN-SVC), Stage 7 of 6 (Stages 3-8, actually 6 of "Stages 3-8" per prior stage numbering)

## Context

Stages 1-6 (Foundation, Template+Placeholder, Brand, Letterhead+QR, Layout+Watermark, PDF Renderer) are complete, reviewed, and frozen. Stage 7 delivers the remaining "plumbing" groups from Phase 12C: **Preview** (Group 7), **Validation** (Group 8), **Configuration** (Group 9), **Builders** (Group 10 — already substantially satisfied by every aggregate built since Stage 3 having its own builder; this stage closes the remaining gaps), and **Events** (Group 11). None of these introduce new rendering capability — they wrap/observe/configure what Stages 1-6 already built.

## Group 7 — Preview

**`DocumentPreview`** (api.preview) — a lightweight wrapper: `PreviewResult`'s bytes plus metadata (`RendererId`, `mimeType`, `pageCount` if known). **`PreviewConfiguration`** (api.preview) — `RendererId targetRenderer`, defaulting to `RendererId.of("pdf")` if unset (preview is inherently about seeing what the final render will look like, so the PDF renderer is the natural preview default; other renderers can preview too — CSV preview is just CSV output). **`PreviewRenderer`** (port) — `preview(ComposedDocument, RendererConfiguration): PreviewResult`, a thin wrapper delegating to `RendererResolver.resolve(previewConfig.targetRenderer())` and calling that renderer's `render(...)` — Preview is explicitly **not** a second rendering pipeline, it's the existing pipeline with a different result-shaping wrapper, consistent with the brief's stated purpose ("applications preview before exporting" — same output, earlier look). `PreviewResult` (api.preview) wraps the underlying `RenderResult` plus whatever preview-specific metadata is useful (page count, when derivable cheaply from the renderer — for `PdfRenderer` specifically, this is a free byproduct since Stage 6's two-pass render already counts pages internally, but Preview must not require every renderer to expose this — `pageCount` is `Optional<Integer>`, empty when the renderer can't cheaply provide it).

## Group 8 — Validation

Validation errors listed in the brief (missing template, missing renderer, missing placeholder, missing brand, missing letterhead, invalid layout, unsupported format, invalid page configuration) are **already thrown** as typed exceptions by the relevant Stage 1-6 component (`TemplateNotFoundException`, `RendererNotFoundException`, `BrandNotFoundException`, `LetterheadNotFoundException`, `ValidationException` from every constructor, `UnsupportedOperationException` from `PdfLetterheadOverlay` for SVG). What's missing is a **pre-flight validation pass** — a way for an application to check "will this render request succeed" *before* committing to a potentially-expensive render, surfacing every problem at once rather than failing on the first one encountered mid-render.

**`DocumentValidator`** (port) — `validate(ComposedDocument, RendererConfiguration): ValidationResult`. **`ValidationResult`** (api.validation) — `boolean valid()`, `List<ValidationIssue> issues()`. **`ValidationIssue`** (api.validation) — `String code()` (e.g. `"MISSING_LETTERHEAD"`, `"UNSUPPORTED_RENDERER"`), `String message()`. `DefaultDocumentValidator` (internal.validation) checks, without actually rendering: does `RendererResolver` have the requested `RendererId` registered; if `PdfRenderInputs` is present, is its `letterhead()` (if present) format one of the four handled formats (not a fifth unknown format that would only fail at render time); is `layout()` non-null (always true per Stage 6's construction guarantee, but checked defensively since this validator's job is to catch problems before they become exceptions, not to trust every upstream guarantee blindly). This is deliberately a **lightweight, additive check**, not a rendering dry-run — actually attempting a dry-run render to validate would duplicate Stage 6's real rendering cost, defeating Preview's own purpose.

## Group 9 — Configuration

**`DocumentEngineConfiguration`** (api.config) — immutable, `RendererId defaultRenderer`, `PaperSize defaultPaperSize`, `Orientation defaultOrientation`, `Optional<BrandId> defaultBrand`, `Optional<LetterheadId> defaultLetterhead`, `Margins defaultMargins`, `PageNumberConfiguration defaultPageNumbers`, `boolean previewEnabled`, `boolean watermarkEnabled`. Built via `DocumentEngineConfiguration.builder()` with all-optional chainable setters (every field has a sensible zero-config default — `RendererId.of("json")` for `defaultRenderer` since JSON requires no PDFBox/visual setup and is the safest zero-config choice, `StandardPaperSize.A4`/`StandardOrientation.PORTRAIT` for layout defaults, `Margins.none()`, `PageNumberConfiguration.disabled()`, both booleans default `false`) — mirrors every builder's now-established null-coalescing-in-constructor pattern. This type is **consumed by applications wiring up the engine**, not by any Stage 1-6 internal — it's a convenience default-holder an application can build once and reference when constructing individual `RendererConfiguration`/`PdfRenderInputs` instances per-document; it does not itself get threaded through the render call, keeping Stage 6's `RendererConfiguration`/`PdfRenderInputs` the single source of truth for what a specific render actually uses. This satisfies "no Spring, no YAML, no constants" — it's a plain immutable Java object an application populates however it wants (hardcoded, a config file it parses itself, environment variables — the SDK doesn't care).

## Group 10 — Builders

Every aggregate built since Stage 3 already has a builder (`BrandProfile.Builder`, `PageLayout.Builder`, `PdfRenderInputs.Builder`) or is simple enough not to need one (`Letterhead.of(...)`, `Watermark.of(...)`, `QrCodeContent.of(...)` — 2-4 required fields, no optional ones). The brief's Group 10 list (`BrandProfile`, `Letterhead`, `Layout`, `Watermark`, `Preview`, `RenderRequest`, `PdfConfiguration`) is covered as follows: `BrandProfile`/`Layout` (=`PageLayout`) already have builders from Stages 3/5. `Letterhead`/`Watermark` deliberately don't need one (too few fields — adding a builder would be premature abstraction the module has consistently avoided). **`PreviewConfiguration`** (this stage, Group 7) gets a builder since it's genuinely optional-heavy. **`RenderRequest`** — **decision: this is exactly what `PdfRenderInputs` already is** (Stage 6) generalized one level — rather than inventing a second, parallel "render request" type that would duplicate `PdfRenderInputs`'s fields, this stage does **not** add a new `RenderRequest` type; `PdfRenderInputs.Builder` (already exists) is the render-request builder for the PDF path, and `RendererConfiguration`'s existing `of(DocumentAttributes)`/`withPdfInputs(...)` factories are the render-request builder for every renderer generically. **`PdfConfiguration`** — likewise already covered by `PdfRenderInputs` + `PageLayout`; no new type. This stage's only genuinely new builder is `DocumentEngineConfiguration.Builder` (Group 9) and `PreviewConfiguration.Builder` (Group 7).

## Group 11 — Events

**`DocumentEvent`** (api.event) — marker interface, `Instant occurredAt()`. Seven concrete event types per the brief, each a small immutable record-shaped class implementing `DocumentEvent`: `DocumentRendered` (`RendererId`, `DocumentId`), `PdfGenerated` (`DocumentId`, `int pageCount`), `BrandApplied` (`BrandId`, `DocumentId`), `LetterheadApplied` (`LetterheadId`, `DocumentId`), `LayoutApplied` (`DocumentId`, paper size code, orientation code), `WatermarkApplied` (`WatermarkLabel`, `DocumentId`), `QrCodeRendered` (`String key`, `DocumentId`). **`DocumentEventListener`** (port, SPI, extends `Extension`) — `void onEvent(DocumentEvent event)`. **`DocumentEventPublisher`** (port) — `void publish(DocumentEvent event)`; `DocumentEventPublishers.standard()` (port factory, mirrors every other Stage 3-6 `XResolvers.standard()` shape) returns a `Bundle` with `register(DocumentEventListener)`/`publisher(): DocumentEventPublisher`.

**Decision: events are opt-in and not wired into `PdfRenderer` automatically in this stage.** Wiring event publication into `PdfRenderer.render(...)` would mean every render call needs an event publisher threaded through `RendererConfiguration` (another additive field, mirroring the `PdfRenderInputs` precedent) — this is a reasonable next increment, but doing it *and* getting the event-type list right *and* proving the plumbing works are three separable concerns. This stage builds the event types + publisher/listener SPI + factory and **proves them with direct unit tests** (publish an event, confirm the listener receives it) — wiring `PdfRenderer` to actually publish these events during a real render is explicitly deferred to a documented follow-up (noted in Stage 8's Future Roadmap section if not completed opportunistically within this stage's budget), since "only domain events, never messaging" (the brief's own constraint) means this is a synchronous, in-process notification mechanism an application can already get equivalent value from by calling `PreviewRenderer`/`DocumentValidator` before and `DocumentRenderer` after and comparing — the events are a convenience, not the only way to observe what happened.

## Package Layout

```
io.genfin.document
├── api
│   ├── preview       DocumentPreview, PreviewConfiguration, PreviewResult
│   ├── validation    ValidationResult, ValidationIssue
│   ├── config        DocumentEngineConfiguration
│   └── event         DocumentEvent, DocumentRendered, PdfGenerated, BrandApplied, LetterheadApplied,
│                     LayoutApplied, WatermarkApplied, QrCodeRendered
├── port
│   ├── PreviewRenderer
│   ├── DocumentValidator
│   ├── DocumentEventListener   (SPI, extends Extension)
│   ├── DocumentEventPublisher
│   └── DocumentEventPublishers (factory)
└── internal
    ├── preview    DefaultPreviewRenderer
    ├── validation DefaultDocumentValidator
    └── event      DefaultDocumentEventPublisher (backing DocumentEventPublishers.standard())
```

## Testing

Standard pattern by now: each new value/aggregate type gets construction + validation tests; `DefaultPreviewRenderer` gets a test proving it delegates to the resolved renderer and produces equivalent output to calling that renderer directly; `DefaultDocumentValidator` gets one test per checked condition (missing renderer id, unsupported letterhead format) plus a "valid request produces empty issues list" happy-path test; `DocumentEventPublishers.standard()` gets a register-then-publish-then-listener-receives round-trip test, once per event type or with a couple of representative types (not all seven exhaustively — the plumbing is identical across all seven, so 2-3 representative tests plus one test per event type's field-accessor correctness is proportionate, not exhaustive-for-its-own-sake).

## Out of Scope for Stage 7

Actually wiring event publication into `PdfRenderer`'s render call (deferred, documented). A `RenderRequest`/`PdfConfiguration` type distinct from `PdfRenderInputs` (deliberately not built — would duplicate existing types). Spring/YAML-based configuration loading (explicitly forbidden generally). A rendering dry-run inside `DocumentValidator` (deliberately lightweight, not a duplicate render).
