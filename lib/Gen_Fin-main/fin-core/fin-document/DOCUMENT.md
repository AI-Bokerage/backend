# fin-document

`fin-document` is a reusable Java Document Engine: templates, placeholder substitution, brand
profiles, letterhead overlay, QR codes, page layout, watermarks, PDF rendering (via Apache
PDFBox), preview, validation, configuration, and domain events. It is **never CPMS-, Invoice-,
Payment-, or Refund-aware** — it consumes only generic `ComposedDocument`/`DocumentModel` data
handed to it by whatever module (invoice, receipt, statement, dunning notice, etc.) needs a
document produced, and hardcodes no business document types, letterheads, or brand assets.

This document covers the package layout, the composition pipeline, templates/placeholders,
branding/letterhead/QR, layout/watermark, PDF rendering, preview, validation, configuration,
events, the SPI registration entry point, serialization, a usage example, integration guidance,
and the future roadmap of deliberately deferred features.

## Package layout

- `io.genfin.document.api.<concept>` — public model/value types: `identity` (all `Id` types),
  `model` (`DocumentModel`, `DocumentSection`, `DocumentElement`), `compose` (`ComposedDocument`),
  `result` (`RenderResult`, `PdfRenderInputs`), `exception` (typed exceptions), `placeholder`,
  `template`, `brand`, `letterhead`, `qr`, `layout`, `watermark`, `preview`, `validation`,
  `config`, `event`.
- `io.genfin.document.port` — SPI interfaces and factories: `DocumentRenderer`/`DocumentRenderers`,
  `RendererResolver`, `RendererConfiguration`, `DocumentComposer`, `TemplateEngine`/
  `TemplateEngines`, `PlaceholderResolver`/`PlaceholderResolvers`, `BrandResolver`/
  `BrandResolvers`, `LetterheadResolver`/`LetterheadResolvers`, `QrCodeResolver`/
  `QrCodeResolvers`, `PreviewRenderer`/`PreviewRenderers`, `DocumentValidator`/
  `DocumentValidators`, `DocumentEventListener`/`DocumentEventPublisher`/
  `DocumentEventPublishers`.
- `io.genfin.document.internal.<concern>` — default implementations, never exported from
  `module-info.java`.
- `io.genfin.document.spi.DocumentExtensions` — registers every default extension into an
  `ExtensionRegistry` in one call.

## The Document pipeline (Compose → Brand → Letterhead → Layout → Render)

An application builds a `DocumentModel`, then a `DocumentComposer` turns it into a
`ComposedDocument` by resolving its template (via `TemplateEngine`, substituting placeholders),
applying a `BrandProfile` (via `BrandResolver`), and folding in `Letterhead`/`Watermark`/QR
placement metadata. The composed document, together with a `RendererConfiguration` (page layout,
optional `PdfRenderInputs`), is handed to a `DocumentRenderer` resolved via `RendererResolver`.
Composition happens **before** rendering — a renderer never re-derives branding or layout, it only
paints what composition already decided.

## Templates and placeholders

`TemplateEngine.render(TemplateId, TemplateContext): List<DocumentSection>` resolves a registered
`DocumentTemplate` and substitutes `{{placeholder}}` tokens (including `{{#each}}` sections and
formatter pipes) using a hand-rolled parser/compiler — no external templating library. Applications
register templates via `TemplateEngines.standard()`'s `Bundle.register(DocumentTemplate)` and
placeholder sources via `PlaceholderResolvers.standard()`'s `Bundle.register(PlaceholderProvider)`.
fin-document ships no built-in templates or placeholder sources.

## Branding, letterhead, QR

`BrandProfile`, `Letterhead`, and `QrCodeContent` are open-value types (`of(...)` factory,
validated fields). Each has a matching `XProvider`/`XResolver` pair and a `XResolvers.standard()`
factory returning a `Bundle` (`register(...)` + `resolver()`), mirroring the Payment Provider
pattern used elsewhere in Gen-Fin. fin-document ships no built-in brand profiles, letterhead
images, or QR content — applications register their own via each bundle.

## Layout and watermark

`PageLayout` (paper size, orientation, margins, page numbering) and `Watermark` (label, opacity)
are plain immutable value types with builders — no port/internal split, since they carry no
pluggable behavior. `StandardPaperSize`/`StandardOrientation`/`StandardLetterheadFormat`/
`WatermarkLabel` follow the open-value-type pattern (interface + record + constants), so
applications can add their own paper sizes or letterhead formats without modifying this module.

## PDF rendering

The `pdf` renderer (`RendererId.of("pdf")`) is the only renderer that paints branding/letterhead/
layout/watermark visually; it is built on Apache PDFBox 3.0.7 (an `implementation`-scoped
dependency — PDFBox types never appear in any exported `api`/`port` signature). It composes six
internal collaborators: `PdfDocumentBuilder` (page geometry), `PdfContentPainter` (word-wrapped
body text, multi-page), `PdfImagePlacer` (logo/QR placement), `PdfWatermarkStamper` (diagonal
semi-transparent watermark), `PdfHeaderFooterPainter` (header/footer with `{n}`/`{total}` page
numbering via a two-pass render), and `PdfLetterheadOverlay` (PDF/PNG/JPEG letterhead background,
prepended per page).

**Known limitations** (documented, not fixed, to keep the v1 freeze on schedule):

- Letterhead aspect ratio is stretched to fit the target page when the letterhead page's size
  differs from the target's, rather than preserving the letterhead's own aspect ratio — visible
  distortion (observed up to ~9%) for mismatched page sizes. Workaround: use a letterhead sized to
  match the target page size.
- Letterhead PDF content held in PDF *annotations* (rather than the page's content stream) is
  dropped by the overlay — only content stream drawing operators are imported. Workaround: flatten
  annotation content into the page content stream before using it as a letterhead source.

## Preview

`PreviewRenderer.preview(ComposedDocument, RendererConfiguration, PreviewConfiguration):
DocumentPreview` is **not** a second rendering pipeline — it delegates to the same
`RendererResolver`-resolved renderer used for a real render, wrapped with preview-specific
metadata (`PreviewResult`'s `Optional<Integer> pageCount`, populated when the resolved renderer can
report it cheaply). `PreviewConfiguration.targetRenderer` defaults to `RendererId.of("pdf")`.
Obtain one via `PreviewRenderers.of(RendererResolver)`.

## Validation

`DocumentValidator.validate(ComposedDocument, RendererConfiguration, RendererId): ValidationResult`
is a lightweight pre-flight check — it does not render. It surfaces every problem found (renderer
not registered, unsupported letterhead format) as a `ValidationIssue` (`code()` + `message()`) in
one pass, rather than an application discovering the first problem only when a real render throws.
`ValidationResult.isValid()`/`valid()`/`withIssues(List<ValidationIssue>)`. Obtain a validator via
`DocumentValidators.of(RendererResolver)`.

## Configuration

`DocumentEngineConfiguration` is a plain immutable, builder-constructed holder of zero-config
application-wide defaults (`defaultRenderer` = `RendererId.of("json")`, `defaultPaperSize` = A4,
`defaultOrientation` = portrait, `defaultMargins` = none, `defaultPageNumbers` = disabled,
`previewEnabled`/`watermarkEnabled` = false). It is a convenience an application populates however
it likes (hardcoded, its own config file, environment variables) and references when constructing
per-document `RendererConfiguration`/`PdfRenderInputs` instances — it is never itself threaded
through a render call, so those two types remain the single source of truth for what a specific
render actually uses. No Spring, no YAML, no constants.

## Events

Seven `DocumentEvent` types (`DocumentRendered`, `PdfGenerated`, `BrandApplied`,
`LetterheadApplied`, `LayoutApplied`, `WatermarkApplied`, `QrCodeRendered`) are synchronous,
in-process domain events — never messaging. `DocumentEventListener` (SPI, extends `Extension`)
observes; `DocumentEventPublisher` (obtained via `DocumentEventPublishers.standard()`'s `Bundle`)
publishes. **Not wired into `PdfRenderer` automatically** — this stage builds and proves the event
types and publish/subscribe plumbing with direct tests; wiring `PdfRenderer` to actually publish
these events during a real render is a documented follow-up (see Future Roadmap). Until then, an
application gets equivalent observability by calling `DocumentValidator`/`PreviewRenderer` before a
render and comparing against the `DocumentRenderer` result after.

## Extension points (SPI)

`io.genfin.document.spi.DocumentExtensions.registerDefaults(ExtensionRegistry)` registers every
default extension point in one call, mirroring every other fin-* module's `spi.XExtensions`
convention:

| Extension point | Default implementation |
|---|---|
| `RendererResolver` | `DocumentRenderers.standard()` — all 6 built-in renderers (json/text/markdown/csv/xml/pdf) |
| `BrandResolver` | `BrandResolvers.standard().resolver()` — empty |
| `LetterheadResolver` | `LetterheadResolvers.standard().resolver()` — empty |
| `QrCodeResolver` | `QrCodeResolvers.standard().resolver()` — empty |
| `TemplateEngine` | `TemplateEngines.standard().engine()` — empty |
| `PlaceholderResolver` | `PlaceholderResolvers.standard().resolver()` — empty |
| `DocumentEventPublisher` | `DocumentEventPublishers.standard().publisher()` |
| `PreviewRenderer` | `PreviewRenderers.of(rendererResolver)` |
| `DocumentValidator` | `DocumentValidators.of(rendererResolver)` |

`ExtensionRegistry.register` accumulates (never overwrites) and `find()` returns the
**first-registered** implementation for a type. An application that wants its own brand,
letterhead, QR, template, or placeholder provider to take priority over the (empty) default must
call `registry.register(BrandResolver.class, myBundle.resolver())` (etc.) **before** calling
`DocumentExtensions.registerDefaults(registry)`.

## Serialization

fin-document has no separate serialization type or format. The `json` renderer
(`RendererId.of("json")`) *is* the serialization mechanism: rendering a `ComposedDocument` with
`RendererId.of("json")` produces its plain-Java-object serialization as bytes, through the same
renderer pipeline used for every other output format — no Spring, no external serialization
framework.

## Usage example

```java
ExtensionRegistry registry = ExtensionRegistries.create();
DocumentExtensions.registerDefaults(registry);

RendererResolver renderers = registry.find(RendererResolver.class).orElseThrow();
DocumentRenderer pdf = renderers.resolve(RendererId.of("pdf"));

ComposedDocument document = ...; // built by the calling module's own DocumentComposer usage
RendererConfiguration config = RendererConfiguration.defaults();

DocumentValidator validator = registry.find(DocumentValidator.class).orElseThrow();
ValidationResult validation = validator.validate(document, config, RendererId.of("pdf"));
if (!validation.isValid()) {
  throw new IllegalStateException("Invalid render request: " + validation.issues());
}

RenderResult result = pdf.render(document, config);
byte[] pdfBytes = result.content();
```

## How applications integrate — without touching CPMS/Invoice/Payment/Refund concepts

fin-document never imports or references any CPMS/Invoice/Payment/Refund type. The calling module
(e.g. `fin-invoice`) is responsible for mapping its own domain object into a generic
`DocumentModel` via its own dedicated mapper (e.g. `InvoiceDocumentMapper`, not built in this
module) before handing it to `DocumentComposer`. fin-document has no knowledge of what business
document it is producing — it only knows sections, placeholders, branding, and layout.

## Future Roadmap

The following were considered during Phase 12C and deliberately deferred to keep the v1 freeze on
schedule. None are built; each is listed with purpose, likely architecture, extension points, and
deferral reason.

- **`DocumentAttachment`/`AttachmentCollection`** — purpose: attach supplementary files (e.g. a
  supporting PDF) to a `ComposedDocument` without embedding them in the rendered output. Likely
  architecture: an `api.attachment` value type plus a `List<DocumentAttachment>` field on
  `ComposedDocument`, no new port (attachments are inert data, not pluggable behavior). Deferral
  reason: flagged in Stage 1 as needing its own stage; never picked up in Stages 2-7; not core to
  the FIN-SVC MVP.
- **Theme/Typography engine** — purpose: centralize font/color choices beyond per-`BrandProfile`
  fields. Likely architecture: a `Theme` value type resolved similarly to `BrandProfile`. Extension
  points: a `ThemeResolver` port. Deferral reason: `BrandProfile` already covers the MVP's branding
  needs; a dedicated theme layer is premature abstraction until a second visual axis is needed.
- **White-label branding** — purpose: swap an entire visual identity (logo + colors + fonts +
  letterhead) as one unit per tenant. Likely architecture: a `BrandProfile` extension bundling a
  `Theme` and default `Letterhead` together. Deferral reason: depends on Theme engine above.
- **DOCX / HTML / Excel / PowerPoint renderers** — purpose: additional output formats beyond
  today's json/text/markdown/csv/xml/pdf. Likely architecture: each is a new `DocumentRenderer`
  implementation registered into `DocumentRenderers.standard()`, no interface changes needed — the
  `DocumentRenderer` SPI already supports arbitrary output formats. Deferral reason: not required
  for the FIN-SVC MVP; the SPI makes each an additive, isolated follow-up.
- **Image (PNG/JPEG-as-primary-output) renderer** — purpose: render a document as a rasterized
  image rather than embedding images inside a PDF (today's only image use). Likely architecture:
  same as above, a new `DocumentRenderer` using PDFBox's `PDFRenderer` to rasterize the PDF
  renderer's own output. Deferral reason: no current consumer need.
- **Digital Signature** — purpose: cryptographically sign rendered PDFs. Likely architecture: a
  `DocumentSigner` port wrapping PDFBox's signature APIs, applied as a post-render step. Deferral
  reason: requires a certificate/key-management story out of scope for the Document Engine itself.
- **Document Encryption** — purpose: password-protect or encrypt rendered PDFs. Likely
  architecture: a `DocumentEncryptor` port wrapping PDFBox's `ProtectionPolicy` APIs. Deferral
  reason: no current consumer need; same key-management scoping concern as signatures.
- **Rich Component Library** — purpose: higher-level reusable document elements (tables with
  styling, charts) beyond today's `DocumentElement` visitor set. Likely architecture: additional
  `DocumentElementVisitor`-dispatched element types. Deferral reason: today's element set covers
  the MVP; richer components are additive via the existing visitor pattern.
- **Advanced Template Inheritance** — purpose: templates that extend/override a parent template's
  sections. Likely architecture: a `parentTemplateId` field on `DocumentTemplate` resolved
  recursively by `TemplateEngine`. Deferral reason: not required for the MVP's flat template model.
- **Rich CSS Layout** — purpose: CSS-like flexible layout (flexbox/grid) instead of today's
  top-to-bottom flow layout. Likely architecture: would require an HTML-based renderer (see above)
  as a prerequisite. Deferral reason: depends on HTML renderer; no current consumer need.
- **Multi-brand Organizations** — purpose: a single application context resolving different
  `BrandProfile`s per sub-organization/tenant. Likely architecture: a keyed `BrandResolver` variant
  (resolve by tenant id, not just brand id) or an application-level `Map<TenantId, BrandId>`
  outside this module. Deferral reason: `BrandResolver` already supports registering multiple
  profiles; per-tenant selection is an application concern, not core engine work, until proven
  otherwise.
- **Advanced Export Formats** — purpose: bundling multiple rendered documents (e.g. a zip of PDFs).
  Likely architecture: an application-level concern composing multiple `RenderResult`s; not a new
  engine capability. Deferral reason: no current consumer need.
- **Document Versioning** — purpose: track revisions of a rendered/composed document over time.
  Likely architecture: a `DocumentVersion` wrapper with a `previousVersion` reference, likely
  living in the calling module (e.g. `fin-invoice`) rather than fin-document itself, since
  versioning semantics are domain-specific. Deferral reason: not core to the Document Engine; no
  current consumer need.
- **Approval Workflow** — purpose: gate document finalization behind an approval step. Likely
  architecture: belongs in the calling module's own lifecycle (documents are stateless render
  requests here, not long-lived aggregates with workflow state). Deferral reason: out of scope for
  a stateless rendering engine.
- **Electronic Seal Support** — purpose: apply a regulatory electronic seal (distinct from a
  digital signature) to a rendered document. Likely architecture: similar to Digital Signature
  above, a `DocumentSealer` port. Deferral reason: requires jurisdiction-specific seal formats out
  of scope for v1; no current consumer need.
- **Wiring event publication into `PdfRenderer`** — purpose: automatically publish the seven
  `DocumentEvent` types during a real render, rather than requiring an application to call
  `DocumentEventPublisher.publish(...)` itself. Likely architecture: an additive
  `Optional<DocumentEventPublisher>` field on `RendererConfiguration` (mirroring the
  `PdfRenderInputs` precedent), consulted by `PdfRenderer.render(...)`. Deferral reason: event
  types + SPI plumbing were proven correct in Stage 7 with direct unit tests; wiring them into the
  render path is a separable, additive follow-up.
