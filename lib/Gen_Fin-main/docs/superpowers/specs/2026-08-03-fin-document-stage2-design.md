# fin-document — Stage 2 Design: Template Engine + Placeholder Engine

Date: 2026-08-03
Status: Approved for planning
Part of: Phase 12B — CPMS Document Engine (Minimal Enterprise Profile), Stage 2 of 7 (Stages 2-8)

## Context

Stage 1 (identity, `DocumentModel`, `DocumentComposer`/`NoOpDocumentComposer`, `DocumentRenderer` SPI + 5 built-in renderers) is complete and frozen — this stage does not modify any Stage 1 public type. Phase 12B adds the remaining generic capabilities needed for a consuming application (CPMS FIN-SVC or any other) to compose enterprise documents. This module must never reference CPMS/FIN-SVC/Invoice/Project/Tenant/Milestone — those are the consuming application's concepts; this module only ever sees generic `DocumentModel`s, templates, and placeholders.

Stage 2 delivers the **Template Engine** (Group 1) and **Placeholder Engine** (Group 2): a lightweight, hand-rolled `{{placeholder}}` / `{{#if}}` / `{{#each}}` / `{{include}}` / inheritance template language (no Mustache/Handlebars/Thymeleaf/FreeMarker/Velocity, no scripting or expression language), and the generic key-resolution engine that supplies the values templates substitute.

## Where This Plugs Into the Pipeline

```
DocumentModel
     │
     ▼
DocumentComposer   ← Stage 2 adds TemplateDocumentComposer (new), NoOpDocumentComposer unchanged
     │
     ▼
ComposedDocument
     │
     ▼
DocumentRenderer SPI (unchanged — renderer never resolves placeholders, never knows templates)
```

`DocumentComposers` (Stage 1 factory) gets one new additive method: `DocumentComposers.templated(TemplateEngine engine): DocumentComposer` (name illustrative; exact signature decided at implementation time) returning a `TemplateDocumentComposer` that, when a `DocumentModel`'s attributes carry a `templateId` key, renders that template and merges the result into the model's sections before producing `ComposedDocument`. `DocumentComposers.noOp()` is untouched. No existing Stage 1 signature changes.

## Package Layout

```
io.genfin.document
├── api
│   ├── template       DocumentTemplate, TemplateContext, TemplateVariables, CompiledTemplate,
│   │                  TemplateFragment (+ LiteralFragment, PlaceholderFragment, IfFragment,
│   │                  EachFragment, IncludeFragment), TemplateSection
│   └── placeholder    Placeholder, PlaceholderValue (+ ScalarPlaceholderValue, ListPlaceholderValue,
│                      MissingPlaceholderValue), PlaceholderContext
├── port
│   ├── TemplateEngine          (single entry point applications call)
│   ├── TemplateResolver
│   ├── PlaceholderProvider     (SPI — extends io.genfin.api.port.spi.Extension)
│   ├── PlaceholderResolver
│   └── PlaceholderFormatter    (SPI — extends Extension)
└── internal
    ├── template
    │   ├── DefaultTemplateRegistry, DefaultTemplateResolver
    │   ├── TemplateParser        (hand-rolled {{...}} tokenizer/parser, no external dependency)
    │   ├── TemplateCompiler      (resolves parent chain + section overrides, produces CompiledTemplate)
    │   ├── DefaultTemplateEngine (implements TemplateEngine, renders CompiledTemplate → List<DocumentSection>)
    │   └── TemplateDocumentComposer (implements DocumentComposer)
    └── placeholder
        ├── DefaultPlaceholderRegistry, DefaultPlaceholderResolver
```

## Core Types

**`Placeholder`** (api.placeholder) — a value object wrapping a dotted key, e.g. `Placeholder.of("invoice.number")`. Used by the parser to represent a `{{...}}` token; not itself resolved.

**`PlaceholderValue`** (api.placeholder) — the resolved value of a placeholder. Three variants dispatched via `accept(PlaceholderValueVisitor<R>)` (no `instanceof`/`switch`, per the module-wide ban):
- `ScalarPlaceholderValue(String value)` — a single string, used by `{{placeholder}}` substitution and `{{#if}}` truthiness (empty/blank string is falsy).
- `ListPlaceholderValue(List<PlaceholderContext> items)` — a list of per-item contexts, used by `{{#each}}`.
- `MissingPlaceholderValue` (singleton) — key not resolvable by any provider; falsy for `{{#if}}`, renders as empty string for `{{placeholder}}` (never throws — a missing placeholder is a normal, expected outcome, not a validation failure at this stage; Stage 6's Validation group adds the "missing placeholder" diagnostic on top of this base behavior).

**`PlaceholderProvider`** (port, SPI, extends `Extension`) — `boolean supports(String key)`; `PlaceholderValue resolve(String key)`. Applications register one provider per domain concept (e.g. an app-side `InvoicePlaceholderProvider` supplying `invoice.*` keys) — zero providers ship in this module, per "no hardcoded placeholders."

**`PlaceholderRegistry`** (internal) — ordered list of registered `PlaceholderProvider`s. `PlaceholderResolver` (port) — `PlaceholderValue resolve(String key)`, delegating to the registry: first provider whose `supports(key)` is true wins.

**`PlaceholderFormatter`** (port, SPI, extends `Extension`) — `String format(String rawValue)`. Applications may register a formatter per placeholder key (e.g. currency/date formatting); if none is registered for a key, the raw resolved string is used unchanged.

**`PlaceholderContext`** (api.placeholder) — the object templates render against. Wraps a `PlaceholderResolver` plus a local override map (for loop-scoped bindings like the `item` in `{{#each items}}...{{item.name}}...{{/each}}`) plus registered formatters. `resolve(String key): PlaceholderValue` checks local overrides first, then falls back to the wrapped resolver. `withLocal(String name, PlaceholderValue value): PlaceholderContext` returns a new child context layering one override — this is how `{{#each}}` binds each iteration's item without mutating shared state.

**Template fragments** (api.template) — the parsed, pre-substitution tree, each dispatched via `accept(TemplateFragmentVisitor<R>)`:
- `LiteralFragment(String text)` — raw text between tags.
- `PlaceholderFragment(Placeholder placeholder)` — a `{{key}}` token.
- `IfFragment(Placeholder condition, List<TemplateFragment> body)` — `{{#if key}}...{{/if}}`; body included only if `condition` resolves truthy in the current context.
- `EachFragment(Placeholder collection, List<TemplateFragment> body)` — `{{#each key}}...{{/each}}`; body repeated once per item in the resolved `ListPlaceholderValue`, with the context scoped via `withLocal`.
- `IncludeFragment(TemplateId includedTemplateId)` — `{{include "otherTemplateId"}}`; resolved and inlined at compile time (not render time), so a cyclic include chain is a compile-time error (`ValidationException`), not infinite recursion.

**`TemplateSection`** (api.template) — a named, overridable block: `{{#section "name"}}...{{/section}}`. Used for inheritance: a `DocumentTemplate` may declare a `parentId`; at compile time, `TemplateCompiler` resolves the parent's `CompiledTemplate`, then replaces any parent section whose name matches one the child declares, leaving all other parent content untouched. A child template with no matching sections for a given parent renders the parent unchanged (sections are optional overrides, not required declarations).

**`DocumentTemplate`** (api.template) — the registrable, uncompiled unit: `TemplateId id`, raw source `String`, optional parent `TemplateId`. `TemplateRegistry`/`TemplateResolver` are the registration/lookup pair (mirrors Stage 1's `DocumentRenderers`/`RendererResolver` shape): applications register `DocumentTemplate` instances; `TemplateResolver.resolve(TemplateId): CompiledTemplate` returns the compiled, cached form (`TemplateCompiler` compiles once per `TemplateId`, cached inside the registry — a `DocumentTemplate` registered after another already referenced it as a parent triggers recompilation of dependents on next resolve, not eager invalidation propagation, since Stage 2 has no template-hot-reload requirement).

**`CompiledTemplate`** (api.template) — `TemplateId id`, the fully resolved fragment tree (parent sections merged, includes inlined). Immutable, safe to cache and reuse across renders.

**`TemplateVariables`** (api.template) — a minimal immutable local-binding map (`String` key → `PlaceholderValue`), used internally by `PlaceholderContext.withLocal` and exposed as its own type per the brief's explicit list; kept separate from `PlaceholderContext` because a `TemplateContext` may want to seed initial variables before any resolution happens (e.g. `TemplateContext.of(PlaceholderResolver resolver, TemplateVariables initialVariables)`).

**`TemplateContext`** (api.template) — what applications pass into `TemplateEngine.render(...)`: wraps a `PlaceholderResolver` (or a ready-made `PlaceholderContext`) plus optional `TemplateVariables`. Constructing a `PlaceholderContext` from a `TemplateContext` is an internal rendering-time step.

**`TemplateEngine`** (port) — the single entry point applications interact with, per the brief ("Applications interact only through `TemplateEngine`"): `List<DocumentSection> render(TemplateId templateId, TemplateContext context)`. Internally: resolves the `CompiledTemplate` via `TemplateResolver`, walks its fragment tree substituting placeholders against a `PlaceholderContext` derived from `context`, and produces Stage 1 `DocumentSection`/`DocumentElement` values as output — **reusing Stage 1's model types rather than inventing a parallel structured-content model**, so the rest of the pipeline (composer, renderers) needs no new integration surface. `LiteralFragment`/`PlaceholderFragment` sequences within a section become `TextBlockElement`/`KeyValueElement` content; `EachFragment` bodies repeat as grouped elements within the section.

**`TemplateDocumentComposer`** (internal) — implements `DocumentComposer`. Reads a `templateId` attribute from the incoming `DocumentModel` (via `DocumentAttributes`); if absent, behaves identically to `NoOpDocumentComposer` (pure passthrough); if present, calls `TemplateEngine.render(...)` and merges/replaces the model's sections with the rendered output before producing `ComposedDocument`. Exposed via a new `DocumentComposers` factory method — additive, no existing method's behavior or signature changes.

## Parser Design (internal, no external dependency)

`TemplateParser` is a hand-written single-pass scanner over the raw template string: it looks for `{{` / `}}` delimiters, classifies each tag by its leading token (`#if`, `/if`, `#each`, `/each`, `#section`, `/section`, `include`, or a bare key for a placeholder), and builds a flat token stream. `TemplateCompiler` then turns that flat stream into the nested `TemplateFragment` tree (matching `#if`/`/if` and `#each`/`/each` pairs, erroring via `ValidationException` on unmatched tags — a parse error is a construction-time failure, never silently ignored). No regex-based expression evaluation, no arithmetic, no method calls inside `{{ }}` — a tag is either a bare dotted key or one of the five fixed directives.

## Error Handling

- Parse errors (unmatched `#if`/`#each`, malformed `{{`), cyclic includes, and missing parent templates are `ValidationException` at compile time (via `TemplateCompiler`) — never silent, never deferred to render time.
- A placeholder key with no matching provider resolves to `MissingPlaceholderValue` at render time — not an error (per Stage 2's scope; Stage 6 adds an opt-in strict-validation pass on top).
- `TemplateResolver.resolve()` on an unregistered `TemplateId` throws an unchecked "not found" exception, mirroring Stage 1's `RendererNotFoundException` pattern (a genuine caller error, not an expected outcome).

## Testing

- Parser/compiler unit tests: literal-only, single placeholder, nested `{{#if}}`, nested `{{#each}}` (including empty list → zero repetitions), `{{include}}` inlining, section-override inheritance (child overrides one of two parent sections, other parent section untouched), and every parse-error path (unmatched tag, cyclic include, unknown parent).
- Placeholder engine unit tests: provider resolution (found/missing), formatter application, local-override shadowing a provider-resolved key, `PlaceholderValue` visitor dispatch for all three variants.
- `TemplateEngine.render(...)` integration tests producing `DocumentSection`/`DocumentElement` output, verified against Stage 1's existing renderers (e.g. render a template, feed the resulting sections through `NoOpDocumentComposer` → `JsonDocumentRenderer`, assert the final JSON contains the substituted values) — this is the seam-correctness proof for the whole stage.
- `TemplateDocumentComposer` test: model without `templateId` behaves exactly like `NoOpDocumentComposer`; model with `templateId` produces rendered sections.
- Architecture test extension: new packages join the existing `DocumentArchitectureTest`'s package-structure/forbidden-import/no-instanceof-switch/cycle checks — no new architecture test class, extend the existing one's scope.

## Out of Scope for Stage 2

Branding, Letterhead, QR (Stage 3); Layout, Watermark (Stage 4); PDF rendering (Stage 5); Preview/Validation/Configuration/Builders (Stage 6); Events/Serialization/SPI registration (Stage 7); exhaustive testing sweep + `DOCUMENT.md` (Stage 8). No PDFBox or any rendering-library dependency is introduced in this stage.
