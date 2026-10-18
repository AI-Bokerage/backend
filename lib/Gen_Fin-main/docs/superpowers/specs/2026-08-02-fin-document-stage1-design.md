# fin-document — Stage 1 Design: Identity, Core Model, Composer Spine, Renderer SPI

Date: 2026-08-02
Status: Approved for planning

## Context

Gen-Fin's core financial domain is feature-complete through Phase 11 (Dunning Engine). Phase 12 is the Document Engine (`fin-document`): renders already-computed finance domain models (invoices, receipts, refunds, ledger reports, dunning letters, etc.) into professional business documents. It owns zero finance logic — only rendering/composition.

The full Phase 12 spec spans 23 groups (identity, core model, renderer SPI, template engine, branding, letterhead, theme, layout, watermark, digital signature, header/footer, attachments, placeholder engine, export, preview, validation, events, configuration, builders, serialization, SPI registration, testing, docs). That is too large for one plan/implementation cycle — consistent with how fin-dunning and fin-pricing were staged, Phase 12 is split into 6 stages, built in pipeline order:

1. **Stage 1 (this spec)** — Identity, Core Aggregate, Composer spine, Renderer SPI + built-in data renderers (JSON/Text/Markdown/CSV/XML)
2. Stage 2 — Template Engine
3. Stage 3 — Brand Engine + Letterhead Engine
4. Stage 4 — Theme Engine + Layout Engine + Watermark Engine + Digital Signature + Header/Footer Engine (+ PDF/HTML renderers)
5. Stage 5 — Placeholder Engine + Export Engine + Preview Engine
6. Stage 6 — Validation + Events + Configuration + Builders + full SPI Registration (`DocumentExtensions`) + exhaustive testing + `DOCUMENT.md`

Stage 1 delivers a working, testable pipeline spine that every later stage plugs into without touching caller-facing types:

```
Invoice / Refund / Ledger / Quote / ...
                │
                ▼
       DocumentMapper<T>  (SPI — no concrete impls yet)
                │
                ▼
          DocumentModel
                │
                ▼
        DocumentComposer  (Stage 1: NoOpDocumentComposer; Stage 2-4 replace internals)
                │
                ▼
         ComposedDocument
                │
                ▼
        RendererResolver
                │
                ▼
        DocumentRenderer (SPI)
                │
      ┌─────────┼──────────┬─────────┬─────────┐
      ▼         ▼          ▼         ▼         ▼
    JSON      Text      Markdown    CSV       XML
                │
                ▼
          RenderResult
```

`DocumentComposer` is the seam: Stage 1 ships a no-op passthrough (`DocumentModel` → `ComposedDocument` with no transformation). Stages 2-4 internally grow it into Template → Brand → Letterhead → Theme → Layout, without changing its public contract or any caller code. `ComposedDocument` (not `RenderableDocument`) is the deliberate name — once branding/templates/layout are applied it is a fully composed representation, not merely "renderable."

## Dependencies

Allowed (per module Phase 12 spec): `fin-api`, `fin-money`, `fin-invoice`, `fin-payment`, `fin-refund`, `fin-ledger`, `fin-pricing`, `fin-reconciliation`, `fin-dunning`.

Stage 1 uses **only `fin-api`** (already wired in `fin-core/fin-document/build.gradle.kts`). No concrete `DocumentMapper<T>` implementations exist yet — those require a dependency on the mapped module (e.g. `InvoiceDocumentMapper` needs `fin-invoice`) and are deferred to whichever stage/consumer wires them in. `DocumentMapper<T>` itself is a generic SPI contract with no knowledge of any domain type, so it can live in Stage 1 without pulling in any domain module.

Forbidden: Spring, database, RabbitMQ, Redis, HTTP, cloud SDKs, and (Stage 1 specifically) any PDF/HTML rendering library — those arrive in Stage 4.

## Package Layout

```
io.genfin.document
├── api
│   ├── identity     DocumentId, TemplateId, RendererId, BrandId, ThemeId, LayoutId,
│   │                LetterheadId, AttachmentId, WatermarkId
│   ├── model        DocumentModel, DocumentMetadata, DocumentSection, DocumentElement,
│   │                DocumentAttributes, DocumentType (extensible, StandardDocumentType constants)
│   ├── compose       DocumentComposer, ComposedDocument
│   └── result        RenderResult, RenderFailure
├── port
│   ├── DocumentRenderer         (SPI — applications register renderers)
│   ├── DocumentMapper<T>        (SPI — applications map domain objects to DocumentModel)
│   ├── RendererResolver         (SPI-facing resolution contract)
│   ├── RendererCapabilities
│   └── RendererConfiguration
└── internal
    ├── DefaultRendererRegistry   (package-private storage, not exposed)
    ├── DefaultRendererResolver   (implements RendererResolver, delegates to registry)
    ├── NoOpDocumentComposer      (Stage 1 default DocumentComposer)
    ├── renderer/
    │   ├── JsonDocumentRenderer
    │   ├── PlainTextDocumentRenderer
    │   ├── MarkdownDocumentRenderer
    │   ├── CsvDocumentRenderer
    │   └── XmlDocumentRenderer
    └── DocumentRenderers          (factory: default RendererResolver pre-registered with the 5 built-ins)
```

`DocumentRenderer` and `DocumentMapper` live in `port`, not `api` — they are extension points applications implement, not domain objects applications consume. This mirrors `PaymentGateway`/`TaxCalculator`/`ExchangeRateProvider` elsewhere in Gen-Fin. `RendererResolver` is the only renderer-lookup surface applications see; the registry is an internal implementation detail (mirrors how the rest of Gen-Fin exposes a resolver/facade over an internal registry, not the registry itself).

## Core Types

**Identity** — all nine ID types added now (thin, immutable, strongly-typed wrappers — same pattern as `InvoiceId` etc. elsewhere in the repo). They're pure value types with no behavior dependent on unbuilt engines, so adding all of them in Stage 1 avoids identity-package churn in every later stage.

**DocumentModel** — the aggregate root. Domain-agnostic tree: a `DocumentType` (extensible open-value type, not a Java `enum` — same pattern as `StandardFailureCategory` in fin-dunning, with `StandardDocumentType` constants: INVOICE, RECEIPT, REFUND, QUOTE, LEDGER_REPORT, TRIAL_BALANCE, STATEMENT, RECONCILIATION_REPORT, DUNNING_LETTER — applications can add their own), `DocumentMetadata` (id, type, created timestamp, locale, currency — all supplied by the mapper, not computed), a list of `DocumentSection`, each containing `DocumentElement`s (key-value fields, tables, text blocks — the minimal element vocabulary needed to render JSON/Text/Markdown/CSV/XML; richer element kinds arrive with Layout in Stage 4), and `DocumentAttributes` (open string-keyed attribute bag for anything a mapper wants to carry through that doesn't fit the structured model — this is what keeps `DocumentModel` reusable across arbitrary domain objects without new fields per document type).

Deferred to later stages (not in Stage 1): `DocumentPage` (needs Layout), `DocumentAttachment`/`AttachmentCollection` (Group 12, own stage), `DocumentContext`/`DocumentProperties`/`DocumentSummary` (needed once Placeholder/Preview engines exist to resolve against).

**ComposedDocument** — Stage 1's `DocumentComposer` output. Structurally identical to `DocumentModel` (same sections/elements) since no template/brand/letterhead/theme/layout exists yet to transform it — but it's a distinct type from day one so `DocumentRenderer` implementations are written once against `ComposedDocument` and never need to change when Stage 2-4 start actually transforming the content.

**DocumentComposer** (port) — single method: `ComposedDocument compose(DocumentModel model)`. `NoOpDocumentComposer` (internal) is Stage 1's only implementation: copies the model into a `ComposedDocument` unchanged. `DocumentComposers` (factory) exposes `DocumentComposers.noOp()`; a later stage adds `DocumentComposers.standard(...)` wiring Template→Brand→Letterhead→Theme→Layout without removing `noOp()`.

**DocumentRenderer** (port) — single method: `RenderResult render(ComposedDocument document, RendererConfiguration configuration)`. `RenderResult` (api) carries the output bytes/string plus `RendererId` and format metadata; `RenderFailure` (api) is the typed failure case (unsupported document type, resolution failure) — no exceptions for expected failure paths, consistent with Gen-Fin's result-object convention elsewhere.

**RendererResolver** (port) — single method: `DocumentRenderer resolve(RendererId id)` (or by format/capability — resolution-by-capability is the natural extension point but Stage 1 only needs resolution-by-id since there's no format-negotiation requirement yet; capability-based resolution is easy to add later without breaking this contract). `DefaultRendererResolver` (internal) delegates to `DefaultRendererRegistry` (internal). `DocumentRenderers.standard()` (factory, mirrors `DunningCaseLifecycles`/`FailurePolicies` factory pattern already used in fin-dunning) returns a `RendererResolver` pre-registered with the 5 built-in renderers.

**DocumentMapper\<T\>** (port) — single method: `DocumentModel map(T source)`. Purely generic; Stage 1 ships the contract only, zero concrete implementations, zero dependency on any domain module. This is what keeps the promise "the Document Engine never knows what an Invoice is" — concrete mappers (`InvoiceDocumentMapper`, etc.) are written by whoever depends on both `fin-document` and the source module (a later stage, or the consuming application).

## Built-in Renderers (Stage 1)

All five are pure data-serialization renderers — no template/brand/layout dependency, so they're safe to build before those engines exist and remain useful afterward (JSON export of a fully-composed document is still just JSON export):

- **JsonDocumentRenderer** — serializes `ComposedDocument` using Gen-Fin's existing serialization contracts (reuse, not reinvent — Group 20 in the full spec, but the JSON renderer needs it now so Stage 1 pulls in exactly that much of it).
- **PlainTextDocumentRenderer** — flattens sections/elements into readable plain text.
- **MarkdownDocumentRenderer** — same tree, Markdown headings/tables.
- **CsvDocumentRenderer** — flattens table-shaped elements into CSV rows; non-tabular elements (free text) are skipped with a documented convention, not an error.
- **XmlDocumentRenderer** — serializes the same tree as XML.

PDF and HTML are explicitly out of scope for Stage 1 (they need Template/Brand/Letterhead/Layout to be meaningful — building them now would mean rebuilding them in Stage 4 anyway).

## Error Handling

Renderer/resolver failures are typed (`RenderFailure`, plus a `RendererNotFoundException`-style unchecked failure only for genuine programmer error — resolving an unregistered `RendererId` — mirroring how other Gen-Fin SPIs distinguish expected-outcome failures from misconfiguration). No silent fallbacks: an unsupported `DocumentType`/element combination in a renderer (e.g. CSV renderer hitting a non-tabular element) is either explicitly skipped per that renderer's documented contract, or reported in `RenderResult`'s failure case — never dropped without a trace.

## Testing

- Unit tests per type: identity value semantics, `DocumentModel`/`DocumentComposer`/`ComposedDocument` construction and immutability, each built-in renderer against representative `DocumentModel` fixtures (including edge cases: empty sections, missing optional metadata, non-tabular elements hitting CSV).
- `DefaultRendererResolver`/`DefaultRendererRegistry` registration and resolution tests (found, not-found, duplicate registration policy).
- `NoOpDocumentComposer` passthrough test (output structurally equals input).
- Architecture test (`DocumentArchitectureTest`, mirrors `DunningArchitectureTest`): zero forbidden imports (Spring/DB/RabbitMQ/Redis/HTTP/cloud SDK/PDF-HTML libs), zero coupling to fin-invoice/fin-payment/fin-refund/fin-ledger/fin-pricing/fin-reconciliation/fin-dunning (Stage 1 depends on fin-api only), correct `api`/`port`/`internal` package structure, `internal` types never referenced outside the module.
- Build gate: `./gradlew :fin-document:build` green — compile, Spotless, PMD, Checkstyle, ArchUnit, JUnit, JaCoCo.

## Out of Scope for Stage 1 (roadmap only)

- **Stage 2 (Template Engine)**: custom minimal placeholder/section/fragment/inheritance resolver (no external templating dependency, per decision above), plugged into `DocumentComposer`.
- **Stage 3 (Brand + Letterhead)**: `Brand`/`BrandProfile`/`BrandAssets` and `Letterhead`/`LetterheadProvider` SPI — including the mandatory PDF-letterhead-overlay requirement (default PDF renderer backend: Apache PDFBox, chosen for its content-stream API's fit for drawing generated content onto an existing uploaded PDF page).
- **Stage 4 (Theme + Layout + Watermark + Signature + Header/Footer, + PDF/HTML renderers)**: this is where `PdfDocumentRenderer` and `HtmlDocumentRenderer` actually get built, once there's a composed, styled, laid-out document worth rendering visually.
- **Stage 5 (Placeholder + Export + Preview)**.
- **Stage 6 (Validation + Events + Configuration + Builders + full `DocumentExtensions`/`ExtensionRegistry` wiring + exhaustive test suite + `DOCUMENT.md`)**.

Each stage gets its own spec/plan cycle when reached, same process as this one.
