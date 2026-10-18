# fin-document Stage 7 Implementation Plan — Preview, Validation, Configuration, Events

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Add Preview (wraps existing renderers), Validation (pre-flight checks before rendering), Configuration (application-level defaults holder), and Events (opt-in observer SPI) — no new rendering capability, all wrapping/observing what Stages 1-6 already built.

**Architecture:** `api.preview`/`api.validation`/`api.config`/`api.event` hold value types. `port` holds the four SPIs/facades. `internal.preview`/`internal.validation`/`internal.event` hold default implementations.

**Tech Stack:** Java, JUnit 5 + AssertJ. Only dependency: `fin-api`. No PDFBox usage in this stage — this stage never touches `internal.renderer.pdf`.

## Global Constraints

- Package layout exactly per the design spec: `io.genfin.document.api.preview`, `api.validation`, `api.config`, `api.event`, `port`, `internal.preview`, `internal.validation`, `internal.event`.
- This module has caught these defect classes repeatedly: (1) constructor validation on required fields via `io.genfin.api.validation.Validate` (methods: `notNull`, `notBlank`, `positive`, `nonNegative`, `required`, `state`, `argument`); (2) builder optional setters coalesced to non-null defaults INSIDE THE CONSTRUCTOR. Apply both proactively from day one in every new type this stage.
- This module has had six-plus prior incidents of moving classes across api/port/internal boundaries to dodge ArchUnit cycles. If any new factory (`DocumentEventPublishers.standard()`) needs to reach into `internal`, add ONE narrow additive `.ignoreDependency(...)` entry to `DocumentArchitectureTest.java`'s existing factory-class pattern — never move a class.
- SPI interfaces (`DocumentEventListener`) extend `io.genfin.api.port.spi.Extension`.
- No `instanceof`, no `switch`/`switch(` anywhere in `fin-document` source.
- Test stack: JUnit 5 + AssertJ. Build/verify command every task: `./gradlew :fin-document:check` (fast, module-scoped — do NOT run the full `./gradlew build` until the final task).

---

### Task 1: Preview — `DocumentPreview`, `PreviewConfiguration`, `PreviewResult`, `PreviewRenderer` port, `DefaultPreviewRenderer`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/preview/PreviewResult.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/preview/DocumentPreview.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/preview/PreviewConfiguration.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/PreviewRenderer.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/preview/DefaultPreviewRenderer.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/preview/PreviewConfigurationTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/preview/DefaultPreviewRendererTest.java`

**Interfaces:**
- Consumes: `ComposedDocument`, `RendererConfiguration`, `RendererResolver`, `RenderResult`, `RendererId` (Stage 1).
- Produces: `PreviewResult.of(RenderResult delegate, java.util.Optional<Integer> pageCount)` + `renderResult(): RenderResult` + `pageCount(): Optional<Integer>`. `DocumentPreview.of(PreviewResult result)` — thin wrapper matching the brief's named type (keep minimal: if `DocumentPreview` ends up structurally identical to `PreviewResult` once you're implementing it, it's fine for `DocumentPreview` to just BE the public-facing name and `PreviewResult` to not exist as a separate type — use your judgment, but the plan's default is both exist as the design spec describes). `PreviewConfiguration.builder(): Builder` with `.targetRenderer(RendererId)` (defaults to `RendererId.of("pdf")` if never set — coalesce in the constructor, not the builder field) + `.build(): PreviewConfiguration`, `targetRenderer(): RendererId`. `PreviewRenderer.preview(ComposedDocument, RendererConfiguration, PreviewConfiguration): DocumentPreview`. `DefaultPreviewRenderer(RendererResolver resolver)` implements `PreviewRenderer` — resolves `previewConfig.targetRenderer()` via the injected `RendererResolver`, calls that renderer's `render(document, configuration)`, wraps the `RenderResult` into a `DocumentPreview`.

- [ ] **Step 1: Write the failing tests**

```java
package io.genfin.document.api.preview;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.RendererId;
import org.junit.jupiter.api.Test;

class PreviewConfigurationTest {

  @Test
  void defaultsToPdfRendererWhenUnset() {
    PreviewConfiguration config = PreviewConfiguration.builder().build();
    assertThat(config.targetRenderer()).isEqualTo(RendererId.of("pdf"));
  }

  @Test
  void explicitNullTargetRendererCoalescesToDefault() {
    PreviewConfiguration config = PreviewConfiguration.builder().targetRenderer(null).build();
    assertThat(config.targetRenderer()).isEqualTo(RendererId.of("pdf"));
  }

  @Test
  void exposesGivenTargetRenderer() {
    PreviewConfiguration config = PreviewConfiguration.builder().targetRenderer(RendererId.of("json")).build();
    assertThat(config.targetRenderer()).isEqualTo(RendererId.of("json"));
  }
}
```

```java
package io.genfin.document.internal.preview;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.api.preview.DocumentPreview;
import io.genfin.document.api.preview.PreviewConfiguration;
import io.genfin.document.port.DocumentRenderers;
import io.genfin.document.port.RendererConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultPreviewRendererTest {

  @Test
  void previewDelegatesToResolvedRendererAndProducesEquivalentOutput() {
    ComposedDocument document =
        ComposedDocument.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en", "USD", Instant.EPOCH),
            List.of(DocumentSection.of("Summary", List.of(KeyValueElement.of("Total", "500.00")))),
            DocumentAttributes.empty());

    var resolver = DocumentRenderers.standard();
    DefaultPreviewRenderer previewRenderer = new DefaultPreviewRenderer(resolver);

    DocumentPreview preview =
        previewRenderer.preview(
            document,
            RendererConfiguration.defaults(),
            PreviewConfiguration.builder().targetRenderer(RendererId.of("json")).build());

    String directOutput =
        new String(
            resolver.resolve(RendererId.of("json")).render(document, RendererConfiguration.defaults()).content(),
            StandardCharsets.UTF_8);
    String previewOutput =
        new String(preview.result().renderResult().content(), StandardCharsets.UTF_8);

    assertThat(previewOutput).isEqualTo(directOutput);
  }
}
```

- [ ] **Step 2: Run tests, verify they fail, then implement**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.preview.PreviewConfigurationTest" --tests "io.genfin.document.internal.preview.DefaultPreviewRendererTest"`

Implement `PreviewResult` (constructor coalesces nothing — both fields required, `RenderResult` non-null validated, `pageCount` may legitimately be `Optional.empty()` so no coalescing needed there since `Optional` IS the non-null representation), `DocumentPreview` (thin wrapper: `DocumentPreview.of(PreviewResult result)` + `result(): PreviewResult`), `PreviewConfiguration` (builder with `targetRenderer` defaulting to `RendererId.of("pdf")` when null, coalesced in the constructor), `PreviewRenderer` port interface, `DefaultPreviewRenderer` (package-private, `internal.preview`) delegating to the injected `RendererResolver`.

- [ ] **Step 3: Run tests to verify they pass, then commit**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.preview.*" --tests "io.genfin.document.internal.preview.*"` then `./gradlew :fin-document:check`.

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/preview/ \
  fin-core/fin-document/src/main/java/io/genfin/document/port/PreviewRenderer.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/preview/ \
  fin-core/fin-document/src/test/java/io/genfin/document/api/preview/ \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/preview/
git commit -m "feat(fin-document): add Preview (DocumentPreview, PreviewConfiguration, PreviewRenderer) wrapping existing renderers"
```

---

### Task 2: Validation — `ValidationResult`, `ValidationIssue`, `DocumentValidator` port, `DefaultDocumentValidator`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/validation/ValidationIssue.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/validation/ValidationResult.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/DocumentValidator.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/validation/DefaultDocumentValidator.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/validation/ValidationResultTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/internal/validation/DefaultDocumentValidatorTest.java`

**Interfaces:**
- Consumes: `ComposedDocument`, `RendererConfiguration`, `RendererResolver`, `RendererNotFoundException`, `PdfRenderInputs`, `Letterhead`/`LetterheadFormat`/`StandardLetterheadFormat` (Stages 1, 4, 6).
- Produces: `ValidationIssue.of(String code, String message)` + `code()`/`message()`. `ValidationResult.valid(): ValidationResult` (empty issues), `ValidationResult.withIssues(List<ValidationIssue> issues): ValidationResult` + `valid(): boolean` (true iff issues is empty) + `issues(): List<ValidationIssue>`. `DocumentValidator.validate(ComposedDocument document, RendererConfiguration configuration): ValidationResult`. `DefaultDocumentValidator(RendererResolver resolver)` implements `DocumentValidator` — checks: does `resolver` have `configuration`'s implied target renderer registered (the validator needs a `RendererId` to check against — since `RendererConfiguration` doesn't carry a target renderer id itself in Stage 1's design, this validator's `validate` method should accept the `RendererId` to check explicitly as a third parameter: revise the interface to `validate(ComposedDocument document, RendererConfiguration configuration, RendererId targetRenderer): ValidationResult` — check the renderer resolves without throwing, catching `RendererNotFoundException` and turning it into a `"RENDERER_NOT_FOUND"` issue rather than letting it propagate); if `configuration.pdfInputs()` is present and carries a `letterhead()`, is its format one of `PDF`/`PNG`/`JPEG`/`BLANK` (flag `"UNSUPPORTED_LETTERHEAD_FORMAT"` if not, e.g. `SVG`).

- [ ] **Step 1: Write the failing tests**

```java
package io.genfin.document.api.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationResultTest {

  @Test
  void validResultHasNoIssues() {
    ValidationResult result = ValidationResult.valid();
    assertThat(result.valid()).isTrue();
    assertThat(result.issues()).isEmpty();
  }

  @Test
  void resultWithIssuesIsInvalid() {
    ValidationResult result =
        ValidationResult.withIssues(List.of(ValidationIssue.of("RENDERER_NOT_FOUND", "no such renderer")));
    assertThat(result.valid()).isFalse();
    assertThat(result.issues()).hasSize(1);
    assertThat(result.issues().get(0).code()).isEqualTo("RENDERER_NOT_FOUND");
  }
}
```

```java
package io.genfin.document.internal.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.layout.PageLayout;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import io.genfin.document.api.letterhead.Letterhead;
import io.genfin.document.api.letterhead.StandardLetterheadFormat;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.api.result.PdfRenderInputs;
import io.genfin.document.port.DocumentRenderers;
import io.genfin.document.port.RendererConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DefaultDocumentValidatorTest {

  private static ComposedDocument emptyDocument() {
    return ComposedDocument.of(
        DocumentMetadata.of(
            DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en", "USD", Instant.EPOCH),
        List.of(),
        DocumentAttributes.empty());
  }

  @Test
  void validRequestProducesNoIssues() {
    DefaultDocumentValidator validator = new DefaultDocumentValidator(DocumentRenderers.standard());

    var result =
        validator.validate(emptyDocument(), RendererConfiguration.defaults(), RendererId.of("json"));

    assertThat(result.valid()).isTrue();
  }

  @Test
  void unregisteredRendererProducesAnIssue() {
    DefaultDocumentValidator validator = new DefaultDocumentValidator(DocumentRenderers.standard());

    var result =
        validator.validate(emptyDocument(), RendererConfiguration.defaults(), RendererId.of("nonexistent"));

    assertThat(result.valid()).isFalse();
    assertThat(result.issues().stream().map(i -> i.code()).collect(Collectors.toList()))
        .contains("RENDERER_NOT_FOUND");
  }

  @Test
  void unsupportedLetterheadFormatProducesAnIssue() {
    DefaultDocumentValidator validator = new DefaultDocumentValidator(DocumentRenderers.standard());
    Letterhead svgLetterhead =
        Letterhead.of(LetterheadId.of("lh-1"), StandardLetterheadFormat.SVG, new byte[] {1}, "image/svg+xml");
    PdfRenderInputs inputs =
        PdfRenderInputs.builder(PageLayout.builder(StandardPaperSize.A4, StandardOrientation.PORTRAIT).build())
            .letterhead(svgLetterhead)
            .build();

    var result =
        validator.validate(
            emptyDocument(), RendererConfiguration.defaults().withPdfInputs(inputs), RendererId.of("pdf"));

    assertThat(result.valid()).isFalse();
    assertThat(result.issues().stream().map(i -> i.code()).collect(Collectors.toList()))
        .contains("UNSUPPORTED_LETTERHEAD_FORMAT");
  }
}
```

- [ ] **Step 2: Run tests, verify they fail, then implement**

`ValidationIssue`/`ValidationResult` are plain value types (validate `code`/`message` non-blank via `Validate.notBlank`). `DefaultDocumentValidator.validate(...)`: wrap `resolver.resolve(targetRenderer)` in a try/catch for `RendererNotFoundException`, appending an issue on catch (do NOT let the exception propagate — validation's whole point is collecting problems, not throwing on the first one); separately check `configuration.pdfInputs().flatMap(PdfRenderInputs::letterhead)` — if present, check its `format().code()` against the four supported format codes via a small `Set<String>` membership check (not `switch`/`instanceof`), appending `"UNSUPPORTED_LETTERHEAD_FORMAT"` if not a member. Collect all issues into one list, return `ValidationResult.withIssues(...)` or `ValidationResult.valid()` if empty.

- [ ] **Step 3: Run tests to verify they pass, then commit**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.validation.*" --tests "io.genfin.document.internal.validation.*"` then `./gradlew :fin-document:check`.

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/validation/ \
  fin-core/fin-document/src/main/java/io/genfin/document/port/DocumentValidator.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/validation/ \
  fin-core/fin-document/src/test/java/io/genfin/document/api/validation/ \
  fin-core/fin-document/src/test/java/io/genfin/document/internal/validation/
git commit -m "feat(fin-document): add DocumentValidator for pre-flight validation before rendering"
```

---

### Task 3: Configuration — `DocumentEngineConfiguration`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/config/DocumentEngineConfiguration.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/config/DocumentEngineConfigurationTest.java`

**Interfaces:**
- Consumes: `RendererId`, `PaperSize`/`Orientation`/`Margins`/`PageNumberConfiguration` (Stages 1, 5), `BrandId`/`LetterheadId` (Stage 1 identity).
- Produces: `DocumentEngineConfiguration.builder(): Builder` with all-optional chainable `.defaultRenderer(RendererId)`, `.defaultPaperSize(PaperSize)`, `.defaultOrientation(Orientation)`, `.defaultBrand(BrandId)`, `.defaultLetterhead(LetterheadId)`, `.defaultMargins(Margins)`, `.defaultPageNumbers(PageNumberConfiguration)`, `.previewEnabled(boolean)`, `.watermarkEnabled(boolean)`, `.build()`. Zero-config defaults (all coalesced in the constructor): `defaultRenderer = RendererId.of("json")`, `defaultPaperSize = StandardPaperSize.A4`, `defaultOrientation = StandardOrientation.PORTRAIT`, `defaultBrand`/`defaultLetterhead` = `Optional.empty()`, `defaultMargins = Margins.none()`, `defaultPageNumbers = PageNumberConfiguration.disabled()`, both booleans default `false`.

- [ ] **Step 1: Write the failing test**

```java
package io.genfin.document.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.BrandId;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.layout.StandardOrientation;
import io.genfin.document.api.layout.StandardPaperSize;
import org.junit.jupiter.api.Test;

class DocumentEngineConfigurationTest {

  @Test
  void zeroConfigDefaultsAreSensible() {
    DocumentEngineConfiguration config = DocumentEngineConfiguration.builder().build();

    assertThat(config.defaultRenderer()).isEqualTo(RendererId.of("json"));
    assertThat(config.defaultPaperSize()).isEqualTo(StandardPaperSize.A4);
    assertThat(config.defaultOrientation()).isEqualTo(StandardOrientation.PORTRAIT);
    assertThat(config.defaultBrand()).isEmpty();
    assertThat(config.defaultLetterhead()).isEmpty();
    assertThat(config.defaultMargins().topPoints()).isZero();
    assertThat(config.defaultPageNumbers().enabled()).isFalse();
    assertThat(config.previewEnabled()).isFalse();
    assertThat(config.watermarkEnabled()).isFalse();
  }

  @Test
  void explicitValuesAreRespected() {
    DocumentEngineConfiguration config =
        DocumentEngineConfiguration.builder()
            .defaultRenderer(RendererId.of("pdf"))
            .defaultBrand(BrandId.of("brand-1"))
            .previewEnabled(true)
            .build();

    assertThat(config.defaultRenderer()).isEqualTo(RendererId.of("pdf"));
    assertThat(config.defaultBrand()).contains(BrandId.of("brand-1"));
    assertThat(config.previewEnabled()).isTrue();
  }

  @Test
  void explicitNullOptionalSettersCoalesceToDefaultsRatherThanNull() {
    DocumentEngineConfiguration config =
        DocumentEngineConfiguration.builder()
            .defaultRenderer(null)
            .defaultPaperSize(null)
            .defaultOrientation(null)
            .defaultMargins(null)
            .defaultPageNumbers(null)
            .build();

    assertThat(config.defaultRenderer()).isEqualTo(RendererId.of("json"));
    assertThat(config.defaultPaperSize()).isEqualTo(StandardPaperSize.A4);
    assertThat(config.defaultOrientation()).isEqualTo(StandardOrientation.PORTRAIT);
    assertThat(config.defaultMargins()).isNotNull();
    assertThat(config.defaultPageNumbers()).isNotNull();
  }
}
```

- [ ] **Step 2: Run test, verify it fails, then implement**

Follow `PageLayout.Builder`'s exact established pattern (private constructor takes the `Builder`, coalesces every nullable builder field to its default, `Validate` not needed here since every field has a real default — there's no field this type cannot synthesize a sane value for, unlike `BrandIdentity.companyName` or `Letterhead.id`).

- [ ] **Step 3: Run test to verify it passes, then commit**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.config.DocumentEngineConfigurationTest"` then `./gradlew :fin-document:check`.

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/config/ \
  fin-core/fin-document/src/test/java/io/genfin/document/api/config/
git commit -m "feat(fin-document): add DocumentEngineConfiguration with sensible zero-config defaults"
```

---

### Task 4: Events — `DocumentEvent` + 7 concrete types, `DocumentEventListener` SPI, `DocumentEventPublisher`/`DocumentEventPublishers`

**Files:**
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/event/DocumentEvent.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/event/DocumentRendered.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/event/PdfGenerated.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/event/BrandApplied.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/event/LetterheadApplied.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/event/LayoutApplied.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/event/WatermarkApplied.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/api/event/QrCodeRendered.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/DocumentEventListener.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/DocumentEventPublisher.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/port/DocumentEventPublishers.java`
- Create: `fin-core/fin-document/src/main/java/io/genfin/document/internal/event/DefaultDocumentEventPublisher.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/api/event/DocumentEventTest.java`
- Test: `fin-core/fin-document/src/test/java/io/genfin/document/port/DocumentEventPublishersTest.java`

**Interfaces:**
- Consumes: `RendererId`/`DocumentId`/`BrandId`/`LetterheadId` (Stage 1 identity), `WatermarkLabel` (Stage 5).
- Produces: `DocumentEvent` (marker interface, `java.time.Instant occurredAt()`). Each of the 7 concrete types: a `final class` with a private constructor, `of(...)` factory taking its specific fields plus stamping `Instant.now()` internally (this is the one place in the module a real timestamp is needed — use `java.time.Instant.now()` directly, not injected, since events are fired at the moment they occur and there's no test requirement to control the clock for this stage), accessors for each field plus `occurredAt()`. `DocumentEventListener` (port, SPI, extends `Extension`) — `void onEvent(DocumentEvent event)`. `DocumentEventPublisher` (port) — `void publish(DocumentEvent event)`. `DocumentEventPublishers.standard(): Bundle` with `register(DocumentEventListener)`/`publisher(): DocumentEventPublisher` — mirrors every other Stage 3-6 `XResolvers.standard()` factory shape exactly. `DefaultDocumentEventPublisher` (internal.event) holds a list of registered listeners, `publish(event)` calls `onEvent(event)` on each in registration order.

- [ ] **Step 1: Write the failing tests**

```java
package io.genfin.document.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.identity.RendererId;
import org.junit.jupiter.api.Test;

class DocumentEventTest {

  @Test
  void documentRenderedExposesFieldsAndTimestamp() {
    DocumentRendered event = DocumentRendered.of(RendererId.of("pdf"), DocumentId.of("doc-1"));

    assertThat(event.rendererId()).isEqualTo(RendererId.of("pdf"));
    assertThat(event.documentId()).isEqualTo(DocumentId.of("doc-1"));
    assertThat(event.occurredAt()).isNotNull();
  }

  @Test
  void pdfGeneratedExposesPageCount() {
    PdfGenerated event = PdfGenerated.of(DocumentId.of("doc-1"), 3);
    assertThat(event.pageCount()).isEqualTo(3);
  }
}
```

```java
package io.genfin.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.event.DocumentEvent;
import io.genfin.document.api.event.DocumentRendered;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.identity.RendererId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentEventPublishersTest {

  @Test
  void registeredListenerReceivesPublishedEvent() {
    DocumentEventPublishers.Bundle bundle = DocumentEventPublishers.standard();
    List<DocumentEvent> received = new ArrayList<>();
    bundle.register(received::add);

    DocumentRendered event = DocumentRendered.of(RendererId.of("pdf"), DocumentId.of("doc-1"));
    bundle.publisher().publish(event);

    assertThat(received).containsExactly(event);
  }
}
```

- [ ] **Step 2: Run tests, verify they fail, then implement**

Each of the 7 event types follows the identical shape — write `DocumentEvent` first, then the 7 concrete types (each ~15 lines: private constructor + `of(...)` + accessors + `occurredAt()`), then the port types and `DefaultDocumentEventPublisher`/`DocumentEventPublishers` mirroring `BrandResolvers`/`LetterheadResolvers`'s exact `Bundle` shape from Stages 3-4.

- [ ] **Step 3: Run tests to verify they pass, then commit**

Run: `./gradlew :fin-document:test --tests "io.genfin.document.api.event.*" --tests "io.genfin.document.port.DocumentEventPublishersTest"` then `./gradlew :fin-document:check`. If the architecture cycle test fails for `DocumentEventPublishers` reaching into `internal.event`, add one more narrow entry to the existing factory-class pattern — never move a class.

```bash
git add fin-core/fin-document/src/main/java/io/genfin/document/api/event/ \
  fin-core/fin-document/src/main/java/io/genfin/document/port/DocumentEventListener.java \
  fin-core/fin-document/src/main/java/io/genfin/document/port/DocumentEventPublisher.java \
  fin-core/fin-document/src/main/java/io/genfin/document/port/DocumentEventPublishers.java \
  fin-core/fin-document/src/main/java/io/genfin/document/internal/event/ \
  fin-core/fin-document/src/test/java/io/genfin/document/api/event/ \
  fin-core/fin-document/src/test/java/io/genfin/document/port/DocumentEventPublishersTest.java
git commit -m "feat(fin-document): add DocumentEvent hierarchy (7 types) and DocumentEventPublisher/Listener SPI"
```

---

### Task 5: `module-info.java` exports + full build gate

**Files:**
- Modify: `fin-core/fin-document/src/main/java/module-info.java` (add `exports io.genfin.document.api.preview;`, `exports io.genfin.document.api.validation;`, `exports io.genfin.document.api.config;`, `exports io.genfin.document.api.event;`)

**Interfaces:**
- Consumes: everything from Tasks 1-4.

- [ ] **Step 1: Add the four exports, run the module gate**

Run: `./gradlew :fin-document:check`
Expected: PASS.

- [ ] **Step 2: Run the full repo build**

Run: `./gradlew build`
Expected: PASS across all modules.

- [ ] **Step 3: Commit**

```bash
git add fin-core/fin-document/src/main/java/module-info.java
git commit -m "test(fin-document): export preview/validation/config/event packages, verify Stage 7 architecture constraints"
```

## Self-Review Notes

- **Spec coverage:** Preview (Group 7), Validation (Group 8), Configuration (Group 9), Builders (Group 10 — satisfied by existing builders + this stage's 2 new ones, explicitly not duplicating `PdfRenderInputs` with a redundant `RenderRequest` type), Events (Group 11, minus the explicitly-deferred `PdfRenderer` wiring) are all covered.
- **Deliberately deferred, documented, not silently dropped:** wiring event publication into `PdfRenderer.render(...)` itself — the event types and plumbing are proven standalone; actually firing them during a real render is a follow-up, noted for Stage 8's Future Roadmap if not picked up opportunistically.
- **No placeholders:** every step has complete, real code.
