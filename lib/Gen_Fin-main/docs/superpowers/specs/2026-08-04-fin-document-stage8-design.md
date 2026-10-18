# fin-document — Stage 8 Design: SPI Registration + Serialization Clarification + Testing Sweep + DOCUMENT.md

Date: 2026-08-04
Status: Approved for planning
Part of: Phase 12C — Document Engine Completion (MVP for FIN-SVC), Stage 8 of 8 (final stage before v1 freeze)

## Context

Stages 1-7 are complete, reviewed, and committed. This is the last stage before the whole Document Engine gets squashed into one final commit. It closes three remaining items from the original Groups 1-15 brief (Serialization, SPI Registration, exhaustive Testing) plus the mandatory `DOCUMENT.md`, and explicitly defers one discovered gap (`DocumentAttachment`/`AttachmentCollection`, Group 12) to the Future Roadmap section rather than building it — approved by the user to keep the freeze on schedule.

## Group 12 — Attachments (deferred, not built)

`DocumentAttachment`/`AttachmentCollection` was flagged in the Stage 1 design as needing "its own stage" and was never picked up in Stages 2-7. Per explicit user decision, this stage does **not** build it. It is documented only, in `DOCUMENT.md`'s Future Roadmap section (purpose, likely architecture, extension points, deferral reason), alongside the other already-known-deferred features (Theme/Typography, White-label, DOCX/HTML/Excel/PowerPoint/Image renderers, Digital Signature, Document Encryption, Rich Component Library, Advanced Template Inheritance, Rich CSS Layout, Multi-brand Orgs, Advanced Export Formats, Document Versioning, Approval Workflow, Electronic Seal Support).

## Group 12 (renumbered in later stage docs as "Serialization") — clarified

Research into the codebase confirms there is no separate "serialization" concept missing from fin-document: Stage 1's `JsonDocumentRenderer` already IS the serialization path (a `ComposedDocument` renders to JSON bytes via the existing renderer pipeline — this is the SDK's actual "no Spring, no framework, plain Java" serialization story, consistent with the brief's "no Spring, no YAML, no constants" constraint applied elsewhere). No new type is added for this stage. This is a documentation-only closure: `DOCUMENT.md` states explicitly that `RendererId.of("json")` is the serialization mechanism, so nobody re-derives this later.

## Group 11 (Events) SPI note

Already complete from Stage 7 — no work here.

## SPI Registration — `DocumentExtensions`

Every other fin-* module (fin-pricing, fin-ledger, fin-dunning, fin-invoice, fin-refund, fin-reconciliation, fin-payment, fin-money, fin-provider-api) has a `spi/XExtensions` final utility class with a single `static void registerDefaults(ExtensionRegistry registry)` method, registering every default-implementation extension point the module ships, using the shared `io.genfin.api.spi.ExtensionRegistry` (`<T> void register(Class<T>, T)`, already exists in fin-api, not built this stage). `PricingExtensions` (`fin-core/fin-pricing/src/main/java/io/genfin/pricing/spi/PricingExtensions.java`) is the exact template to mirror.

`DocumentExtensions` (new: `io.genfin.document.spi.DocumentExtensions`, package `spi` — a new top-level package alongside `api`/`port`/`internal`, matching every sibling module's `X.spi` package placement, not nested under `api`/`port`/`internal`) registers:

- `RendererResolver.class` → `DocumentRenderers.standard()` (registers all 6 built-in renderers: json/text/markdown/csv/xml/pdf)
- `BrandResolver.class` → `BrandResolvers.standard().resolver()` (registered empty — fin-document ships no built-in brand profiles, application populates via the bundle's `register(...)`)
- `LetterheadResolver.class` → `LetterheadResolvers.standard().resolver()` (empty, same reasoning)
- `QrCodeResolver.class` → `QrCodeResolvers.standard().resolver()` (empty, same reasoning)
- `TemplateEngine.class` → `TemplateEngines.standard()`
- `PlaceholderResolver.class` → `PlaceholderResolvers.standard().resolver()` (empty — no built-in placeholder sources; formatters are a separate, already-standard concern)
- `DocumentEventPublisher.class` → `DocumentEventPublishers.standard().publisher()`
- `PreviewRenderer.class` → confirmed: `DefaultPreviewRenderer` has a package-private constructor `DefaultPreviewRenderer(RendererResolver)`, `internal.preview`, no public factory exists yet. This stage adds `PreviewRenderers.of(RendererResolver): PreviewRenderer` in `port` (mirrors fin-pricing's dependent-factory convention, e.g. `CommercialRuleEngines.of(ruleRegistry)` — a plain `of(dependency)` factory, not a zero-arg `standard()`, since this type has a real required collaborator). `DocumentExtensions` calls `PreviewRenderers.of(rendererResolver)`.
- `DocumentValidator.class` → confirmed: `DefaultDocumentValidator` has a package-private constructor `DefaultDocumentValidator(RendererResolver)`, `internal.validation`, no public factory exists yet. This stage adds `DocumentValidators.of(RendererResolver): DocumentValidator` in `port`, same convention. `DocumentExtensions` calls `DocumentValidators.of(rendererResolver)`.

Confirmed: `BrandResolvers.standard()` (and identically `LetterheadResolvers`, `QrCodeResolvers`, `PlaceholderResolvers`) returns a `Bundle` interface with exactly two members — `void register(X)` and `XResolver resolver()` — no other public surface. Following `PricingExtensions`'s exact convention (it registers the *resolved* type, e.g. `CatalogRegistry.class → catalogRegistry`, never a wrapper "Bundle" type), `DocumentExtensions.registerDefaults` registers each `Bundle`'s `.resolver()` under its `XResolver` port interface (`BrandResolver.class`, `LetterheadResolver.class`, `QrCodeResolver.class`, `PlaceholderResolver.class`) — matching the list above exactly, no separate `Bundle` registration. An application that needs to add its own brand/letterhead/QR/placeholder source calls `BrandResolvers.standard()` (etc.) itself, keeps the `Bundle` reference, and passes `.resolver()` to `DocumentExtensions` — OR, since `DocumentExtensions.registerDefaults` constructs its own empty bundles internally (matching `PricingExtensions`'s self-contained `registerDefaults(registry)` with no extra parameters), an application registers additional providers by calling `registry.find(BrandResolver.class)` after `registerDefaults` and checking whether the resolver instance supports a provider-registration API — it does not (only the `Bundle` does). Confirmed from `DefaultExtensionRegistry`: `register` accumulates (never overwrites) and `find()` returns the *first-registered* implementation for a type. Resolution: `DocumentExtensions.registerDefaults(ExtensionRegistry registry)` keeps the exact `PricingExtensions` zero-extra-parameter signature; an application that wants its own brand/letterhead/QR/placeholder provider to win must call `registry.register(BrandResolver.class, myBundle.resolver())` **before** calling `DocumentExtensions.registerDefaults(registry)`, so its registration is first and `find()` returns it ahead of the empty default. `DOCUMENT.md`'s SPI section states this ordering requirement explicitly.

## Testing Sweep

`find fin-core/fin-document/src/test -name "*.java" | wc -l` = 75 test files against ~130 main files across `api`+`port`+`internal`. No package is glaringly untested (every `api.*`/`internal.*` package with main files has at least one sibling test file); this stage's testing work is a **verification sweep**, not a bulk-authoring effort:

1. Run full `./gradlew :fin-document:test :fin-document:jacocoTestReport` and read the coverage report; flag (do not necessarily fix) any class below ~70% branch coverage that isn't a trivial value type accessor.
2. Add tests only for the new `DocumentExtensions.registerDefaults(...)` (one test: construct a fresh `ExtensionRegistry`, call `registerDefaults`, assert every one of the 8 extension-point types above resolves via `registry.find(...)`).
3. No other new test authoring planned — if the coverage report surfaces a real gap (a branch with zero coverage, not just a low percentage on a getter-heavy class), add one targeted test for it as a documented finding, not a blanket "improve coverage" pass.

## `DOCUMENT.md`

Location: `fin-core/fin-document/DOCUMENT.md` (module root, mirrors `fin-core/fin-ledger/LEDGER.md`, `fin-core/fin-pricing/PRICING.md`, `fin-core/fin-dunning/DUNNING.md`, `fin-core/fin-reconciliation/RECONCILIATION.md` placement and heading style exactly).

Heading structure (mirroring `LEDGER.md`'s structure, adapted to this module's actual shape):

```
# fin-document
## Package layout
## The Document pipeline (Compose → Brand → Letterhead → Layout → Render)
## Templates and placeholders
## Branding, letterhead, QR
## Layout and watermark
## PDF rendering
## Preview
## Validation
## Configuration
## Events
## Extension points (SPI)
## Serialization
## Usage example
## How applications integrate — without touching CPMS/Invoice/Payment/Refund concepts
## Future Roadmap
```

The **Future Roadmap** section is mandatory per the original Phase 12C brief and must cover, for each item, purpose / likely architecture / extension points / deferral reason: Theme/Typography engine, White-label branding, DOCX renderer, HTML renderer, Excel renderer, PowerPoint renderer, Image (PNG/JPEG-as-primary-output, distinct from today's PDF-embeds-images) renderer, Digital Signature, Document Encryption, Rich Component Library, Advanced Template Inheritance, Rich CSS Layout, Multi-brand Organizations, Advanced Export Formats, Document Versioning, Approval Workflow, Electronic Seal Support, and `DocumentAttachment`/`AttachmentCollection` (Group 12, this stage's deferral).

The two documented-but-not-fixed PDFBox limitations from Stage 6 (aspect-ratio distortion when letterhead page size ≠ target size; annotation-borne letterhead content dropped) belong in the PDF rendering section as **known limitations**, not the Future Roadmap (they're bugs in the current implementation's edge-case handling, not unbuilt features).

## Out of Scope for Stage 8

Building `DocumentAttachment`/`AttachmentCollection` (deferred to Future Roadmap per user decision). Any new renderer, brand/letterhead/QR provider, or template feature. Wiring event publication into `PdfRenderer` (already documented as deferred in Stage 7's design; restated in `DOCUMENT.md`, not re-implemented). Bulk test-authoring beyond the targeted gaps found by the coverage report.
