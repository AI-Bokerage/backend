# fin-document — Stage 6 Design: PDF Renderer (PDFBox)

Date: 2026-08-04
Status: Approved for planning
Part of: Phase 12C — Document Engine Completion (MVP for FIN-SVC), Stage 6 of 6 (Stages 3-8)

## Context

Stages 1-5 (Foundation, Template + Placeholder Engine, Brand Profile, Letterhead + QR, Layout + Watermark) are complete, reviewed, and frozen. Stage 6 is the module's first stage with an actual visual output: a `PdfRenderer` implementing Stage 1's `DocumentRenderer` SPI, consuming `ComposedDocument` and producing real PDF bytes via Apache PDFBox internally. This is also the module's first stage to add a real external dependency — everything through Stage 5 depended on `fin-api` only.

**This is the highest-risk stage in the whole engine.** Every prior stage built and proved a value model with no rendering consumer; this stage is the consumer, and it's where every value model built in Stages 3-5 (`BrandProfile`, `Letterhead`, `QrCodeContent`, `PageLayout`, `Watermark`) gets used for the first time, together, for real. Bugs here are the first ones with actual visual/functional consequences rather than just API-contract consequences.

## Non-Negotiable Constraints (from the brief, restated because this stage is where violating them actually matters)

- PDFBox types (`PDDocument`, `PDPage`, `PDPageContentStream`, `PDFont`, etc.) must **never** appear in any public method signature of `fin-document`. `PdfRenderer` implements `DocumentRenderer` (`render(ComposedDocument, RendererConfiguration): RenderResult`) — the public contract is exactly Stage 1's existing interface, unchanged. All PDFBox usage lives inside `internal.renderer.pdf`, invisible from outside the module.
- The PDFBox dependency is added as `implementation`, not `api`, in `build.gradle.kts` — this is enforced at the build level, not just by convention, exactly like `fin-stripe`'s `implementation(libs.stripe.java)` pattern for the Stripe SDK.
- `PdfRenderer` consumes `ComposedDocument`, never `DocumentModel` — the renderer paints a fully composed document; it never resolves placeholders, never processes templates, never applies branding logic. Branding/letterhead/layout/watermark/QR *data* arrives already-resolved, as part of the render request (see "How PdfRenderer Receives Composed Inputs" below) — the renderer's only job is turning already-decided values into PDF drawing calls.
- Must support rendering onto an uploaded PDF letterhead (mandatory, explicit).

## How PdfRenderer Receives Composed Inputs

Stage 1's `DocumentRenderer.render(ComposedDocument document, RendererConfiguration configuration)` signature is frozen and unchanged. `RendererConfiguration` (Stage 1, `port`) already wraps a `DocumentAttributes` bag (`RendererConfiguration.of(DocumentAttributes options)`) — this is the existing, established extension point every renderer configuration need has used so far, and Stage 6 continues using it rather than inventing a parallel configuration channel.

**Decision:** `PdfRenderer` reads a fixed set of well-known attribute keys from `RendererConfiguration.options()` for the non-text visual inputs that don't fit into `ComposedDocument`'s generic section/element model:
- `"pdf.brandProfile"` → expects a `BrandProfile` object attached as a *typed* attribute. **Problem:** Stage 1's `DocumentAttributes` is a `Map<String, String>` (string-to-string), which cannot carry a `BrandProfile` object directly.

This is a real seam the prior stages didn't anticipate, and it needs a decision now rather than a workaround improvised mid-implementation:

**Resolved design:** Extend `RendererConfiguration` (additive only — Stage 1's `defaults()`/`of(DocumentAttributes)` factories and `options()` accessor are untouched) with a small, additional, strongly-typed side-channel specifically for this stage's non-string composed inputs, since forcing `BrandProfile`/`Letterhead`/`PageLayout`/`Watermark`/`QrCodeContent` through a `Map<String,String>` would mean serializing rich objects into strings for no reason other than an accidental type mismatch discovered a stage too late. Add:

```java
RendererConfiguration.withPdfInputs(PdfRenderInputs inputs): RendererConfiguration
```

where `PdfRenderInputs` (new type, `api.result` — same package as `RenderResult`, since it's a rendering-time input carrier, analogous in spirit to a request object) bundles:
- `Optional<BrandProfile> brandProfile`
- `Optional<Letterhead> letterhead`
- `PageLayout layout` (required — every PDF needs *some* layout; `PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build()` is the sensible zero-config default if the caller doesn't specify one, applied by `PdfRenderer` itself, not by `PdfRenderInputs`, keeping `PdfRenderInputs` a pure carrier with no defaulting logic of its own)
- `Optional<Watermark> watermark`
- `Map<String, QrCodeContent> qrCodesByKey` (a document can place more than one QR code; keyed the same way `QrCodeProvider`/`QrCodeResolver` already key QR lookups, so a caller who resolved QR content via `QrCodeResolvers.standard()` can pass the resolved map straight through)

`RendererConfiguration.pdfInputs(): Optional<PdfRenderInputs>` is the accessor `PdfRenderer` reads. Every other existing `DocumentRenderer` (JSON/Text/Markdown/CSV/XML) ignores `pdfInputs()` entirely — it's additive, optional, and irrelevant to formats that don't have a visual page model. This keeps `fin-document`'s dependency direction correct: `RendererConfiguration` (port, already exists) references `PdfRenderInputs` (a new api type) and the five Stage 3-5 value types it wraps, all within `fin-document`'s own `api.*` packages — no new cross-package dependency problem, since `port` already legitimately depends on `api.*` throughout this module.

## Where This Plugs Into the Pipeline

```
ComposedDocument + RendererConfiguration(with PdfRenderInputs)
     │
     ▼
PdfRenderer (implements DocumentRenderer, internal.renderer.pdf)
     │  internally: PDFBox PDDocument/PDPage/PDPageContentStream — never exposed
     ▼
RenderResult (PDF bytes, mimeType "application/pdf")
```

Registered into the existing `DocumentRenderers.standard()` factory (Stage 1) as a sixth built-in renderer, `RendererId.of("pdf")`, mimeType `"application/pdf"` — additive to that factory exactly the way Stage 1's five renderers were each added one at a time.

## Package Layout

```
io.genfin.document
├── api
│   └── result           PdfRenderInputs (new, alongside existing RenderResult/RenderFailure)
├── port
│   └── RendererConfiguration   (existing Stage 1 type — additive withPdfInputs()/pdfInputs() methods)
└── internal
    └── renderer
        └── pdf
            ├── PdfRenderer                  (implements DocumentRenderer)
            ├── PdfDocumentBuilder            (owns the PDDocument lifecycle: create pages, set size/orientation)
            ├── PdfContentPainter             (draws DocumentSection/DocumentElement content via double-dispatch visitor)
            ├── PdfLetterheadOverlay          (loads letterhead bytes, overlays generated content onto it)
            ├── PdfWatermarkStamper           (draws watermark text diagonally with opacity)
            ├── PdfHeaderFooterPainter        (draws layout header/footer text + page-number substitution)
            └── PdfImagePlacer                (draws brand logo / QR code images at fixed positions)
```

Splitting PDFBox usage across these six focused internal classes (rather than one 800-line `PdfRenderer` god-class) follows this module's established "one clear responsibility per file" discipline (mirrors how Stage 2's `TemplateParser`/`TemplateCompiler`/`DefaultTemplateEngine` were kept as separate focused classes rather than one monolith). `PdfRenderer` itself is the thin orchestrator implementing the public SPI method; every actual PDFBox call lives in one of the five specialist classes it delegates to.

## Rendering Approach (MVP scope — explicitly not a full layout engine)

**Text (`TextBlockElement`, `KeyValueElement`):** simple top-to-bottom text flow within the page's margin box, with basic word-wrapping (measure string width against the page's content width using the chosen `PDFont`, break at word boundaries when a line would overflow). No rich text, no inline styling, no multi-column flow — matches the brief's explicit "No advanced layouts... No rich typography" exclusion.

**Tables (`TableElement`):** a simple equal-width-column grid — header row drawn with a distinguishing style (bold or a background rule line), each data row drawn beneath it, cell text truncated or wrapped the same basic way as paragraph text if it overflows its column width. No merged cells, no nested tables, no auto-sizing columns by content — matches "No complex components" and the module's broader "no rich CSS layout engine" exclusion (documented fully in Stage 8's Future Roadmap).

**Multi-page:** when content exceeds the current page's remaining vertical space (tracked as a running Y-cursor against the margin box bottom), `PdfDocumentBuilder` starts a new `PDPage` with the same paper size/orientation/margins, and `PdfHeaderFooterPainter`/`PdfWatermarkStamper`/letterhead-overlay are all re-applied per page (a letterhead, watermark, and header/footer are page decorations, not document-once decorations — this must repeat correctly on every page, not just the first).

**Letterhead overlay (mandatory):** when `PdfRenderInputs.letterhead()` is present and its `LetterheadFormat` is `PDF`, `PdfLetterheadOverlay` loads the letterhead's PDF bytes as a *second* `PDDocument`, and for each generated page, opens that generated page's `PDPageContentStream` in **append** mode after first drawing the corresponding letterhead page's content as a background (PDFBox's `LayerUtility`/page-importing mechanism, or a direct content-stream-prepend technique — the implementer verifies the exact PDFBox 3.x API against current PDFBox documentation, since Stage 6 is this module's first use of PDFBox and no prior stage's code can be mirrored for the exact call sequence). This satisfies `StandardLetterheadOverlayPolicy.CONTENT_ON_TOP` (the letterhead is the background layer, generated content draws over it) — `LETTERHEAD_ON_TOP` is accepted as a valid `LetterheadOverlayPolicy` value but is out of scope to actually implement in Stage 6 (document this gap explicitly in Stage 8's Future Roadmap rather than half-implementing it — the brief's "mandatory" requirement is specifically the content-on-top case, since that's what "rendering onto an uploaded PDF letterhead" means literally). For non-PDF letterhead formats (PNG/JPEG/SVG), `PdfLetterheadOverlay` draws the image as a full-page background using `PdfImagePlacer`'s image-drawing capability instead of PDF-page-merging — SVG specifically requires a rasterization step Stage 6 may defer to Stage 8's Future Roadmap if no lightweight SVG-to-image path exists without adding a new dependency (check what's actually available before deciding to skip it — an outright PNG/JPEG-only MVP for non-PDF letterheads, with SVG explicitly roadmapped, is an acceptable, honestly-scoped MVP if SVG rasterization would require a new dependency this stage doesn't otherwise need).

**Watermark:** `PdfWatermarkStamper` draws the resolved `WatermarkLabel`'s code as large, diagonal, semi-transparent text (`WatermarkOpacity.value()` maps to PDFBox's graphics-state alpha constant) across the page center, using `StandardWatermarkPlacement.DIAGONAL_CENTER`'s the only placement this stage implements (matches Stage 5's MVP scope — the value type supports future placements, the renderer implements exactly one).

**Header/Footer/Page Numbers:** `PdfHeaderFooterPainter` draws `PageLayout.header()`/`footer()` text at the top/bottom margin, and if `PageNumberConfiguration.enabled()`, substitutes `{n}`/`{total}` tokens in `PageNumberConfiguration.format()` with the actual current-page/total-page-count integers (a two-token literal substitution, not the Stage 2 Template Engine — consistent with Stage 5's design spec's explicit decision on this). Total page count requires either a two-pass render (render once to count pages, then render again with the known total) or deferred rendering (draw placeholder, patch in the real total after all pages are known) — **decision: two-pass**, since it's dramatically simpler to implement correctly and PDF generation for an invoice-sized document is fast enough that a second pass is not a meaningful performance concern for this MVP.

**Images (brand logo, QR codes):** `PdfImagePlacer` decodes the logo's/QR's `byte[]` via PDFBox's image-from-bytes API and draws it at a fixed position (logo: top-left or top-right of the header area; QR: `StandardQrCodePlacement.BOTTOM_RIGHT`'s the only placement this stage implements, mirroring the watermark placement scoping decision above) at a fixed reasonable size (no dynamic aspect-ratio-preserving fit-to-box algorithm in this MVP — document the fixed-size limitation, don't build a general image-fitting algorithm for it).

## Error Handling

`PdfRenderer.render(...)` throws `RenderException` (Stage 1, existing) for any PDFBox-level failure (corrupt letterhead bytes, unsupported image format, PDFBox internal I/O error) — wrapping the underlying PDFBox exception as the cause, never leaking a PDFBox exception type across the module boundary uncaught. A missing required layout defaults silently (see `PdfRenderInputs.layout` being required — `PdfRenderer` applies the A4/Portrait zero-config default itself if the caller passes no layout at all, i.e. if `pdfInputs()` is entirely absent) — but if `pdfInputs()` *is* present with an explicitly-null-like empty layout, that's a caller error and validated the same way every Stage 3-5 constructor validates its required fields.

## Testing

- `PdfDocumentBuilder`: produces a `PDDocument` with the correct page count/size/orientation for a given `PageLayout` — inspect the resulting PDF's page dimensions programmatically (PDFBox exposes `PDPage.getMediaBox()`), not just "it didn't throw."
- `PdfContentPainter`: render a `ComposedDocument` with known text/table content, then use PDFBox's own text-extraction utility (`PDFTextStripper`) to assert the expected text appears in the output PDF — the same "render, then verify via the format's own read-back capability" testing philosophy Stage 2 used when it fed rendered output through `JsonDocumentRenderer` and parsed the JSON back.
- `PdfLetterheadOverlay`: render with a real (small, fixture) letterhead PDF, assert the output PDF's page count matches (overlay doesn't accidentally add/drop pages), and that generated text still extracts correctly (proving content-on-top actually drew over the letterhead layer rather than being obscured or lost).
- `PdfWatermarkStamper`/`PdfHeaderFooterPainter`/`PdfImagePlacer`: each independently testable against a fresh `PDDocument`/`PDPage`, asserting the expected text extracts or the expected image XObject count increases.
- Multi-page: a `ComposedDocument` with enough content to force a page break produces the expected page count with the same header/footer/watermark/letterhead present on every page (not just page 1) — this is the specific "decoration must repeat, not just apply once" risk called out above, so it gets its own explicit test.
- `PdfRenderer` end-to-end: register `"pdf"` into `DocumentRenderers.standard()`, resolve it, render a full `ComposedDocument` with brand/letterhead/watermark/QR/layout all present via `PdfRenderInputs`, assert `RenderResult.mimeType() == "application/pdf"` and the byte content is a structurally valid PDF (PDFBox can re-load it without error) containing the expected extracted text.
- Architecture test: `PdfRenderer` and its five specialist classes live in `internal.renderer.pdf`, never exported; the module-wide "no PDFBox type in a public signature" constraint is checked by extending `DocumentArchitectureTest` with a rule scanning `api`/`port` package class files for any import starting with `org.apache.pdfbox` — if this rule is new to the test file, it's additive, not a modification of an existing rule.

## Out of Scope for Stage 6

Responsive layouts, complex grids, columns, magazine layouts, rich typography, dynamic image fit-to-box sizing, SVG letterhead rasterization (unless a zero-new-dependency path exists — check before committing to build or skip), `LETTERHEAD_ON_TOP` overlay policy's actual implementation, multiple simultaneous watermark/QR placements beyond the one each this stage implements, merged/nested table cells, auto-sized table columns. All of the above are Stage 8 Future Roadmap material, explicitly documented there, not half-built here.
