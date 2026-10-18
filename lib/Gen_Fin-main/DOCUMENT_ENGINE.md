# Document Engine (`fin-document`)

A reusable Document Engine — templates, placeholder substitution, brand profiles, letterhead
overlay, QR codes, page layout, watermarks, PDF rendering (Apache PDFBox), preview, validation,
configuration, and domain events. Never CPMS-, Invoice-, Payment-, or Refund-aware: it consumes
only generic `ComposedDocument`/`DocumentModel` data, hardcodes no business document types,
letterheads, or brand assets. Depends only on `fin-api`.

**Full documentation:** [`fin-core/fin-document/DOCUMENT.md`](fin-core/fin-document/DOCUMENT.md)
— package layout, the Compose → Brand → Letterhead → Layout → Render pipeline, templates,
branding, PDF rendering internals, preview, validation, configuration, events, SPI registration,
serialization, a usage example, and the Future Roadmap.

## At a glance

| Concern | Key types |
|---|---|
| Composition | `DocumentModel`, `DocumentComposer`, `ComposedDocument` |
| Templates/placeholders | `TemplateEngine`, `DocumentTemplate`, `PlaceholderProvider` |
| Branding | `BrandProfile`, `BrandResolver` |
| Letterhead/QR | `Letterhead`, `LetterheadProvider`, `QrCodeContent`, `QrCodeProvider` |
| Layout/watermark | `PageLayout`, `Watermark` |
| Rendering | `DocumentRenderer`, `RendererResolver` (json/text/markdown/csv/xml/pdf built in) |
| Preview/validation | `PreviewRenderer`, `DocumentValidator` |
| SPI registration | `io.genfin.document.spi.DocumentExtensions.registerDefaults(registry)` |

## Known limitations (v1)

- Letterhead aspect ratio is stretched (not preserved) when the letterhead page size differs from
  the target page size — use a same-size letterhead to avoid this.
- Letterhead content held in PDF *annotations* (rather than the page content stream) is dropped by
  the overlay.
- No public extension point to add a 7th built-in-style renderer to `DocumentRenderers.standard()`
  — `fin-spring-boot-starter` works around this at the Spring layer only (see
  [SPRING.md](SPRING.md)'s "Adding a custom document renderer").
- No dedicated `InvoiceDocumentMapper` — every consumer maps its own domain object to
  `DocumentModel` (see [INTEGRATION.md](INTEGRATION.md)). All primitives needed to do so
  (`DocumentSection`, `TableElement`, `KeyValueElement`, `TextBlockElement`) are public and
  sufficient for a realistic invoice layout.

## See also

[ARCHITECTURE.md](ARCHITECTURE.md), [EXTENSIONS.md](EXTENSIONS.md), [INTEGRATION.md](INTEGRATION.md),
[SPRING.md](SPRING.md).
